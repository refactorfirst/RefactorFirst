
---

### Phase P4 — `HtmlReport` migration to single-file template + Chart.js (kills Google Charts)

**Goal:** the interactive report renders from `refactor-first-report.mustache` with all
data inline, all assets inlined from classpath JS/CSS files, Chart.js bubble charts,
pinned CDNs, and full `file://` compatibility. Google Charts is deleted.

- **T4.1 (Red)** `InteractiveTemplateTest`: render fixture through
  `refactor-first-report.mustache`; assert structure: single `<h1>`, skip link,
  `main#app`, section per disharmony with `<canvas id="chart_{{anchorId}}">` +
  `role="img"` + `aria-label` (chart title + "bubble chart" description), graph button
  rows (2D/3D), popup container, footer; **asserts zero `gstatic.com` / `google.charts`
  references anywhere in output** (this is the feature-8 regression gate, kept forever).
- **T4.2 (Red)** `ChartJsConfigTest` (Java): the template's data block exposes
  `disharmonies[n].chart` with `canvasId`, axis labels (page repo appends U+27F6 to
  defaults "Effort to refactor"/"Relative churn (impact)" — port exact strings), and
  bubbles `{x, y, r, color, borderColor, label, priority, path}` where
  `color = rgba(235*t… interpolation)` matches `ReportModelBuilder.createBubble` exactly
  (reuse — single source from T1.5). Golden: fixture bubble arrays pinned (compare
  against page repo's `tests/fixtures/junit4-report.json` values, e.g. priority 2 of 14
  → `rgba(220,72,55,0.75)`).
- **T4.3 (Red)** `charts.test.cjs` (Node, stubbed `Chart`): `rf-charts.js` builds the
  config the page repo builds: bubble type; custom `generateLabels` legend with exactly
  three entries — `High Priority (1)` `rgb(235,64,52)`, `Medium Priority`
  `rgb(137,119,74)`, `Low Priority (max)` `rgb(39,174,96)`; grid `#e0e0e0`; legend/axis
  text from theme (`resolveLegendTextColor` → `#5c6b7a` light / `#9fb0c0` dark); tooltip
  lists File/Priority/Effort Rank/Change Proneness Rank + "Click to open source file";
  `onClick` opens `repoUrl + path` (`isHttpUrl`-gated, `noopener`); `onHover` sets
  pointer cursor only when clickable.
- **T4.4 (Red)** `AssetInlinerTest`: deterministic ordering (palette → tables → graphs
  → report CSS; core → theme → hierarchy-dot → tables → csv → copy → charts → graphs →
  main JS); every classpath file present in output; no file loaded via `src=`; idempotent
  id comments per asset; MinifyHtml(`setMinifyJs(true)`) round-trip leaves scripts
  runnable (parse-check via Node in `js-tests` profile + E2E minified leg).
- **T4.5 (Red)** `PinnedCdnTest`: assert exact pinned URLs in generated output
  (chart.js@4.4.7 UMD, sigma.js@2.4.0, graphology@0.25.4, graphlib-dot@0.6.4,
  svg-pan-zoom@3.6.1, vizdom-ts-web@0.1.19, esm.sh `3d-force-graph@1.80.1?deps=three@0.186.1`,
  `three-spritetext@1.10.0`, mvp.css@1.15, buttons.github.io) and assert **no unpinned
  specifiers** (`npm/3d-force-graph"` without version, `unpkg.com/mvp.css"` without
  version).
- **T4.6 (Green)** Implementation: write the template (port page repo's
  `assets/refactor-first-report.mustache`, adapted: full server-rendered rows per D5,
  `rf-data` JSON block per D3, no `tableUi` client-state baked in, control placeholders
  for P6), write `rf-core.js`/`rf-data` reader + `rf-charts.js`, `AssetInliner`,
  rewire `HtmlReport.execute()`/`generateReport()` to the pipeline; update
  `RefactorFirstMavenReport` (Doxia) to use the same pipeline and delete its duplicated
  `printHead` imports and dead `renderStyle` call.
