---

## 6. TDD strategy and test infrastructure

### 6.1 Test stacks

| Layer | Stack | Runs | Location |
|---|---|---|---|
| Java units | JUnit 5.13.3 (existing) | `mvn -pl report test` (every build) | `report/src/test/java/...` |
| JS units (pure logic + `vm`-sandboxed DOM-less code) | Node built-in `node:test` + `node:assert/strict` + `vm` (pattern proven by `viewer.test.cjs`) | `node --test report/src/test/javascript/` locally; Maven `test` phase via exec-maven-plugin behind the `js-tests` profile; CI job with `setup-node` | `report/src/test/javascript/*.test.cjs` |
| Browser E2E (incl. `file://` protocol checks, a11y scan) | Playwright (chromium first; firefox/webkit stretch) + axe-core, against **generated fixture reports** | `report/e2e/` own package.json; `npx playwright test`; dedicated CI job | `report/e2e/` |
| Parity/visual | computed-style assertions in E2E + golden fixtures | same as E2E | `report/e2e/` + `report/src/test/resources/parity/` |

Node unit tests never require npm packages (mirroring `viewer.test.cjs`: libs are stubbed
in a `vm` context). Playwright is the only place with npm dev dependencies, isolated in
`report/e2e/` so the Java build is untouched.

### 6.2 Wiring (Phase P0 delivers this first — you cannot TDD without it)

1. **Mustache to compile scope**: bump `com.github.spullara.mustache.java:compiler`
   0.9.10 → **0.9.14**, remove `<scope>test</scope>` in `report/pom.xml`.
2. **`js-tests` Maven profile** in `report/pom.xml`: binds
   `exec-maven-plugin` (bash `node --test report/src/test/javascript`) to the `test`
   phase; inactive by default so contributors without Node can still `mvn verify`;
   `maven.yml`/`maven-pr.yml` gain a step (`setup-node@v4`, Node 22) +
   `mvn -P js-tests verify`. A separate lightweight workflow `js-tests.yml` mirrors it.
3. **`report/e2e/`**: `package.json` (`@playwright/test`, `axe-core`),
   `playwright.config.js`, a fixture-generator invocation (see 6.4), npm-free Java side.
4. **Fixture reports generator**: `SampleReportGenerationTest` (JUnit, tagged) renders
   the fixture-project reports (small in-memory codebase built like
   `DisharmonyRenderingTest` fixtures, plus the page repo's
   `tests/fixtures/junit4-report.json` copied into `report/src/test/resources/parity/`)
   into `target/e2e/refactor-first-report.html` / `-simple-report.html`, with
   `minifyHtml=false` and a second leg `true`. Playwright specs open these via
   **`file://` URLs directly** (this is the CORS contract under test).

### 6.3 Golden & parity test patterns

