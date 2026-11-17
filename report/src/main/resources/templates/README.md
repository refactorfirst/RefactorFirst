# Mustache Report Templates

This directory holds the mustache templates that render RefactorFirst's generated
reports. The bundled template is `refactor-first-report.mustache` (loaded from the
classpath under the name `templates/refactor-first-report.mustache`).

## Allowed mustache subset (P2 contract)

Only the following mustache constructs may appear in templates used by the generated
reports and their tests. The subset is intentionally minimal so that the Java engine
(`com.github.mustachejava`, `TemplateEngine`) and the browser engine (vendored
`mustache.js`) render byte-identical output:

- Variables: `{{name}}`, dotted paths (`{{project.name}}`), inverted sections
  (`{{^name}}...{{/name}}`), and the implicit iterator `{{.}}` inside a section
  iterating a plain string list (e.g. disharmony table headers; covered by
  `TemplateEngineTest.rendersImplicitIteratorsAndParentScopeDottedLookups` and
  `mustache.dual-engine.test.cjs`).
- Sections: `{{#name}}...{{/name}}` for lists and boolean flags; `{{#a.b}}...{{/a.b}}`
  for nested paths. Nested sections may resolve parent-scope dotted names
  (`{{project.repoUrl}}` from inside a row loop).
- Raw (unescaped) interpolation: `{{{name}}}` for pre-rendered HTML (e.g. disharmony
  solutions, cycle edge labels) — values are XSS-hardened upstream, never raw user input.
- Comments: `{{! ... }}`.

Explicitly **not** supported (and must not be introduced without updating the
cross-language golden first):

- Lambdas — neither engine executes them; Java lambdas and JS functions in views are
  not interoperable.
- Client-side/partial templates (`{{> name}}`) — partials have no Java-engine equivalent
  in the classpath set; the server renders one flat template.

## Engine parity rules

When adding a mustache construct:

1. Add it to the subset above in this README.
2. Extend `TemplateEngineTest` / `MustacheTemplateRenderingTest` / `ViewModelFactoryTest`
   / `DualEngineGoldenTest` (Java) and `report/src/test/javascript/mustache.dual-engine.test.cjs`
   (Node, vendored mustache.js) so both engines cover the new behavior.
3. Regenerate the golden `report/src/test/resources/golden/mustache/refactor-first-report.mustache.html`
   from the Java engine and confirm the Node dual-engine test still passes.

## Rendering constraints

- Interpolated strings are HTML-escaped by both engines by default; triple-brace values
  render raw. The escaping character map must stay identical (Java
  `& < > " '`; mustache.js default escapes `& < > " ' / \` =` — the Node test supplies a
  custom escape that matches the Java map).
- Standalone-tag line handling must not be relied on for structural HTML: prefer inline
  section content so whitespace handling cannot diverge between engines.
- Templates use CRLF line endings; both engines preserve them.

## Simple report (P3) — `<style>` and the GitHub step summary

`refactor-first-simple-report.mustache` is strictly HTML (no scripts — enforced by
`StrictHtmlComplianceTest`) and carries a single CSS-only `<style>` block providing the
sticky header, sticky `thead th`, and `:focus-visible` outlines. When the report is
appended to `$GITHUB_STEP_SUMMARY`, GitHub's sanitizer strips `<style>` (and scripts);
the page content degrades gracefully to readable, unstyled HTML — an accepted trade-off
(plan G8). The template also renders a minimal no-Git-repository message body so a
missing repository never fails the goal.
