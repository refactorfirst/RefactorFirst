package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T6.5: the enhanced table controls' WCAG contract — the template carries the toolbar
 * skeleton (match-count live region, export button, sortable header buttons) and the JS
 * carries the injected search input contract (visually-hidden label, aria-describedby →
 * the role="status" match-count region, × clear button toggling {@code hidden}),
 * pagination nav labels, copy-cell affordances and ≥ 24 px targets.
 */
class TableControlsWcagTest {

    private final TemplateEngine templateEngine = new TemplateEngine();

    private static final String TEMPLATE_RESOURCE = "templates/refactor-first-report.mustache";

    private String rendered() throws Exception {
        return templateEngine.render(TEMPLATE_RESOURCE, InteractiveTemplateFixture.viewModel());
    }

    private String asset(String name) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("asset not found on classpath: " + name);
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @DisplayName("Every table's match-count region is a status live region")
    @Test
    void matchCountRegionsAreStatusLiveRegions() throws Exception {
        // Given: the rendered template
        String html = rendered();

        // Then: each match-count span carries role=status (the live region the
        // search input describes with aria-describedby)
        assertTrue(html.contains("<span class=\"rf-table-match\" role=\"status\" data-rf-match="), html);
    }

    @DisplayName("Sortable headers are real buttons with valid aria-sort values")
    @Test
    void sortableHeadersAreButtonsWithValidAriaSort() throws Exception {
        // Given: the rendered template
        String html = rendered();

        // Then: every sort header is a real button carrying a sort key
        assertTrue(html.contains("<button type=\"button\" class=\"rf-sort-btn\" data-rf-sort="), html);
        assertTrue(html.contains("data-sort-key=\"priority\""), html);

        // And: every baked aria-sort value is valid
        Matcher matcher = Pattern.compile("aria-sort=\"([^\"]+)\"").matcher(html);
        StringBuilder invalid = new StringBuilder();
        while (matcher.find()) {
            String value = matcher.group(1);
            if (!value.equals("ascending")
                    && !value.equals("descending")
                    && !value.equals("none")
                    && !value.equals("other")) {
                invalid.append(value).append("; ");
            }
        }
        assertEquals("", invalid.toString(), "aria-sort values must be valid");

        // And: rf-tables.js maintains the same cycle (ascending → descending → none)
        String tables = asset("assets/rf-tables.js");
        assertTrue(
                tables.contains("'descending'") && tables.contains("'ascending'") && tables.contains("'none'"),
                "the sort cycle must produce valid aria-sort values");
    }

    @DisplayName("Export buttons and pagination navs carry their accessible names")
    @Test
    void exportAndPaginationCarryAccessibleNames() throws Exception {
        // Given: the rendered template
        String html = rendered();

        // Then: export buttons announce their table
        // WCAG 2.5.3 Label in Name: the accessible name contains the visible "Export CSV" label.
        assertTrue(html.contains("aria-label=\"Export CSV: the class relationships table as CSV\""), html);
        assertTrue(
                html.contains("Export CSV: the God Classes findings table as CSV"),
                "fixture disharmony tables announce their title");

        // And: pagination navs announce their pages
        assertTrue(html.contains("aria-label=\"Pages of the class relationships table\""), html);
        assertTrue(html.contains("Pages of the God Classes findings table"), html);
    }

    @DisplayName("The injected search input contract matches the WCAG wiring")
    @Test
    void injectedSearchInputContract() throws Exception {
        // Given: the table enhancer source
        String tables = asset("assets/rf-tables.js");

        // Then: the search input is described by the match-count live region,
        // carries an accessible name, and the clear button toggles hidden
        assertTrue(tables.contains("input.setAttribute('aria-describedby', matchId)"), tables);
        assertTrue(tables.contains("input.setAttribute('aria-label', 'Filter the ' + caption + ' table')"), tables);
        assertTrue(tables.contains("clear.hidden = input.value === ''"), tables);
        assertTrue(
                tables.contains("clear.setAttribute('aria-label', 'Clear the ' + caption + ' table filter')"), tables);
        assertTrue(tables.contains("'rf-search-label rf-visually-hidden'"), "the label is visually hidden");

        // And: focus returns to the rebuilt cell after a re-render
        // (the pendingFocusRef pattern).
        assertTrue(tables.contains("pendingFocusIndex"), tables);
    }

    @DisplayName("Copy cells are keyboard-operable with a visible focus ring")
    @Test
    void copyCellsAreKeyboardOperable() throws Exception {
        // Given: the enhancer source and the table stylesheet
        String tables = asset("assets/rf-tables.js");
        String css = asset("assets/rf-tables.css");

        // Then: every tbody cell becomes a focusable copy target with a hint
        assertTrue(tables.contains("cell.setAttribute('tabindex', '0')"), tables);
        assertTrue(tables.contains("cell.setAttribute('data-rf-copy', '')"), tables);
        assertTrue(tables.contains("cell.setAttribute('title', 'Click to copy this cell')"), tables);
        assertTrue(tables.contains("event.key === 'Enter' || event.key === ' '"), "Enter/Space copy");
        assertTrue(tables.contains("event.target === cell &&"), "descendant links retain keyboard activation");

        // And: the copy cell focus ring stays inside the table frame
        assertTrue(
                Pattern.compile("td\\[data-rf-copy\\]:focus-visible")
                        .matcher(css)
                        .find(),
                "copy cells carry a :focus-visible ring");

        // And: the toast region announces politely
        String copy = asset("assets/rf-copy.js");
        assertTrue(copy.contains("'status'"), "toast region role=status");
        assertTrue(copy.contains("'polite'"), "toast region aria-live=polite");
    }

    @DisplayName("Controls meet the 24px minimum target size")
    @Test
    void controlsMeetMinimumTargetSize() throws Exception {
        // Given: the table stylesheet
        String css = asset("assets/rf-tables.css");

        // Then: the export and pagination buttons clear 24px in both dimensions
        assertTrue(
                Pattern.compile("\\.rf-export-btn\\s*\\{[^}]*padding:\\s*0\\.3rem\\s+0\\.75rem")
                        .matcher(css)
                        .find(),
                "export button target >= 24px");
        assertTrue(
                Pattern.compile("\\.rf-page-btn\\s*\\{[^}]*padding:\\s*0\\.35rem\\s+0\\.75rem")
                        .matcher(css)
                        .find(),
                "page button target >= 24px");
    }
}
