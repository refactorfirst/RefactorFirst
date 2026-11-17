
---

### Phase P8 — WCAG 2.2 AA audit & hardening

**Goal:** conformance is *continuously tested*, not asserted. Consolidate and close gaps
across both reports.

- **T8.1 (Red)** `WcagStructureTest` (JUnit, both templates): single `<h1>`; no skipped
  heading levels; every table `<caption>` + `scope="col"`; `lang` on `<html>`; unique
  landmark roles (`main`, `nav`, optional `banner`/`contentinfo`); canvas charts carry
  `role="img"` + `aria-label` (chart title) **and** a visually-hidden data-table
  alternative or `aria-describedby` summary (1.1.1); graph buttons have descriptive
  accessible names (e.g. "Open 2D class map for graph X"); popup close button
  `aria-label="Close graph popup"`; popups move focus in on open, restore on close,
  trap Tab (2.4.3), close on Escape.
- **T8.2 (Red)** `ContrastTest` (Node, port of page repo `css-a11y.test.js` logic):
  parse `rf-palette.css` + template `<style>`; compute WCAG contrast ratios for every
  text/background token pair in **both** palettes (≥ 4.5:1 normal text, ≥ 3:1 large
  text & UI boundaries) and focus indicators (≥ 3:1). `palette.json` (Appendix B)
  already passes on the page repo — this test locks the port.
- **T8.3 (Red)** `KeyboardParityTest` (Node + E2E): graph hover has keyboard parity
  (node anchors are tab stops; `focusin`/`focusout` reuse the hover model — T7.2);
  copy cells Enter/Space; sort buttons Enter/Space; radio group arrow-key navigation;
  search Escape clears; no keyboard trap outside intentional popup trap.
- **T8.4** E2E `wcag-axe.spec.js`: axe-core scan (WCAG 2.2 AA ruleset, both reports,
  light + dark, before and after interacting: filter applied, popup open, theme
  dark) — zero violations. Target-size rule: interactive controls ≥ 24 px (2.5.8)
  asserted via computed styles.
- **T8.5** Document conformance claim (which SCs are fully met vs. with-exceptions) in
  README; keep the two "known limitations" honest: WebGL canvas content (3D graph) has
  a text alternative via the tables + 2D map, and third-party CDN widgets are out of
  the report's control.

**Exit criteria:** zero axe violations; contrast/structure/keyboard suites green for
both reports and both themes.

---

### Phase P9 — Parity verification (requirement 9)

**Goal:** demonstrate "no discernable difference" mechanically, not by eyeball.

- **T9.1** `ParityContrastSpec` (E2E): open the generated `refactor-first-report.html`
  via `file://` **and** the page repo's rendering of the same fixture
  (`tests/fixtures/junit4-report.json`, page repo running via its own serve script);
  assert identical computed styles for: table header background/text
  (`#2a6f97` / `#fff`), body text/background per theme, link colors, hover accents on
  a vizdom edge/node, chart legend text color, bubble fill of a known priority bubble.
  Run in both themes (emulated `prefers-color-scheme`).
- **T9.2** `ParityDomSpec`: same DOM contract on both — caption text, aria-sort
  transitions, match-count phrasing ("N of M rows match"), toast phrasing ("Copied …"),
  CSV filename pattern, column headers of the 11 disharmony tables.
- **T9.3** `ParityChartsSpec`: for the fixture, the bubble set (x, y, r, fill) emitted
  by the Java report equals the page repo's rendering of the same JSON (read Chart.js
  instance data in both pages).
- **T9.4** Regenerate the repo-root sample reports (`../../junit4-report.html`,
  `fxgl-report.html`) with the new pipeline for release; keep the old versions in git
  history; update README links/screenshots.
- **T9.5** Manual sign-off checklist (recorded in the PR): side-by-side screenshots at
  1280 px and 375 px, light/dark/system, one large report (fxgl-scale) for scroll and
  threshold behavior.

**Exit criteria:** all parity specs green in chromium (firefox/webkit stretch); samples
regenerated; sign-off recorded.

---

### Phase P10 — Documentation, cleanup, release

- **T10.1** Update `../../AGENTS.md` (RefactorFirst): new report architecture section
  (template + assets pipeline), revised DOT-synthesis constraint (two implementations:
  Java golden + `rf-hierarchy-dot.js`, sync contract per Appendix C), feature map, test
  map. Update `CLAUDE.md` module map line for `report`.
