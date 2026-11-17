package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T5.4: the theme toggle is an accessible radio group — {@code role="radiogroup"} labelled
 * "Color theme", three radios sharing {@code name="rf-theme"} with accessible text, icon-pill
 * targets at least 24×24 px (WCAG 2.5.8), rendered by the interactive template.
 */
class ThemeWcagTest {

    private final TemplateEngine templateEngine = new TemplateEngine();

    private static final String TEMPLATE_RESOURCE = "templates/refactor-first-report.mustache";

    @DisplayName("Theme toggle is a labelled radiogroup with three named radios")
    @Test
    void themeToggleIsAccessibleRadioGroup() throws Exception {
        // Given: the rendered interactive template
        String html = templateEngine.render(TEMPLATE_RESOURCE, InteractiveTemplateFixture.viewModel());

        // Then: the radiogroup carries its accessible name
        assertTrue(
                Pattern.compile("role=\"radiogroup\"[^>]*aria-label=\"Color theme\"")
                        .matcher(html)
                        .find(),
                "theme toggle must be a radiogroup labelled \"Color theme\": " + html);

        // And: three radios share the group name and carry the mode values
        assertEquals(3, count(html, "name=\"rf-theme\""), "three theme radios expected");
        assertTrue(html.contains("id=\"rf-theme-light\""), html);
        assertTrue(html.contains("id=\"rf-theme-dark\""), html);
        assertTrue(html.contains("id=\"rf-theme-system\""), html);

        // And: the system radio is the default
        assertTrue(
                Pattern.compile("id=\"rf-theme-system\"[^>]*checked")
                        .matcher(html)
                        .find(),
                "system must be the defaultChecked mode");

        // And: every radio carries accessible text (the mode label)
        assertTrue(html.contains("aria-label=\"Light theme\""), html);
        assertTrue(html.contains("aria-label=\"Dark theme\""), html);
        assertTrue(html.contains("aria-label=\"System preference\""), html);
    }

    @DisplayName("Theme pills meet the 24px minimum target size")
    @Test
    void themePillsMeetMinimumTargetSize() throws Exception {
        // Given: the report's stylesheets declare the pill target size
        String paletteCss = readAsset("assets/rf-palette.css");

        // Then: the icon-pill label is at least 24px in both dimensions (1.75rem = 28px)
        assertTrue(
                Pattern.compile("\\.theme-option\\s*\\{[^}]*width\\s*:\\s*1\\.75rem")
                        .matcher(paletteCss)
                        .find(),
                "theme pill must be >= 24px wide (SC 2.5.8)");
        assertTrue(
                Pattern.compile("\\.theme-option\\s*\\{[^}]*height\\s*:\\s*1\\.75rem")
                        .matcher(paletteCss)
                        .find(),
                "theme pill must be >= 24px tall (SC 2.5.8)");
    }

    @DisplayName("Selected theme pill uses the visible accent and keeps >= 3:1 in dark")
    @Test
    void selectedThemePillUsesVisibleAccentWithContrast() throws Exception {
        // Given: the report's stylesheets declare the selected pill's fill
        String paletteCss = readAsset("assets/rf-palette.css");

        // Then: the selected pill is a visible selected-state indicator, so it
        // must use the --brand-accent visible accent (>= 3:1 against the dark page
        // and surface backgrounds, SC 1.4.11 / 2.4.11) — not --brand-color, whose
        // white icon passes AA text contrast but whose fill sits below 3:1 against
        // the dark --surface-color.
        assertTrue(
                Pattern.compile("\\.theme-option-input:checked\\s*\\{[^{}]*background\\s*:\\s*var\\(--brand-accent\\)")
                        .matcher(paletteCss)
                        .find(),
                "the selected theme pill must use the --brand-accent visible accent: " + paletteCss);
    }

    private String readAsset(String asset) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(asset)) {
            if (in == null) {
                throw new IllegalStateException("asset not found on classpath: " + asset);
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
}