- **Dual-engine mustache goldens** (T2.3): the Java test serializes the view model to
  JSON (Jackson) and asserts `mustache.java` output against a golden; the Node test
  renders the **same JSON** with mustache.js 4.2.0 (vendored single-file copy in
  `report/src/test/javascript/vendor/`, no npm) and asserts **byte-identical output**.
  This catches engine divergence (the #1 risk of D1/D2).
- **DOT cross-language goldens** (T1.4/T7.1): fixture hierarchies → `NestedDotBuilder`
  goldens already exist (Java). New Node test runs `rf-hierarchy-dot.js` on the same
  fixture JSON and asserts byte-identical DOT in **light mode**, and
  light-DOT+`fontcolor="#9fb0c0"` edge labels in **dark mode** (matching page repo's
  `graphDotOptions`).
- **Palette fixture**: `report/src/test/resources/parity/palette.json` lists every
  canonical token (Appendix B). JUnit asserts `rf-palette.css` declares exactly these
  values; Node tests assert the JS constants (`RF.theme.ACCENTS`, `RF.charts.LEGEND`,
  `SIGMA_HOVER_BG`) equal the same file. One file, three consumers, zero drift.
- **Timestamp normalization**: `ReportModelBuilder` takes a `Clock`; goldens use a fixed
  clock; the footer "Last Published"/scan timestamp assertions use regexes.

### 6.4 Red-green-refactor discipline

Every task below is listed as **Red** (write the failing test first, watch it fail for
the *right* reason), **Green** (minimal implementation), **Refactor** (dedupe, naming,
delete the old path). No task moves to the next without its exit criteria. When a task
touches an existing pinned test (`HtmlReportTest`, `ViewerScriptTest`,
`DisharmonyRenderingTest`, …), the task explicitly lists the churn: those assertions
describe the *old* architecture and are replaced, not deleted silently (mapping table
in §10.3).

---

## 7. Phases

### Phase P0 — Test infrastructure & scaffolding (no production behavior change)

**Goal:** all test rails exist and are green before any production code moves.

- **T0.1 (Red→Green)** `MustacheEngineVersionTest`: trivial render test proving
  compiler 0.9.14 resolves at compile scope. *Green:* pom change from §6.2.
- **T0.2 (Red→Green)** `JsTestWiringTest`: a canary
  `report/src/test/javascript/00-canary.test.cjs` asserting `RF` bootstrap works;
  `mvn -P js-tests test` executes it (assert via surefire/exec output check in CI).
- **T0.3** `report/e2e/` scaffold + `SampleReportGenerationTest` producing
  `target/e2e/*.html`; canary `file-protocol.spec.js` asserting: page loads from
  `file://`, **zero console errors, zero failed requests, zero pageerrors** — this is
  the CORS gate every later phase must keep green.
- **T0.4** Copy page-repo `tests/fixtures/junit4-report.json` →
  `report/src/test/resources/parity/`; write `ParityFixtureTest` asserting the Java
  DTO pipeline can round-trip it (Jackson) — anchors later phases to real data.
- **T0.5** `palette.json` fixture (Appendix B) + placeholder consumers that assert the
  *current* colors, so P5's changes will fail first — establishing the red baseline.
- **T0.6** CI: add `setup-node` + `mvn -P js-tests verify` step to `maven-pr.yml`;
  add `js-tests.yml` (Node 22) and an `e2e.yml` (java + node + playwright chromium).

**Exit criteria:** `mvn verify` green; `mvn -P js-tests verify` green; `npx playwright test`
green on the canary spec; CI legs exist for all three.

---

### Phase P1 — Extract `ReportModelBuilder` (behavior-preserving refactor)

**Goal:** one code path builds `RefactorFirstReportDTO` for all three consumers;
`JsonGenerator` stops extending `HtmlReport`. Zero output changes (verified by existing
green tests + new golden).

- **T1.1 (Red)** `ReportModelBuilderTest`: for a fixed in-memory fixture project
  (reuse fixtures from `SimpleHtmlReportTest`/`JsonGeneratorTest`) and fixed `Clock`,
  assert the built DTO equals the DTO `JsonGenerator` builds today (serialize both,
  compare JSON). Initially fails (class doesn't exist).
- **T1.2 (Green)** Create `ReportModelBuilder` by *moving* (not rewriting) the DTO
  assembly from `JsonGenerator` (hierarchy building via `HierarchyBuilder`,
  relationships-to-remove, disharmony sections + `createBubble`, cycles, metadata).
  Re-point `JsonGenerator` to it. **`JsonGeneratorTest` (14) must stay green untouched.**
- **T1.3 (Green)** Re-point `HtmlReport`/`SimpleHtmlReport` to `ReportModelBuilder` for
  the analysis values they currently recompute, while keeping their string-concat
  rendering for now (rendering migration is P2–P4). Existing `HtmlReportTest` (24),
  `SimpleHtmlReportTest` (13), `DisharmonyRenderingTest` (14) stay green.
- **T1.4 (Red→Green)** Export fixture hierarchies →
  `report/src/test/resources/golden/dot/*.dot` from `NestedDotBuilderTest`'s existing
  goldens (no new truth; just file-externalized) so Node tests can consume them in P7.
- **T1.5 (Refactor)** Delete the `extends HtmlReport` linkage; make
  `createBubble`'s interpolation a static utility on the builder (single source for
  P4's chart config); deprecate & no-op any now-unused protected hooks.

**Exit criteria:** full `mvn verify` green; `../../.refactorfirst/refactor-first.json` content
byte-identical for the fixture project (before/after diff in `ReportModelBuilderTest`).

---

### Phase P2 — Mustache rendering infrastructure + view model

**Goal:** template engine + view model factory proven by dual-engine goldens before any
report flips over.

- **T2.1 (Red→Green)** `TemplateEngineTest`: load classpath template, render map data,
  assert output; assert compiled-template caching; assert HTML-escaping of every
  interpolated string by default.
- **T2.2 (Red)** `ViewModelFactoryTest`: build view model from the P1 fixture DTO and
  assert the contract the page-repo template expects (mirroring `prepareReportData`):
  project metadata, `hasAnyDisharmony` flags, per-disharmony `anchorId`, problem/solution,
  table `{caption, headers, rows(cells as {text, href?})}`, relationship tables, cycle
  summary/breakdown, graph button metadata (ids, `dotThresholdExceeded`, repoUrl).
  *Green:* implement `ViewModelFactory`. All strings pass through the existing
  `escapeHtml` utilities at DTO level (XSS plan already hardened these — reuse, don't fork).
- **T2.3 (Red→Green)** `DualEngineGoldenTest` (Java) +
  `mustache.dual-engine.test.cjs` (Node, vendored mustache.js): render fixture view
  model with both engines against the **P2 test template**; assert byte-identical.
  Documents the allowed mustache subset (no lambdas, no client-side partials) in
  `report/src/main/resources/templates/README.md`.
- **T2.4 (Red→Green)** `DataEmbedderTest`: serialize fixture DTO with Jackson →
  inject into HTML → assert (a) `JSON.parse` round-trip in Node test, (b) `</`
  sequences escaped (`<\/`), (c) U+2028/U+2029 escaped, (d) a class name containing
  `</script>`, backticks, `${}` (Kotlin-style fixture exists in
  `HtmlReportKotlinTest`) survives safely.

**Exit criteria:** engine + factory + embedder green in both stacks; no report output
changed yet.

---

### Phase P3 — `SimpleHtmlReport` migration (strict HTML, WCAG semantics)

**Goal:** the simple report renders from `refactor-first-simple-report.mustache`;
output is strictly HTML and visually equivalent to today (plus WCAG semantics and a
CSS-only sticky header).

- **T3.1 (Red)** `SimpleHtmlReportTemplateTest`: for the P1 fixture, render via the new
  template and assert section-by-section parity with the *current* output (nav menu,
  relationship tables, 11 disharmony sections, cycle summary, largest-cycle breakdown,
  footer timestamp) — port the strongest assertions from `SimpleHtmlReportTest` (13) and
  `DisharmonyRenderingTest` (14) to target the template output. Fails: template missing.
- **T3.2 (Red)** `StrictHtmlComplianceTest` (the M3 gate): asserts the rendered simple
  report contains **no** `<script`, no `javascript:` URI, no `on*=…` attribute, no
  `<iframe`; parses via minify-html (existing dep) without error; UTF-8 charset
  declared; **runs on every fixture including the Kotlin one**.
- **T3.3 (Red)** `SimpleReportWcagTest`: every table has `<caption>` + `scope="col"`
  headers; one `<h1>`; heading levels don't skip; nav has `aria-label="Report sections"`;
  class-name links have discernible text (no bare hrefs); a `<style>` block provides
  sticky `thead th` (CSS-only) and `:focus-visible` outlines.
- **T3.4 (Green)** Write `refactor-first-simple-report.mustache` (adapted from the
  current string output + page-repo semantics); switch `SimpleHtmlReport.execute()` to
  `TemplateEngine` + `ViewModelFactory`; keep MinifyHtml flow and `getOutputName()`.
- **T3.5 (Refactor)** Delete superseded string-building methods from
  `SimpleHtmlReport` (keep `renderDisharmonyChart` hook returning `""` for now —
  P4 removes the hook entirely when `HtmlReport` stops needing it).
- **T3.6** E2E `simple-report.spec.js`: open the generated simple file via `file://`;
  zero console errors; sticky header visible on scroll (CSS assertion); axe-core scan
  with zero violations (light CSS-only page — should pass cleanly).

**Exit criteria:** simple report = template-rendered, strictly valid, WCAG-clean;
`GITHUB_STEP_SUMMARY` README snippet unchanged and still accurate; old string-building
tests replaced per §10.3 mapping.
