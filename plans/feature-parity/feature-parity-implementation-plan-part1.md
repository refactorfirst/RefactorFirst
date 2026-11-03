# Feature Parity Implementation Plan (TDD) — RefactorFirst HTML Reports

> **Combine parts:** this file is part 1 of 4. Concatenate
> `feature-parity-implementation-plan-part1.md` … `-part4.md` into
> `feature-parity-implementation-plan.md` (sections are numbered continuously).
>
> Repositories (as present on this machine):
> - **Java/plugin repo:** `/mnt/c/Code/RefactorFirst` (referred to as **RefactorFirst**)
> - **Report UI site:** `/mnt/c/Code/refactorfirst-page` (referred to as **the page repo**)
>
> Version: v2 (post critical review) — see §11 for the review record and the changes it drove.
> Status of facts below: verified directly against source on 2026-11-03.

---

## 1. Executive summary

The page repo renders RefactorFirst reports with a modern UI: sticky table headers,
light/dark/system theming, WCAG 2.2 AA accessibility, table filtering/sorting/pagination,
CSV export, click-to-copy cells, colored mouseover highlighting in all three graph
renderers, and Chart.js bubble charts. The Java-generated `htmlReport` output
(`HtmlReport.java`) has none of these: it ships 6–12 Google Charts bubble charts, an
unpinned 3D graph library, no theming, no table interactivity, and no hover highlighting
outside the 3D popup.

This plan migrates **`SimpleHtmlReport`** and **`HtmlReport`** to **Mustache templates**
rendered server-side in Java, emitting a **single self-contained `.html` file per report**
that opens directly in a browser via `file://` without CORS errors. All interactive
features are ported from the page repo into **separate, individually-tested JavaScript
files** bundled into the interactive report at generation time. `SimpleHtmlReport` output
remains **strictly HTML (zero JavaScript)** because it is echoed into
`$GITHUB_STEP_SUMMARY`.

Everything is built test-first (JUnit 5 + Node built-in test runner + Playwright E2E),
with golden/parity tests pinning visual and behavioral parity with the page repo.

### The core architectural move

Today there are **four** copies of report logic: Java string concatenation
(`SimpleHtmlReport` + `HtmlReport` text blocks), the client-side Mustache template
(`../../report/src/main/resources/templates/refactor-first-report.mustache`), the inline
viewer (`viewer/index.html`), and the page repo (`lib/hierarchy-dot.js`, template copy).
The JSON data path (`JsonGenerator` → `RefactorFirstReportDTO`) already exists and is
already what the page repo consumes.

The plan converges everything onto that data path:

```
                    ┌─────────────────────────────────────────────────┐
                    │ ReportModelBuilder  (analysis → RefactorFirstReportDTO)
                    │ shared by SimpleHtmlReport / HtmlReport / JsonGenerator
                    └───────────────┬─────────────────────────────────┘
                                    │ DTO
              ┌─────────────────────┼──────────────────────────────┐
              ▼                     ▼                              ▼
   ViewModelFactory        Jackson serialization          (future: viewer)
   (DTO → Mustache data)   (single JSON data block)
              │                     │
              ▼                     ▼
   refactor-first-report.mustache  +  <script type="application/json" id="rf-data">
   (server-rendered, full tables)          │
              │                            │
              └────────────┬───────────────┘
                           ▼
              AssetInliner: inline rf-*.js + rf-*.css from classpath,
              pinned CDN tags, HTMLReport enhancements
                           │
              ┌────────────┴─────────────┐
              ▼                          ▼
   refactor-first-report.html    refactor-first-simple-report.mustache
   (interactive, single file,    (strictly HTML, no JS, same model)
    opens via file://)
```

**Why this is CORS-safe by construction:** all report data (hierarchy payloads, chart
configs, project metadata) is embedded as inline `<script type="application/json">`
blocks — no `fetch()` of local files (the one pattern that breaks `file://`, and the
reason `viewer/index.html` ships a "CORS Notice" file-picker fallback today). All
libraries load from pinned HTTPS CDNs, which work from `file://`. The page repo proves
the runtime contract; the existing `HtmlReport` proves the file:// delivery contract;
this plan joins them.

---

## 2. Goals, non-goals, scope

### 2.1 In scope (the 9 parity features)