- **T10.2** Update `../../README.md`: report feature list, `minifyHtml` notes, sample links,
  and the `simpleHtmlReport` + `$GITHUB_STEP_SUMMARY` snippet (unchanged command,
  verified).
- **T10.3** Chore (from §10.4): bump Gradle plugin's pinned `report` dependency from
  0.7.2-SNAPSHOT; smoke-test `refactorFirstHtmlReport` / `refactorFirstSimpleHtmlReport`
  tasks; CLI `-t HTML|SIMPLE_HTML` smoke test (signatures unchanged, but verify end-to-end).
- **T10.4** Release notes: visual-parity change notice (report now styled like the
  website; legacy `border="5px"` tables replaced by page-repo styling in the
  interactive report), pinned-CDN change notice, new goals unchanged.
- **T10.5** Post-merge follow-ups filed as issues (§12.3): CSP hardening, SRI,
  viewer unification, page-repo template sync-back.

---

## 8. Requirement traceability matrix

| Requirement | Delivered by | Key tests (failing first) |
|---|---|---|
| 1. Sticky column headers | T6.2, T6.6 (`rf-tables.css/.js`), T3.3/T3.6 (CSS-only in simple report) | `sticky.test.cjs`, `TableControlsWcagTest`, `report-features.spec.js` ("sticky headers stay pinned", "wide tables keep header pinned"), `simple-report.spec.js` |
| 2. Light/dark + system default | T5.1–T5.6 | `palette-parity.test.js`, `theme.test.cjs`, `theme-redraw.test.cjs`, `ThemeWcagTest`, `dark-mode.spec.js` |
| 3. WCAG 2.2 AA | T3.3, T6.5, T7.2 (keyboard parity), T8.1–T8.5 — enforced continuously | `WcagStructureTest`, `ContrastTest`, `KeyboardParityTest`, `wcag-axe.spec.js`, `SimpleReportWcagTest` |
| 4. Table filtering (+ sort/pagination parity) | T6.1, T6.5, T6.6 | `tables.test.cjs`, `report-features.spec.js` (filter/Escape/×/match count) |
| 5. CSV export | T6.3, T6.6 | `csv.test.cjs` (RFC 4180 + formula-injection), `report-features.spec.js` (download verification) |
| 6. Cell click-to-copy | T6.4, T6.6 | `copy.test.cjs`, `report-features.spec.js` (clipboard + toast) |
| 7. Colored mouseover (vizdom / Sigma / 3D) | T7.2–T7.6 | `graphs-vizdom.test.cjs`, `graphs-sigma.test.cjs`, `graphs-3d.test.cjs`, `graphs.spec.js` |
| 8. Chart.js instead of Google Charts | T4.1–T4.3, T4.7 | `ChartJsConfigTest`, `charts.test.cjs`, permanent grep gate in `InteractiveTemplateTest` |
| 9. Color parity with page repo | D9, T5.1, P9 | `palette.json` consumers (JUnit + Node), `ParityContrastSpec`, `ParityChartsSpec` |
| M1 Mustache migration (both reports) | P2, T3.4, T4.6 | `TemplateEngineTest`, `ViewModelFactoryTest`, `DualEngineGoldenTest` |
| M2 Single file, opens on `file://`, features in own JS files | D3/D8, T4.4–T4.5, `file-protocol.spec.js` (permanent zero-error gate) | `AssetInlinerTest`, `DataEmbedderTest`, `PinnedCdnTest` |
| M3 SimpleHtmlReport strictly HTML | T3.2 (permanent gate) | `StrictHtmlComplianceTest` |

---