- **T4.7 (Refactor)** Delete from `HtmlReport.java`: `SUGIYAMA_SIGMA_GRAPH`,
  `FORCE_3D_GRAPH`, `POPUP_STYLE`, `POPUP_FUNCTIONS`, `printHead()` CDN list,
  `renderDisharmonyChart`, `generateDotImage`, `generateGraphButtons`,
  `toJavaScriptTemplateLiteral`, `renderSafeNodeId`/`renderSafePackageNodeId` (move any
  still-needed id logic into the model builder if the JSON needs it — it already has
  ids from `HierarchyBuilder`). Graph popups temporarily render DOT that is still
  Java-built (parity bridge) — replaced in P7. Remove `graph-data-generator`'s Google
  Charts-specific assembly from the report path (keep the module for CSV/legacy needs
  if referenced elsewhere).
- **T4.8** E2E `interactive-skeleton.spec.js`: charts render (canvas has non-blank
  pixels), zero console errors, zero CORS errors, `window.RF` present, charts redraw
  stub; both `minifyHtml=false` and `true` legs.

**Exit criteria:** generated `refactor-first-report.html` is single-file, opens via
`file://`, shows Chart.js bubble charts with page-repo colors; grep proves Google
Charts gone; all prior tests green except those remapped in §10.3.

---

### Phase P5 — Light / dark / system theme (system = default)

**Goal:** exact page-repo theming: three radio pills (`rf-theme-light/dark/system`),
`system` `defaultChecked`, pure-CSS switching, persistence, canvas/graph redraw.