| # | Feature | Phase |
|---|---------|-------|
| 1 | Sticky column headers for tables | P6 |
| 2 | Light / dark mode + **system** option (system = default) | P5 |
| 3 | WCAG 2.2 AA compatibility | P3, P6, P8 (throughout) |
| 4 | Table filtering (+ sorting & pagination for parity — see §11, gap G1) | P6 |
| 5 | CSV export | P6 |
| 6 | Table cell click-to-copy | P6 |
| 7 | Colored mouseover in vizdom, Sigma.js, and force-graph-3d renderers | P7 |
| 8 | Chart.js instead of Google Charts | P4 |
| 9 | Color parity with the page repo (no discernable difference) | P5, P9 |

Plus the two structural migrations:

- **M1.** `SimpleHtmlReport` and `HtmlReport` generate from Mustache templates (server-side, Java).
- **M2.** Each report is a single file with an `.html` extension that opens directly in a browser (`file://`) with no CORS issues. Features live in their own JS files (maintainability).
- **M3.** `SimpleHtmlReport` output is strictly HTML — no JavaScript, no JS-dependent features (GitHub Actions `$GITHUB_STEP_SUMMARY` contract).

### 2.2 Out of scope (explicitly)

- Changes to `refactorfirst-page` (its `lib/hierarchy-dot.js`, template, and tests are a
  *sync target*, not modified here). A cross-repo sync contract is documented in Appendix C.
- `jsonReport` goal / `viewer/index.html` behavior beyond what shared-code extraction
  forces (its disabled `copyViewerResources()` stays disabled; re-enabling is listed as an
  optional follow-up, §12.3).
- CSV report, legacy `jsonreport` goal, gradle-plugin version bump (it pins
  `report:0.7.2-SNAPSHOT` — flagged as a separate chore in §10).