## 9. Risks & mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| mustache.java vs mustache.js rendering divergence | Medium | Wrong content in one consumer | Dual-engine byte-identical goldens (T2.3); restricted mustache subset documented; 0.9.14 pinned |
| CDN unreachable / corporate proxy (esm.sh, jsDelivr) breaks graphs | Medium | Degraded report | Feature detection + visible aria-live fallback notices (T7.5); core tables/charts degrade gracefully; pinned URLs are cache-friendly |
| MinifyHtml corrupts inline JS/JSON (`minifyJs=true`) | Low | Broken report only with flag on | T4.4 round-trip tests + E2E minified leg in CI |
| Headless WebGL flakiness in 3D tests | Medium | Flaky CI | Stubbed unit tests carry the logic assertions; E2E 3D limited to smoke "no errors" |
| `:has()` unsupported (very old browsers) | Low | Dark theme unreachable | Documented limitation; system/light still render; matches page repo's supported browsers |
| Large reports (fxgl-scale) grow (inline DTO) | Medium | Slower open | DTO is a fraction of DOT inline size in most cases; threshold behavior preserved; optional perf budget test (page repo has one) |
| Clipboard API variance on `file://` | Medium | Copy fails silently | `execCommand` fallback ported + tested; toast on success only, never throws |
| Playwright + `file://` quirks (esp. webkit) | Medium | E2E friction | Chromium primary; webkit/firefox as separate stretch leg; unit tests carry most logic |
| Existing pinned tests churning | Certain (by design) | Reviewer noise | §10.3 mapping table; each replacement justified in the PR |
| Scope creep into viewer/jsonReport | Medium | Timeline slip | Explicit out-of-scope list (§2.2); shared-builder refactor is the only touchpoint |

---

## 10. Legacy test churn mapping & chores

### 10.1 suites that must stay green untouched
`JsonGeneratorTest` (14), `ReportWriterTest` (17), `HierarchyBuilderTest` (18),
`NestedDotBuilderTest` (10 goldens), `ReportDataSerializationTest`, `CsvReportTest`,
Kotlin fixtures.

### 10.2 suites replaced by design (old architecture assertions)
- `HtmlReportTest` (24): popup-script/DOT-const/printHead assertions → replaced by
  `InteractiveTemplateTest`, `PinnedCdnTest`, `charts.test.cjs`, `graphs-*.test.cjs`.
- `DisharmonyRenderingTest` (14): Google-Charts-string comparisons → replaced by
  `ChartJsConfigTest` + template parity tests (T3.1/T4.1).
- `ViewerScriptTest` (7): still valid (viewer untouched) but repointed if the viewer
  later adopts `rf-hierarchy-dot.js` (§12.3).
- `SimpleHtmlReportTest` (13): strongest assertions ported into
  `SimpleHtmlReportTemplateTest`; the rest become model-builder tests (T1.2).
- `MustacheTemplateRenderingTest` (6): superseded by `DualEngineGoldenTest`.

### 10.3 rule
No pinned assertion is deleted without a mapping entry in the migration PR description
(old assertion → new test). This keeps the churn reviewable.

### 10.4 chores discovered during planning (fold into P10.3)
- Gradle plugin pins `report:0.7.2-SNAPSHOT` (stale vs 0.11.0) — bump + smoke test.
- `RefactorFirstMavenReport` (Doxia) duplicates CDN head imports and contains a dead,
  malformed `renderStyle` call — removed/rewired in T4.6/T4.7.
- `3d-force-graph` and `mvp.css` are unpinned today — pinned in T4.5.
- CI has no Node — added in T0.6.

---

## 11. Critical review record (gaps found and how the plan was updated)

The plan went through a self-review pass against the verified findings; each gap below
**changed** the plan above (v1 → v2):