- **T5.1 (Red)** `palette-parity.test.js` (Node) + `PaletteParityTest` (JUnit): parse
  `rf-palette.css` custom properties; assert every token equals `palette.json`
  (Appendix B) for light, dark, and the `prefers-color-scheme: dark` + system
  combination; assert the dark blocks for `:has(#rf-theme-dark:checked)` and the media
  query are byte-identical (port of page repo's `theme-css.test.js` guard).
- **T5.2 (Red)** `theme.test.cjs`: `rf-theme.js` — reads/persists
  `localStorage['rf-theme']` (invalid values fall back to `system`); pub-sub fires
  `onThemeChange` on radio change and on `prefers-color-scheme` change while in
  `system`; never touches styles itself (asserts zero inline style writes — D6).
- **T5.3 (Red)** `theme-redraw.test.cjs`: `rf-charts.js` `redrawChartsForTheme`
  re-reads legend/axis text colors; `rf-graphs-vizdom.js` `redrawGraphsForTheme`
  re-synthesizes DOT via `rf-hierarchy-dot.js` with dark option (`fontcolor="#9fb0c0"`
  edge labels) and re-renders; sigma popups rebuilt on open with
  `sigmaPopupSettings()` (dark: labelColor + `drawDarkHover` renderer, hover bg
  `#1a2330`); 3D always uses the dark accent set (WebGL canvas paints its own
  background).
- **T5.4 (Red)** `ThemeWcagTest` (JUnit, on template): `role="radiogroup"
  aria-label="Color theme"`; three radios share `name="rf-theme"`; labels are the icon
  pills with accessible text; pills ≥ 24×24 px target (2.5.8).
- **T5.5 (Green)** Implement `rf-palette.css`, theme radios in template, `rf-theme.js`,
  redraw hooks; wire `rf-main.js` bootstrap order (theme → tables → charts → graphs).
- **T5.6** E2E `dark-mode.spec.js` (port core assertions from page repo): switching
  radios flips computed `--bg-color`/`--text-color` on `body`; `system` follows
  `colorScheme` emulation; persistence across reload; chart legend text color changes;
  vizdom SVG re-renders with dark edge label color; zero console errors after N toggles.

**Exit criteria:** all three modes verified in chromium; palette fixture green;
redraws verified; page-repo parity spot-check documented in P9.

---

### Phase P6 — Tables: sticky headers, filtering, sorting, pagination, CSV export, click-to-copy

**Goal:** page-repo table experience, ported as pure functions (`rf-tables.js`,
`rf-csv.js`, `rf-copy.js`) + thin DOM wiring, over server-rendered full tables (D5).

- **T6.1 (Red)** `tables.test.cjs` (pure functions, DOM-free): port
  `table-operations.js` semantics — `filterRows` (case-insensitive substring across
  all columns, `stripHtml` before matching), `sortRows` (per-`TABLE_CONFIG` column
  types: numeric, date-time with localized `, ` delimiter — reuse the `isDateTime`
  /`isNumber` rules from `SimpleHtmlReport` :941–949 as the Java-side oracle in a
  comment/cross-test; `aria-sort` cycle ascending→descending→none; indicators ▲/▼/blank),
  `paginate` (defaults from page repo `TABLE_CONFIG`), `matchStatus` "N of M rows
  match", `pageStatus` "Page X of Y".
- **T6.2 (Red)** `sticky.test.cjs` (vm with tiny fake layout math): port
  `measureHorizontalScroll` toggle logic (`scrollWidth > clientWidth + 1` → add
  `rf-scroll-x-enabled`) and `refreshStickyHeaders` transform clamping math
  (translateY ≤ table bottom; clear stale transforms) as pure calculations; DOM
  behavior covered in E2E.
- **T6.3 (Red)** `csv.test.cjs`: port `escapeCsvValue` (RFC 4180: quote-doubling,
  wrap on comma/quote/newline; **formula-injection neutralization**: leading
  `= + - @` after whitespace gets apostrophe prefix), `buildCsv` (export honors
  current filter + sort, ignores pagination), `generateCsvFilename`
  (`refactorfirst-<slugified-table-id>-<ISO-timestamp>.csv`), `triggerDownload`
  (Blob `text/csv;charset=utf-8;` → object URL → temp `<a download>` → revoke).
- **T6.4 (Red)** `copy.test.cjs`: `copyCellContent` — `navigator.clipboard.writeText`
  with textarea+`execCommand('copy')` fallback (never throws); toast message dedupe;
  3 s auto-dismiss. Template contract: every `tbody td` gets `data-rf-copy`,
  `tabindex="0"`, `title="Click to copy this cell"`; Enter/Space handled; `role="status"
  aria-live="polite"` toast region.
- **T6.5 (Red)** `TableControlsWcagTest` (JUnit, on template + JS-injected markup
  contract): search input has visually-hidden `<label>` + `aria-describedby` →
  `role="status"` match-count live region; × clear button toggles `hidden`; export
  button aria-label `Export the … table as CSV`; sortable headers are real `<button>`
  with valid `aria-sort` values; pagination `<nav aria-label="Pages of …">`; focus
  restored to search input with caret position after re-render (page repo
  `pendingFocusRef` pattern); all controls ≥ 24 px targets; `:focus-visible` ring on
  copy cells.
- **T6.6 (Green)** Implement `rf-tables.css` (port `.rf-data-table thead th` sticky
  block verbatim from page repo template lines 104–118 including the inset shadow
  `#1f5273`; `.rf-table-scroll` wrapper; `overflow` rules), `rf-tables.js` (capture
  rows from DOM once → rebuild `<tbody>` on state change; re-wire copy targets and
  focus after rebuild), `rf-csv.js`, `rf-copy.js`; enhance button + label wiring in
  template.
- **T6.7** E2E `report-features.spec.js` (port the strongest assertions from page
  repo's `report-pagination.spec.js`): sticky headers stay pinned while scrolling wide
  and tall tables; filter narrows rows + match count updates + Escape and × clear;
  keyboard-only sort/filter/export; CSV download content verified (Playwright download
  event → assert file text for fixture rows incl. formula-injection row); copy cell →
  clipboard permission grant → toast visible; charts and graphs survive re-renders;
  zero console errors throughout.

**Exit criteria:** all table interactions pass in chromium E2E from `file://`; pure
functions byte-tested against page-repo semantics; WCAG contract tests green.

---

### Phase P7 — Graphs: vizdom, Sigma 2D, 3D force-graph with colored mouseover

**Goal:** all three renderers driven by inline `hierarchy` payloads via
`rf-hierarchy-dot.js`, each with the page repo's colored hover model + keyboard parity.

- **T7.1 (Red→Green)** `hierarchy-dot.test.cjs` (Node): `rf-hierarchy-dot.js` on
  P1 fixture hierarchies → byte-identical to `NestedDotBuilder` goldens (light);
  dark-mode goldens = light + `fontcolor="#9fb0c0"` edge labels + dark break-up
  cluster colors (`--graph-breakup-fill #7f3350` / line `#f2b8cd` per dark palette);
  unsafe-id rejection; `isDotId` producer contract; `repoUrl` hyperlink gating to
  http(s); subgraph/cluster emission rules (pink `fillcolor=pink` break-up clusters,
  white explicit fills, red removals). This test *is* the AGENTS.md three-implementations
  constraint, reduced to two and cross-pinned.
- **T7.2 (Red)** `graphs-vizdom.test.cjs`: hover model builder from vizdom `<title>`
  texts (`"NodeA"` / `"Source -> Target"`) → neighbor sets; **edge hover → edge + both
  endpoints amber (`rf-hover-edge`)**; **node hover → node amber focus,
  incoming edges+sources green (`rf-hover-in`), outgoing edges+sinks blue
  (`rf-hover-out`), mutual partner amber**; idempotent recompute; class toggling only
  on changed nodes (diff, not full repaint); `focusin`/`focusout` parity (keyboard).
  Assert `rf-graphs.css` fills: amber/green/blue from `palette.json`
  (`#b45309/#1e8449/#2a6f97` light; `#f0b95a/#7ee2a8/#8ed0fa` dark), edge width 2.5 px,
  label re-ink to `--graph-hover-on`.
- **T7.3 (Red)** `graphs-sigma.test.cjs` (vm, stubbed sigma/graphology/graphlib-dot):
  `buildGraphologyGraph` drops subgraph/cluster nodes (`children > 0` rule), arrow
  edges, sugiyama layout positions; `bindSigmaHoverHighlight` — `nodeReducer`
  returns `{color: accent, highlighted: true}` on hovered node; `edgeReducer`
  `{color, size: 3}` on adjacent edges; `enterNode`/`leaveNode` wiring; dark hover
  renderer uses `#1a2330` label box (parity with page repo `drawDarkHover`).
- **T7.4 (Red)** `graphs-3d.test.cjs` (vm, stubbed `ForceGraph3D`): node/link arrays
  from DOT; `onNodeHover`/`onLinkHover` — `nodeStates`/`linkStates` maps,
  hovered node `rgb(255,0,0,1)`, highlighted neighbors `rgba(255,160,0,0.8)`,
  base `rgba(0,255,255,0.6)` (existing `HtmlReport` contract), link width 4:1,
  4 directional particles, `touchedNodes` O(changed) recolor; DOT's own colors show
  when nothing hovered; `< 4000` threshold guard parity (`dotThresholdExceeded` →
  simplified 3D path, "SVG too big" placeholder).
- **T7.5 (Red)** `PinnedCdnTest` extension (T4.5): 3D popup module imports pinned
  esm.sh URLs; vizdom module URL pinned; both wrapped in feature detection that
  renders a visible, aria-live error notice instead of silent breakage when offline/
  proxy-blocked.
- **T7.6 (Green)** Implement `rf-hierarchy-dot.js` (port from page repo
  `lib/hierarchy-dot.js`, keep dark option), `rf-graphs-vizdom.js`,
  `rf-graphs-sigma.js`, `rf-graphs-3d.js`; template emits `hierarchy` payloads (already
  inside `rf-data`) + graph buttons + popup shells; delete the P4 parity-bridge
  Java-DOT path (`HtmlReport` no longer embeds any DOT).
- **T7.7** E2E `graphs.spec.js`: hover a vizdom edge → classes applied (assert via
  `getComputedStyle` fill = amber); hover node → directional in/out colors; tab
  through node anchors reproduces hover (keyboard parity); open Sigma popup → hover
  node → reducer color; open 3D popup (WebGL) → hover → color/particle assertions via
  stubs where headless WebGL is unreliable, plus smoke "no page errors"; theme switch
  re-renders graphs (ties to T5.3).

**Exit criteria:** three renderers + hover model + threshold behavior green; DOT
synthesis contract cross-pinned Java↔JS; update AGENTS.md constraint wording
(three implementations → two, with sync contract Appendix C).