- Replacing mvp.css with a bespoke base stylesheet (parity is cheaper by pinning mvp.css
  and porting the page repo's overrides verbatim).
- Fixing the pre-existing XSS-hardening plan items (already implemented via `escapeHtml*`).

### 2.3 Compatibility guarantees (non-negotiable)

1. Maven goals, names, and parameters unchanged (`htmlReport`, `simpleHtmlReport`, …
   with `showDetails`, `backEdgeAnalysisCount`, `analyzeCycles`, `minifyHtml`,
   `excludeTests`, `testSourceDirectory`).
2. Output paths and filenames unchanged: `../../target/site/refactor-first-report.html`
   (both goals write the same filename today; documented behavior, must not change).
3. `execute(...)` public signatures unchanged — the CLI (`cli/ReportCommand.java`,
   types `HTML`, `SIMPLE_HTML`) and Gradle tasks (`HtmlReportTask`,
   `SimpleHtmlReportTask`) keep compiling unmodified.
4. `mvn site` integration (`RefactorFirstMavenReport`, Doxia sink) keeps producing a
   working report (it currently re-uses `HtmlReport.generateReport(...)` via
   `rawText`; it must be updated to the new pipeline and its dead `renderStyle` bug
   removed — Phase P4, task T4.7).
5. `JsonGenerator` (which **extends `HtmlReport`**) keeps producing byte-compatible
   `refactor-first.json` — all 14 `JsonGeneratorTest` tests stay green (Phase P1
   extracts the shared builder first precisely to protect this).

---

## 3. Current state — verified findings

### 3.1 RefactorFirst (Java side)

**Report generators** (`../../report/src/main/java/org/hjug/refactorfirst/report`):

- `SimpleHtmlReport.java` (~1,300 lines): giant `StringBuilder` + Java 17 text blocks
  (`THE_BEGINNING`, `THE_END`, `printBreadcrumbs`). Produces strictly-HTML output:
  GitHub links block, nav menu, class/package relationship-removal tables, 11
  disharmony sections (11 `DisharmonySpec`s at lines 52–161), class-cycle summary,
  largest-cycle breakdown. **Zero JavaScript** — every extension hook
  (`printTitle` :952, `printHead` :957, `printScripts` :962, `printOpenBodyTag` :967,
  `renderDisharmonyChart` :1232, `renderClassGraphVisuals` :918,
  `renderPackageGraphVisuals` :923) returns `""` in the base class.
  Optional MinifyHtml pass (`minifyHtml` flag; config sets `setMinifyJs(true)` /
  `setMinifyCss(true)`). `execute()` :187, `generateReport()` :233,
  `getOutputName()` → `"refactor-first-report"` :1019.
- `HtmlReport.java` (~960 lines) **extends `SimpleHtmlReport`** and injects
  interactivity by overriding hooks. Interactive JS/CSS lives in four Java text
  blocks: `SUGIYAMA_SIGMA_GRAPH` (:24–143, Sigma 2D popup), `FORCE_3D_GRAPH`
  (:145–313, `<script type="module">` importing `three-spritetext` from esm.sh,
  3D popup **with** hover highlighting), `POPUP_STYLE` (:316–376), `POPUP_FUNCTIONS`
  (:379–421). `printHead()` (:424–439) emits 8 CDN tags: github buttons,
  **`gstatic.com/charts/loader.js` (Google Charts, :429)**, svg-pan-zoom@3.6.1,
  sigma.js@2.4.0, graphology@0.25.4, graphlib-dot@0.6.4, **`3d-force-graph`
  UNPINNED**, `@vizdom/vizdom-ts-web@0.1.19` (`type="module"`), plus
  **unpinned** `unpkg.com/mvp.css`. Charts are Google Charts bubble charts
  (`renderDisharmonyChart` :492–513 fed by `GraphDataGenerator` in
  `../../graph-data-generator`). Graph data is embedded as JS template-literal consts
  (`const <id>_dot = \`...\`` via `toJavaScriptTemplateLiteral` :899–906, which
  escapes `\ ` ` $ < >`); node ids via `renderSafeNodeId` (hex-encoded, `class_`
  prefix) and `renderSafePackageNodeId` (`package_` + SHA-256 12-hex).
  `dotGraphThreshold = 4000` gates vizdom SVG rendering.
- `JsonGenerator.java` **extends `HtmlReport`**, writes
  `../../.refactorfirst/refactor-first.json` from a `RefactorFirstReportDTO`
  (Jackson, INDENT_OUTPUT). Contains `createBubble()` :689 — the **red→green
  bubble interpolation** (`rgba(235,64,52,0.75)` at priority 1 →
  `rgba(39,174,96,0.75)` at max; `red = round(235*(1-t) + 39*t)`) — which is
  exactly the color data the page repo's Chart.js charts consume.
  `copyViewerResources()` is commented out (:82).
- DTOs: `org.hjug.refactorfirst.report.model.*` — 21 classes including
  `RefactorFirstReportDTO`, `ProjectMetadataDTO`, `ClassMap`-style
  `GraphVisualDTO`/`HierarchyDTO`/`EdgeDTO`/`ClassNodeDTO`/`PackageNodeDTO`,
  `ClassRelationshipsToRemoveDTO`, `PackageRelationshipsToRemoveDTO`,
  `ClassCyclesDTO`/`LargestCycleDTO`/`CycleSummaryDTO`/`CycleBreakdownRowDTO`,
  `DisharmonySectionDTO`/`DisharmonyChartDTO`/`ChartJsBubbleDTO`/
  `DisharmonyTableDTO`/`DisharmonyTableRowDTO`/`DisharmonyTableCellDTO`.
  **The DTO already carries everything both reports render** (verified against the
  dogfood `../../.refactorfirst/refactor-first.json`: metadata, class/package maps with
  `hierarchy` payloads + `dotThresholdExceeded`, relationships-to-remove,
  9–12 disharmony sections with problem/solution/chart/table, class cycles).

**Resources** (`../../report/src/main/resources`):

- `templates/refactor-first-report.mustache` (444 lines) — client-side-rendered by
  the viewer with mustache.js 4.2.0. Has CDN tags (incl. unpinned `3d-force-graph`),
  popup CSS, Chart.js `<canvas id="chart_{{anchorId}}">` (:332) — but **no sticky
  headers, no `<caption>`, no `scope=col`, no theme support** (grep-verified).
- `viewer/index.html` (682 lines) — interactive viewer; `fetch('./refactor-first.json')`
  and `fetch('./refactor-first-report.mustache')` (:482–483) — **both fail on
  `file://`**, hence the "CORS Notice" file-picker fallback (:24–28).

**Tests** (JUnit 5.13.3, all string-assertion, no browser tests): 18 classes —
`HtmlReportTest` (24), `HierarchyBuilderTest` (18), `ReportWriterTest` (17),
`JsonGeneratorTest` (14), `DisharmonyRenderingTest` (14), `SimpleHtmlReportTest` (13),
`NestedDotBuilderTest` (10, byte-for-byte DOT goldens), `ViewerScriptTest` (7, pins the
viewer's inline DOT-synthesis copy), `MustacheTemplateRenderingTest` (6, renders the
template with the **test-scope** Java mustache compiler), plus smaller ones.
One JS test exists (`../../report/src/test/javascript/viewer.test.cjs`, node built-in
`node:test` + `vm`, stubbed libs) but is **not wired into Maven**.

**Build:** Java 17 root pom; `mustache-java compiler:0.9.10` is **test-scope only**
(`../../report/pom.xml` :30–35); minify-html 0.15.0; report module is a plain jar (resources
reach the Maven plugin / CLI shade / Gradle shadow transitively). CI (`maven.yml`,
`maven-pr.yml`) runs `mvn -B verify` on JDK 17/25 — **no Node**.

**Plugin surface** (`../../refactor-first-maven-plugin`): goals `htmlReport`
(`RefactorFirstHtmlReport`), `simpleHtmlReport`, `jsonReport`, legacy `jsonreport`,
`csvreport`, and the Doxia site report (`RefactorFirstMavenReport`, which duplicates the
CDN head imports and has a dead/broken `renderStyle` call that is never invoked).

**Samples at repo root:** `../../junit4-report.html` (~300 KB, 6 Google Charts sections),
`fxgl-report.html` (~984 KB, 12 Google Charts sections) — both fully self-contained
single files whose only external refs are CDN scripts (no fetch, no importmap,
no jQuery — grep-verified).

### 3.2 refactorfirst-page (the parity target)

- Runtime deps: `next ^16.3.5`, `react ^19.3.0`, `mustache ^4.2.0`,
  `dompurify ^3.4.15`. All graph/chart libs are **pinned CDN scripts** (chart.js@4.4.7
  UMD, sigma.js 2.4.0, graphology 0.25.4, graphlib-dot 0.6.4, svg-pan-zoom 3.6.1) or
  vendored widgets (`public/widgets/vizdom_ts.min.js` + wasm v0.1.19;
  `force-graph-bridge.js` pinning `3d-force-graph@1.80.1?deps=three@0.186.1` +
  `three-spritetext@1.10.0` via esm.sh). **Google Charts: zero references** (grep-verified).
- Key libs: `lib/report-view.js` (1,380 lines — charts, popups, hover, theme redraw),
  `lib/renderer.js` (Mustache + DOMPurify + `buildTableSlice` which injects `tableUi`
  metadata: `matchStatus` "N of M rows match", `pageStatus` "Page X of Y", `colSort`,
  `colIndicator` ▲/▼), `lib/table-operations.js` (**pure functions**: filter/sort/
  paginate/CSV/copy + `TABLE_CONFIG`), `lib/table-enhancer.js` (DOM wiring,
  `measureHorizontalScroll`, `refreshStickyHeaders` :189–209, `buildSearchControl`
  with 300 ms debounce, Escape-to-clear, × clear button, focus restoration),
  `lib/hierarchy-dot.js` (client-side DOT synthesis from `hierarchy` payloads,
  byte-identical to Java `NestedDotBuilder`, with a dark `edgeFontColor #9fb0c0` option).
- Test suites: ~787 bun unit/integration tests (jsdom) and ~232 Playwright E2E tests —
  including `css-a11y.test.js` (computed contrast ≥ 4.5:1 text / ≥ 3:1 focus, ≥ 24 px
  targets), `theme-css.test.js` (byte-for-byte guard on the duplicated dark palette
  blocks), `report-template-wcag.test.js` (single h1, heading order, `<caption>`,
  `scope="col"`, canvas `role="img"` + text alternative, `nav aria-label`,
  valid `aria-sort`, keyboard-operable sort, live match-count regions, aria-labelled
  export buttons, sticky-header CSS), `report-pagination.spec.js` and `dark-mode.spec.js`.
  Fixture: `tests/fixtures/junit4-report.json` (a real plugin-published report).

### 3.3 Why the current `HtmlReport` mostly works on `file://` — and where CORS bites

Works today because everything is inline: DOT consts, chart data arrays, popup scripts.
The residual hazards this plan eliminates or contains:

| Hazard | Today | Plan |
|---|---|---|
| `fetch()` of local files | viewer only (`jsonReport` path) — broken on `file://` | forbidden in generated reports; all data inline JSON |
| ES-module imports from CDNs | esm.sh (three-spritetext), jsDelivr (vizdom) — works only because those CDNs send `Access-Control-Allow-Origin: *`; breaks behind strict proxies/offline | kept, but **pinned**, wrapped in feature detection with graceful fallback messaging; all *our own* JS stays classic scripts |
| Unpinned CDN deps | `3d-force-graph`, `mvp.css` | pinned (`3d-force-graph@1.80.1`, `mvp.css@1.15`) |
| `</script>`/template-literal injection in embedded data | hand-rolled escaping of DOT template literals | eliminated: data moves to `<script type="application/json">` + `JSON.parse`; only `</` → `<\/` and U+2028/2029 escaping needed (tested) |
| MinifyHtml × inline JS | `setMinifyJs(true)` could corrupt inline scripts | explicit test leg with `minifyHtml=true` |

---

## 4. Target architecture

### 4.1 Generation pipeline (Java, in `report` module)

```
execute(...)                                    ← signature unchanged (CLI/Gradle/mojo compat)
  │
  ├─ ReportModelBuilder.build(...)              ← NEW: analysis → RefactorFirstReportDTO
  │     (extracted from HtmlReport/JsonGenerator shared logic; deterministic clock)
  │
  ├─ ViewModelFactory.toTemplateData(dto)       ← NEW: DTO → Map<String,Object> mustache
  │     (mirrors page repo's prepareReportData; pre-escaped strings; section flags;
  │      rows in page-repo cell shape; NO client-state injected)
  │
  ├─ TemplateEngine.render(template, data)      ← NEW wrapper over mustache.java
  │     (promote compiler 0.9.14 to compile scope; compile + cache)
  │
  ├─ [SimpleHtmlReport]  render "refactor-first-simple-report.mustache"
  │        → strict-HTML validator → ReportWriter (unchanged)
  │
  └─ [HtmlReport]
        ├─ render "refactor-first-report.mustache"   (NEW canonical template, ported
        │        from page repo, adapted for server-side full-row rendering)
        ├─ DataEmbedder.inject(html, dto)   ← <script type="application/json"
        │        id="rf-data">{ ... }</script>  (Jackson + </  and U+2028/9 escaping)
        ├─ AssetInliner.inject(html)        ← inline rf-*.css + rf-*.js from classpath,
        │        pinned CDN tags, in deterministic order
        ├─ [minifyHtml] MinifyHtml.minify(...)   (existing flag, unchanged)
        └─ ReportWriter.writeReportToDisk(...)   (unchanged → target/site/)
```

### 4.2 The JavaScript feature files (classpath resources, inlined at generation)

Location: `report/src/main/resources/js/` and `report/src/main/resources/css/`.
Each file is an IIFE exposing a single namespace object on `window.RF` (no ESM in our
own code → no module/CORS coupling, no importmap, testable in Node via `vm`).
`AssetInliner` emits each file in its own `<script>` tag in the order below.

| File (new) | Owns | Ported from (page repo) |
|---|---|---|
| `rf-core.js` | `RF` namespace, DOM utils, `JSON.parse` of `#rf-data`, error/fallback messaging | new (small) |
| `rf-theme.js` | theme read/persist (`localStorage['rf-theme']`), `prefers-color-scheme` listener, `onThemeChange` pub-sub | `app/layout.jsx` inline script + theme redraw triggers |
| `rf-hierarchy-dot.js` | `hierarchyToDot` / `packageHierarchyToDot` (+ dark `fontcolor` option) | `lib/hierarchy-dot.js` (byte-identical contract with `NestedDotBuilder`) |
| `rf-tables.js` | pure ops: `filterRows`, `sortRows`, `paginate`, `captureRowsFromDom`, tbody rebuild, sticky `measureHorizontalScroll`/`refreshStickyHeaders`, search control | `lib/table-operations.js` + `lib/table-enhancer.js` |
| `rf-csv.js` | `escapeCsvValue` (RFC 4180 + formula-injection guard), `buildCsv`, `generateCsvFilename`, `triggerDownload` | `lib/table-operations.js` CSV section |
| `rf-copy.js` | `copyCellContent` (clipboard → `execCommand` fallback), cell binding, toast | `lib/table-operations.js` + `components/toast-notification.jsx` |
| `rf-charts.js` | Chart.js bubble init, theme-aware redraw, legend, tooltip, click-through | `lib/report-view.js` `initBubbleChart` etc. |
| `rf-graphs-vizdom.js` | vizdom module load, SVG injection, pan/zoom, **hover highlight model** + keyboard parity | `lib/report-view.js` `bindGraphHoverHighlights` |
| `rf-graphs-sigma.js` | Sigma 2D popup, graphology build, sugiyama layout, node/edge **reducer hover**, dark hover renderer | `lib/report-view.js` `showPopup`/`bindSigmaHoverHighlight` |
| `rf-graphs-3d.js` | 3D popup ESM bridge (pinned esm.sh), node/link state maps, colored hover | `lib/report-view.js` `createForceGraph` / `public/widgets/force-graph-bridge.js` |
| `rf-main.js` | bootstrap: read theme → enhance tables → init charts → init graphs → wire popups; re-init on theme change | `components/report-view.jsx` effect chain |

CSS files: `rf-palette.css` (CSS custom properties, verbatim from page repo
`app/globals.css` light + dark blocks), `rf-tables.css` (sticky header, scroll
wrapper, controls, copy affordance), `rf-graphs.css` (popup, hover classes,
fullscreen-svg), `rf-report.css` (layout, mvp.css overrides, focus-visible, skip link).

**Deleted from Java:** `SUGIYAMA_SIGMA_GRAPH`, `FORCE_3D_GRAPH`, `POPUP_STYLE`,
`POPUP_FUNCTIONS` text blocks, `printHead()`'s CDN tag list, `renderDisharmonyChart`'s
Google Charts string building, `generateDotImage`'s inline vizdom module script,
`generateGraphButtons`' template-literal DOT consts — all replaced by template + assets.

### 4.3 HTML contract of the generated interactive file

- `<head>`: charset, viewport, title, pinned CDN tags (classic scripts), inline
  `<style>` blocks (palette → tables → graphs → report, in that order for cascade
  determinism), CSP meta **(optional hardening, §12.3)**.
- `<body class="composite">`: skip link → `<main id="app">` (single `<h1>`, nav with
  `aria-label="Report sections"`, one section per table with `<caption>`, `scope="col"`
  headers, sort buttons with `aria-sort`, filter input + `role="status"` match count,
  export button with aria-label, copy cells with `tabindex="0"`), graph buttons +
  popup container, footer.
- Data: one `<script type="application/json" id="rf-data">…</script>` block
  (full `RefactorFirstReportDTO` serialization — the same object the template rendered).
- Scripts: pinned CDN classics → inline `rf-*.js` classics → two tiny `<script
  type="module">` blocks for vizdom-init and the 3D popup (esm.sh imports; feature-detected).

### 4.4 SimpleHtmlReport output contract (strict HTML)

- Same `ReportModelBuilder` + same sections, different template
  (`refactor-first-simple-report.mustache`): tables with `<caption>`/`scope`/`th`,
  existing presentational markup preserved (`border="5px"` tables, breadcrumb block),
  plus a **small CSS-only `<style>` block** providing sticky theads and palette-true
  colors for direct-browser viewing (CSS is not JavaScript; GitHub's step-summary
  sanitizer strips `<style>` gracefully — behavior there is unchanged).
- **Zero** `<script>`, zero `javascript:` URIs, zero `on*=` attributes, zero
  JS-dependent features (no charts, no filtering, no copy, no CSV, no theme toggle,
  no graph popups) — enforced by a validator test (T3.2).

---

## 5. Key design decisions

| # | Decision | Rationale | Alternatives rejected |
|---|---|---|---|
| D1 | **Server-side Mustache rendering in Java** for both reports; interactive behavior added by JS enhancement over the rendered DOM | Required for M1; gives no-JS graceful degradation of content (WCAG); the Doxia site mojo needs a complete HTML string from Java anyway; no mustache.js dependency shipped | Client-side mustache rendering like the viewer (would re-introduce fetch/cors coupling + ship mustache.js + blank page without JS) |
| D2 | **Two templates, one model**: `refactor-first-report.mustache` (interactive) + `refactor-first-simple-report.mustache` (strict) fed by the same `ViewModelFactory` | Guarantees the simple report can never drift from the interactive one structurally; strict validator only has to police the simple pipeline | One template with a "simple" flag (subtle mustache-section mistakes could leak JS into the simple output — the failure mode this project must never have) |
| D3 | **All inline data as `<script type="application/json">`**, never JS string literals | Kills the whole `toJavaScriptTemplateLiteral` escaping class of bugs; JSON.parse is the parser; only `</` and U+2028/2029 need escaping (tested) | Reuse of DOT template-literal consts (current approach, fragile) |
| D4 | **Client-side DOT synthesis** from inline `hierarchy` payloads (`rf-hierarchy-dot.js`), replacing Java-side DOT embedding in the interactive report | This is exactly how the page repo works (byte-identical to `NestedDotBuilder`, pinned by tests on both sides) → requirement 9 parity + dark-mode DOT re-synthesis (edge `fontcolor #9fb0c0`) comes free; reduces the AGENTS.md "three byte-identical implementations" constraint to: Java `NestedDotBuilder` (golden) + `rf-hierarchy-dot.js` (shared/synced) | Embedding Java-built DOT and regex-recoloring for dark (fragile, loses parity) |
| D5 | **Tables: server renders all rows; JS enhances in place** (capture rows from DOM once → filter/sort/paginate by rebuilding `<tbody>`) | No-JS users get full data; JS users get page-repo behavior; single source of truth (no duplicated JSON per table); focus + scroll state managed on rebuild | Embedding per-table JSON duplicating rendered rows (size ×2, drift risk) |
| D6 | **Theme = pure CSS** `:has()` + `prefers-color-scheme`, 3 radios, `system` default, `localStorage` persistence via `rf-theme.js`; JS only redraws canvas/WebGL/graph-label content on change | Verbatim parity with page repo (its `theme-css.test.js` guards the design); zero-JS theme switching; system default required by feature 2 | JS-driven class toggling (`data-theme`) (diverges from parity target; more JS on the critical path) |
| D7 | **Chart.js 4.4.7 UMD (pinned CDN)**; bubbles rendered from `dto.disharmonies[].chart` (bubbles already carry interpolated colors from `createBubble`); Google Charts deleted | Feature 8; data path already exists (`ChartJsBubbleDTO`); page repo already renders this exact JSON → automatic color parity | Keeping Google Charts behind a flag (dead code, two chart engines) |
| D8 | **Pinned CDNs**, classic UMD scripts for sigma/graphology/graphlib-dot/svg-pan-zoom/chart.js; ESM **only** for vizdom and the 3D bridge (esm.sh, pinned `3d-force-graph@1.80.1?deps=three@0.186.1`, `three-spritetext@1.10.0`) | CORS-safety on `file://` + reproducible behavior (fixes unpinned `3d-force-graph`/`mvp.css`); matches page repo's exact 3D stack (single three.js build) | Vendoring/inlining all libs (file bloat, license noise); importmap (unneeded) |
| D9 | **Palette single-source-of-truth**: port page-repo CSS custom properties verbatim into `rf-palette.css`; a canonical `palette-fixture.json` in test resources pins every hex; JS constants (`GRAPH_HOVER_ACCENTS`, `SIGMA_HOVER_BG`, `CHART_LEGEND_TEXT`) are asserted equal to the fixture in Node tests | Requirement 9 is *testable* only if there is a machine-readable contract; mirrors the page repo's own `report-view.test.js` drift guard | "Eyeball" parity (not verifiable, will drift) |
| D10 | **API surface frozen**; `JsonGenerator` refactored to consume `ReportModelBuilder` (no longer *extends* `HtmlReport` — the inheritance is a hidden coupling that must be broken before template migration) | Protects CLI/Gradle/mojo/JSON consumers; makes `HtmlReport` free to change internally | Keeping `extends` (every `HtmlReport` change risks the JSON report; currently why `renderDisharmonyChart` hooks exist at all) |
| D11 | **Node built-in test runner** (`node:test` + `vm`) for JS units — no npm in the Java build; Playwright only for E2E in a dedicated optional harness | Extends the existing `viewer.test.cjs` pattern; zero new build deps for contributors; CI gets Node via `setup-node` | Adding jsdom/bun to the Java repo (heavier tooling, unclear ownership) |
| D12 | **Goldens everywhere, with timestamp normalization**: DOT goldens (already exist), view-model JSON goldens, dual-engine mustache goldens, bubble-config goldens | TDD on rendering code without goldens = regex whack-a-mole; the repo's existing golden culture (`NestedDotBuilderTest`) proves the pattern works | Pure example-based assertions (miss regressions) |

---

*Continued in part 2: §6 TDD strategy & infrastructure, §7 phases P0–P3.*
