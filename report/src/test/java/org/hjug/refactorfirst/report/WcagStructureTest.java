package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T8.1: the WCAG 2.2 AA structure contract, continuously tested across BOTH templates —
 * single {@code <h1>}, no skipped heading levels, captioned tables with scoped headers,
 * {@code lang} on {@code <html>}, unique landmarks, chart canvases carrying
 * {@code role="img"} + aria-label + a text alternative, descriptive graph-button names,
 * the popup close label, and the popup focus-management contract in rf-core.js.
 */
class WcagStructureTest {

    private final TemplateEngine templateEngine = new TemplateEngine();

    private static final String INTERACTIVE = "templates/refactor-first-report.mustache";
    private static final String SIMPLE = "templates/refactor-first-simple-report.mustache";
    private static final Pattern HEADING_TAG = Pattern.compile("<h([1-6])\\b");

    private String render(String template) throws Exception {
        if (template.equals(INTERACTIVE)) {
            return templateEngine.render(template, InteractiveTemplateFixture.viewModel());
        }
        return new TemplateEngine().render(template, SimpleReportWcagView.view());
    }

    @Test
    void skipTargetFollowsNavigationAndLayoutRespectsHiddenControls() throws Exception {
        // Given / When: the rendered report and its styles.
        String html = templateEngine.render(INTERACTIVE, InteractiveTemplateFixture.viewModel());
        String core = new String(
                getClass().getResourceAsStream("/assets/rf-core.css").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        String tables = new String(
                getClass().getResourceAsStream("/assets/rf-tables.css").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        // Then: skip focus bypasses navigation, keyboard focus reveals submenus,
        // and full-width padding and hidden pagination retain their contracts.
        assertTrue(html.indexOf("id=\"report-content\"") > html.indexOf("</nav>"));
        assertTrue(html.contains("href=\"#report-content\""));
        assertTrue(html.contains("id=\"report-content\" tabindex=\"-1\""));
        assertTrue(core.contains("box-sizing: border-box"));
        assertTrue(core.contains("nav ul li:focus-within ul"));
        assertTrue(tables.contains(".rf-table-pagination:not([hidden])"));
        assertTrue(
                Pattern.compile("\\.rf-table-pagination\\[hidden\\]\\s*\\{[^}]*display:\\s*none")
                        .matcher(tables)
                        .find(),
                "hidden pagination must override the base nav display rule");
    }

    @DisplayName("Both templates: single h1, no skipped heading levels")
    @Test
    void headingStructureHolds() throws Exception {
        for (String template : List.of(INTERACTIVE, SIMPLE)) {
            String html = render(template);
            Matcher headings = HEADING_TAG.matcher(html);
            List<Integer> levels = new ArrayList<>();
            while (headings.find()) {
                levels.add(Integer.parseInt(headings.group(1)));
            }
            assertEquals(1, levels.stream().filter(level -> level == 1).count(), template + ": exactly one h1");
            for (int i = 1; i < levels.size(); i++) {
                assertTrue(
                        levels.get(i) <= levels.get(i - 1) + 1,
                        template + ": heading jump h" + levels.get(i - 1) + " → h" + levels.get(i));
            }
        }
    }

    @DisplayName("Both templates: lang on html, one main landmark, named navs")
    @Test
    void landmarksAndLanguageHold() throws Exception {
        for (String template : List.of(INTERACTIVE, SIMPLE)) {
            String html = render(template);
            assertTrue(Pattern.compile("<html[^>]*lang=\"en\"").matcher(html).find(), template + ": lang=en");
            assertEquals(1, count(html, "<main"), template + ": one main landmark");
            // Multiple nav landmarks are valid when each carries an accessible
            // name (the report sections nav + per-table pagination navs).
            assertTrue(count(html, "<nav") >= 1, template + ": nav landmarks render");
            assertEquals(0, countUnlabeledNavs(html), template + ": every nav is labelled");
        }
    }

    private int countUnlabeledNavs(String html) {
        Matcher navs = Pattern.compile("<nav\\b[^>]*>").matcher(html);
        int unlabeled = 0;
        while (navs.find()) {
            if (!navs.group().contains("aria-label")) {
                unlabeled++;
            }
        }
        return unlabeled;
    }

    @DisplayName("Interactive template: canvases carry role=img, aria-label and a text alternative")
    @Test
    void chartCanvasesCarryTextAlternatives() throws Exception {
        // Given: the rendered interactive template
        String html = render(INTERACTIVE);

        // Then: the canvas carries role=img + the chart title aria-label
        assertTrue(
                Pattern.compile("<canvas id=\"chart_GOD\" role=\"img\"\\s*aria-label=\"[^\"]+\"")
                        .matcher(html)
                        .find(),
                "the canvas must carry role=img + aria-label: " + html);

        // And: the canvas element carries its own fallback text (1.1.1 alternative)
        assertTrue(
                Pattern.compile("aria-label=\"[^\"]+\">[^<]+</canvas>")
                        .matcher(html)
                        .find(),
                "the canvas fallback text describes the chart");
    }

    @DisplayName("Interactive template: graph buttons and popup closes have descriptive names")
    @Test
    void graphControlsHaveDescriptiveNames() throws Exception {
        // Given: the rendered interactive template
        String html = render(INTERACTIVE);

        // Then: the 2D/3D popup buttons announce which map they open
        assertTrue(html.contains("aria-label=\"Show classGraph 2D Popup (2D class map)\""), html);
        assertTrue(html.contains("aria-label=\"Show classGraph 3D Popup (3D class map)\""), html);
        assertTrue(html.contains("3D cycle map for"), html);

        // And: each rendered popup's close control is labelled
        int popups = count(html, "<div class=\"popup\"");
        assertTrue(popups >= 2, html);
        assertEquals(popups, count(html, "aria-label=\"Close graph popup\""), html);
    }

    @DisplayName("Popup focus management: focus in on open, trap Tab, restore on close")
    @Test
    void popupFocusManagementContract() throws Exception {
        // Given: the core asset
        String core = asset("assets/rf-core.js");

        // Then: focus moves into the popup on open (the close control)
        assertTrue(core.contains("const close = popup.querySelector('.close-btn');"), core);
        assertTrue(core.contains("close.focus()"), core);

        // And: Tab is trapped between the popup's first and last focusable elements
        assertTrue(core.contains("function bindFocusTrap(popup)"), core);
        assertTrue(core.contains("event.key !== 'Tab'"), core);
        assertTrue(core.contains("last.focus()") && core.contains("first.focus()"), core);

        // And: close restores focus to the trigger element
        assertTrue(core.contains("popupTriggerElement.focus()"), core);
    }

    @DisplayName("Both templates: every table is captioned with scoped headers")
    @Test
    void tableContractsHold() throws Exception {
        for (String template : List.of(INTERACTIVE, SIMPLE)) {
            String html = render(template);
            int tables = count(html, "<table");
            assertTrue(tables > 0, template + ": tables render");
            assertEquals(tables, count(html, "<caption"), template + ": every table has a caption");
            assertEquals(0, countUnscopedColumnHeaders(html), template + ": every th is scoped");
        }
    }

    private int countUnscopedColumnHeaders(String html) {
        Matcher headers = Pattern.compile("<th\\b[^>]*>").matcher(html);
        int unscoped = 0;
        while (headers.find()) {
            if (!headers.group().contains("scope=")) {
                unscoped++;
            }
        }
        return unscoped;
    }

    private String asset(String name) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("asset not found: " + name);
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private int count(String html, String needle) {
        int count = 0;
        Matcher matcher = Pattern.compile(Pattern.quote(needle)).matcher(html);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /** Minimal view model for the simple template (mirrors SimpleReportWcagTest's fixture). */
    private static final class SimpleReportWcagView {

        static Map<String, Object> view() {
            try {
                var test = new SimpleReportWcagTest();
                var method = SimpleReportWcagTest.class.getDeclaredMethod("view");
                method.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Object> view = (Map<String, Object>) method.invoke(test);
                return view;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private SimpleReportWcagView() {}
    }
}