| # | Gap identified in v1 / in current state | Update applied |
|---|---|---|
| G1 | Requirements list omitted **sorting & pagination**, but they are part of the page repo's table UI; requirement 9 ("no discernable difference") implies them, and CSV "honors filter + sort, ignores pagination" is meaningless without sort | Sorting + pagination added to P6 scope (explicitly marked as parity-driven, descope-able) |
| G2 | `JsonGenerator extends HtmlReport` — migrating `HtmlReport` first would have broken `jsonReport` output | P1 (model extraction, D10) inserted **before** any template work; `JsonGeneratorTest` locked as untouched-green |
| G3 | Doxia site report (`mvn site`) path forgotten — it re-uses `HtmlReport.generateReport` and duplicates CDN imports, plus a dead `renderStyle` bug | T4.6/T4.7 + compatibility guarantee 4 (§2.3) |
| G4 | mustache.java 0.9.10 (test-scope, stale) vs mustache.js 4.2.0 divergence risk | Bump to 0.9.14 + dual-engine byte-identical goldens (T2.3) |
| G5 | Template-literal escaping of DOT/JS (backticks, `${`, `</script>`, Kotlin identifiers) is a latent XSS/corruption class | D3: all data moves to `<script type="application/json">`; adversarial escaping tests (T2.4) incl. `</script>`-bearing class names |
| G6 | Unpinned `3d-force-graph` and `mvp.css` = silent behavior drift; unpinned was also a CORS/compat hazard | Pinning + `PinnedCdnTest` (T4.5, permanent gate) |
| G7 | `minifyHtml` with `setMinifyJs(true)` could corrupt the new inline JS/JSON | Minified-leg E2E + round-trip tests (T4.4) |
| G8 | GitHub `$GITHUB_STEP_SUMMARY` sanitizer strips `<style>` (and scripts) — styling decisions for the simple report needed an explicit stance | Simple report keeps presentational markup + adds CSS-only sticky block that degrades gracefully; strict validator (T3.2); documented in T3.4 |
| G9 | `navigator.clipboard` availability on `file://` treated as guaranteed — it isn't universal | `execCommand` fallback ported and unit-tested (T6.4) |
| G10 | Existing 100+ pinned assertions describe the old architecture — silent rewrites would hide regressions | §10 churn mapping rule (10.1–10.3) |
| G11 | Node not in CI → JS tests would silently never run | `js-tests` Maven profile + `setup-node` CI legs + canary test (T0.2/T0.6) |
| G12 | "WCAG" as a single checkbox — contrast/focus/target-size need automation, not intent | Ported page-repo computed-style tests: `ContrastTest`, `ThemeWcagTest`, `TableControlsWcagTest`, `KeyboardParityTest`, axe E2E (P8) |
| G13 | Dark-mode graph recoloring impossible if DOT is prebuilt Java-side (label glyphs are fill-only paths beyond CSS) | D4: client-side DOT synthesis from hierarchy payloads — the same mechanism the page repo uses, giving dark redraw *and* color parity |
| G14 | Goldens would flake on embedded timestamps | `Clock` injection in `ReportModelBuilder` (T1.1) + regex normalization |
| G15 | Empty/degenerate paths (no disharmonies, `analyzeCycles=false`, `dotThresholdExceeded`, Kotlin fixtures) untested in templates | Empty-section fixtures required in T3.1/T4.1; threshold parity in T7.4 |
| G16 | "Features in their own JS files" vs single-file delivery tension | D1/D8: classpath `rf-*.js` IIFE modules inlined by `AssetInliner` — maintainable sources, single-file output, Node-testable |
| G17 | Focus/caret loss after table re-renders (page repo solved this; a naive port loses it) | `pendingFocusRef` pattern ported (T6.5/T6.6) |
| G18 | Cross-repo drift: RefactorFirst's template and the page repo's template have already diverged (page version has sticky/WCAG work; Java version doesn't) | Page repo template is the port base; sync contract (Appendix C) + `palette.json` fixture prevent recurrence |
| G19 | CORS story stated loosely ("avoid fetch") — not actionable | §3.3 hazard table + §4.3 contract + permanent `file-protocol.spec.js` zero-error gate (T0.3) |
| G20 | WebGL/3D accessibility and WebKit E2E feasibility over-optimistic | 3D logic unit-tested via stubs; E2E 3D = smoke; 2D + tables provide the accessible alternative (T8.5) |

---

## 12. Delivery checklist

### 12.1 Ordered execution & dependencies
P0 → P1 → P2 → P3 (simple report is independent and can proceed in parallel with
P4–P7 after P2) → P4 → P5 → P6 → P7 → P8 → P9 → P10. Every phase ends with all suites
green: `mvn verify`, `mvn -P js-tests verify`, `npx playwright test`.

### 12.2 Global "done" gates (permanent regression tests)
1. `file-protocol.spec.js`: zero console errors / failed requests from `file://` (CORS gate).
2. `InteractiveTemplateTest` grep gates: no `gstatic`/`google.charts`; no unpinned CDNs.
3. `StrictHtmlComplianceTest`: simple report contains zero JavaScript artifacts.
4. Palette consumers all read `palette.json` (single color truth).
5. `DualEngineGoldenTest` + DOT cross-language goldens stay byte-identical.

### 12.3 Optional follow-ups (filed, not scheduled)
- CSP meta with per-script SHA-256 hashes computed at generation time (Java-side port
  of page repo's `fix-csp-hashes.mjs`); optional SRI on CDN tags.
- Re-enable `JsonGenerator.copyViewerResources()` and share `rf-*.js` with
  `viewer/index.html`, retiring its inline copies.
- Sync the page repo's `assets/refactor-first-report.mustache` back to consume this
  template as canonical (flip sync direction, Appendix C).
- Perf budget test for very large reports (page repo `performance.spec.js` pattern).

---

## Appendix A — CDN pins (exact URLs in generated output)

| Library | URL | Load |
|---|---|---|
| Chart.js | `https://cdn.jsdelivr.net/npm/chart.js@4.4.7/dist/chart.umd.min.js` | classic |
| Sigma.js | `https://cdnjs.cloudflare.com/ajax/libs/sigma.js/2.4.0/sigma.min.js` | classic |
| Graphology | `https://cdnjs.cloudflare.com/ajax/libs/graphology/0.25.4/graphology.umd.min.js` | classic |
| graphlib-dot | `https://cdn.jsdelivr.net/npm/graphlib-dot@0.6.4/dist/graphlib-dot.min.js` | classic |
| svg-pan-zoom | `https://cdn.jsdelivr.net/npm/svg-pan-zoom@3.6.1/dist/svg-pan-zoom.min.js` | classic |
| vizdom | `https://cdn.jsdelivr.net/npm/@vizdom/vizdom-ts-web@0.1.19/vizdom_ts.min.js` | module |
| 3D force graph | `https://esm.sh/3d-force-graph@1.80.1?deps=three@0.186.1` | module (popup) |
| SpriteText | `https://esm.sh/three-spritetext@1.10.0` | module import (popup) |
| mvp.css | `https://unpkg.com/mvp.css@1.15` | stylesheet |
| GitHub buttons | `https://buttons.github.io/buttons.js` | classic, async defer |

All are HTTPS with `Access-Control-Allow-Origin: *` (verified for jsDelivr/cdnjs/esm.sh),
so they load from `file://` origins; vizdom's WASM is fetched relative to its own CDN URL.

## Appendix B — Canonical palette (`palette.json` source values, from page repo `app/globals.css`)

Light: `--brand-color #2a6f97`, `--brand-accent #1f5273`, `--link-color #2a6f97`,
`--on-accent-color #ffffff`, `--text-color #1f2d3d`, `--muted-color #5c6b7a`,
`--bg-color #ffffff`, `--surface-color #f5f8fa`, `--card-bg #ffffff`,
`--border-color #dde4ea`, `--input-border #c6d2dc`, `--graph-line #000000`,
`--graph-breakup-fill #ffc0cb`, `--graph-breakup-line #000000`,
`--graph-hover-edge #b45309`, `--graph-hover-in #1e8449`, `--graph-hover-out #2a6f97`,
`--graph-hover-on #ffffff`, `--error-color #c0392b`, `--success-color #1e8449`,
`--popup-border #cccccc`, `--popup-close-bg #c0392b`, `--toast-bg #1f2933`,
`--toast-fg #ffffff`; chart grid `#e0e0e0`; chart legend text `#5c6b7a`; sticky header
`background #2a6f97`, inset shadow `#1f5273`; legend swatches `rgb(235,64,52)` /
`rgb(137,119,74)` / `rgb(39,174,96)`; bubble interpolation `rgba(235,64,52,0.75)` →
`rgba(39,174,96,0.75)` (midpoint `rgb(137,119,74)`).

Dark: `--brand-color #2a6f97`, `--brand-accent #3178a6`, `--link-color #6fb1d9`,
`--text-color #e6edf3`, `--bg-color #10161d`, `--surface-color #1a2330`,
`--card-bg #1a2330`, `--muted-color #9fb0c0`, `--border-color #2c3a4d`,
`--input-border #5b7396`, `--graph-line #9fb0c0`, `--graph-breakup-fill #7f3350`,
`--graph-breakup-line #f2b8cd`, `--graph-hover-edge #f0b95a`,
`--graph-hover-in #7ee2a8`, `--graph-hover-out #8ed0fa`, `--graph-hover-on #000000`,
`--error-color #f0928c`, `--success-color #5bbd8b`, `--popup-border #3a4b61`,
`--popup-close-bg #cf4436`, scrollbar `#566d8f`; chart legend text `#9fb0c0`;
dark DOT edge `fontcolor #9fb0c0`; sigma hover bg `#1a2330` (light `#ffffff`);
3D popups always use the dark accent set.

Bubble color rule (Java source of truth, `ReportModelBuilder.createBubble`):
`red = round(235*(1-t) + 39*t)`, `green = round(64*(1-t) + 174*t)`,
`blue = round(52*(1-t) + 96*t)`, `t = (priority-1)/(maxPriority-1)`; fill alpha 0.75.

## Appendix C — Cross-repo sync contract (RefactorFirst ↔ refactorfirst-page)

1. `report/src/main/resources/js/rf-hierarchy-dot.js` is the canonical browser DOT
   synthesizer; the page repo's `lib/hierarchy-dot.js` is a synced copy (their
   `tests/unit/hierarchy-dot.test.js` and our `hierarchy-dot.test.cjs` both pin against
   the Java `NestedDotBuilderTest` goldens — a change requires: Java golden update →
   both JS copies → both AGENTS.md notes).
2. `palette.json` (this repo, `report/src/test/resources/parity/`) mirrors page repo
   `app/globals.css` tokens; a palette change on either side must flip both + rerun
   parity specs.
3. Template lineage: `refactor-first-report.mustache` (this repo, server-rendered) is
   derived from the page repo's `assets/refactor-first-report.mustache` (SPA-rendered).
   Deltas: full server-rendered rows, `rf-data` JSON block, control placeholders, no
   `tableUi` client state. Follow-up (§12.3) flips the direction so this template
   becomes canonical for both.
4. Requirement-9 regressions are caught by P9 parity specs, which run both artifacts.

## Appendix D — File inventory

**New (Java):** `ReportModelBuilder`, `ViewModelFactory`, `TemplateEngine`,
`DataEmbedder`, `AssetInliner` (+ tests `ReportModelBuilderTest`,
`ViewModelFactoryTest`, `TemplateEngineTest`, `DualEngineGoldenTest`,
`DataEmbedderTest`, `AssetInlinerTest`, `StrictHtmlComplianceTest`,
`SimpleHtmlReportTemplateTest`, `SimpleReportWcagTest`,
`InteractiveTemplateTest`, `ChartJsConfigTest`, `PinnedCdnTest`,
`PaletteParityTest`, `ThemeWcagTest`, `TableControlsWcagTest`,
`WcagStructureTest`, `SampleReportGenerationTest`).

**New (resources):** `templates/refactor-first-report.mustache`,
`templates/refactor-first-simple-report.mustache`, `templates/README.md`;
`js/rf-{core,theme,hierarchy-dot,tables,csv,copy,charts,graphs-vizdom,graphs-sigma,graphs-3d,main}.js`;
`css/rf-{palette,tables,graphs,report}.css`.

**New (tests):** `report/src/test/javascript/*.test.cjs` (canary, dual-engine,
data-embedder, theme, theme-redraw, hierarchy-dot, tables, sticky, csv, copy, charts,
graphs-vizdom, graphs-sigma, graphs-3d, palette-parity, contrast, keyboard-parity);
`report/e2e/` (playwright config + `file-protocol`, `simple-report`,
`interactive-skeleton`, `dark-mode`, `report-features`, `graphs`, `wcag-axe`,
`parity-contrast`, `parity-dom`, `parity-charts` specs).

**Deleted (Java):** `SUGIYAMA_SIGMA_GRAPH`, `FORCE_3D_GRAPH`, `POPUP_STYLE`,
`POPUP_FUNCTIONS` text blocks; `printHead()` CDN list; `renderDisharmonyChart`;
`generateDotImage`; `generateGraphButtons`; `toJavaScriptTemplateLiteral`;
`renderSafeNodeId`/`renderSafePackageNodeId` (subsumed by `HierarchyBuilder` ids);
Google Charts usage in the report path.

**Modified:** `SimpleHtmlReport` (template pipeline), `HtmlReport` (template pipeline),
`JsonGenerator` (uses `ReportModelBuilder`), `RefactorFirstMavenReport` (new pipeline),
`../../report/pom.xml` (mustache compile-scope 0.9.14, exec-maven-plugin `js-tests` profile),
`.github/workflows/{maven.yml,maven-pr.yml}` (+ node leg), new
`.github/workflows/{js-tests.yml,e2e.yml}`, `AGENTS.md`, `CLAUDE.md`, `README.md`,
regenerated `junit4-report.html` / `fxgl-report.html` samples.

---

*End of plan (parts 1–4). Suggested session title: "RefactorFirst feature-parity report migration (TDD)".*
