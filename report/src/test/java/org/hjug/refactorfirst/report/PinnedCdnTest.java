package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T4.5: the generated interactive report loads every CDN widget from the exact pinned
 * URL and never an unpinned specifier — silent behavior drift and proxy/CORS hazards
 * are pinned away (permanent gate).
 */
class PinnedCdnTest {

    private final TemplateEngine templateEngine = new TemplateEngine();

    private static final String TEMPLATE_RESOURCE = "templates/refactor-first-report.mustache";

    private static final String[] PINNED_URLS = {
        "https://unpkg.com/mvp.css@1.15",
        "https://buttons.github.io/buttons.js",
        "https://cdn.jsdelivr.net/npm/svg-pan-zoom@3.6.1/dist/svg-pan-zoom.min.js",
        "https://cdnjs.cloudflare.com/ajax/libs/sigma.js/2.4.0/sigma.min.js",
        "https://cdnjs.cloudflare.com/ajax/libs/graphology/0.25.4/graphology.umd.min.js",
        "https://cdn.jsdelivr.net/npm/graphlib-dot@0.6.4/dist/graphlib-dot.min.js",
        "https://cdn.jsdelivr.net/npm/chart.js@4.4.7/dist/chart.umd.min.js",
        "https://cdn.jsdelivr.net/npm/@vizdom/vizdom-ts-web@0.1.19/vizdom_ts.min.js",
        "https://esm.sh/3d-force-graph@1.80.1?deps=three@0.186.1",
        "https://esm.sh/three-spritetext@1.10.0?deps=three@0.186.1",
    };

    private String rendered() throws Exception {
        return templateEngine.render(TEMPLATE_RESOURCE, InteractiveTemplateFixture.viewModel());
    }

    @DisplayName("Generated report carries every pinned CDN URL")
    @Test
    void carriesEveryPinnedCdnUrl() throws Exception {
        // Given: the rendered interactive report
        String html = rendered();

        // Then: every pinned URL appears
        for (String url : PINNED_URLS) {
            assertTrue(html.contains(url), "missing pinned URL: " + url + " in " + html);
        }
    }

    @DisplayName("No unpinned CDN specifier survives")
    @Test
    void carriesNoUnpinnedSpecifier() throws Exception {
        // Given: the rendered interactive report
        String html = rendered();

        // Then: the historical unpinned specifiers are gone
        assertFalse(html.contains("npm/3d-force-graph\""), "unpinned 3d-force-graph specifier");
        assertFalse(html.contains("unpkg.com/mvp.css\""), "unpinned mvp.css specifier");
        assertFalse(html.contains("npm/chart.js\""), "unpinned chart.js specifier");
    }

    @DisplayName("Widget failures surface a visible, polite live notice")
    @Test
    void widgetFailuresSurfaceVisibleNotice() throws Exception {
        // Given: the rendered report and the core asset
        String html = rendered();
        String core = readAsset();

        // Then: the 3D module bridge announces failures to the notice region
        assertTrue(
                html.contains("window.RF?.announceWidgetFailure?.('3d-force-graph')"),
                "the 3D bridge must announce its failure");
        assertTrue(
                html.contains("window.RF?.announceWidgetFailure?.('three-spritetext')"),
                "the sprite-text bridge must announce its failure");

        // And: the notice region is a polite status live region
        assertTrue(core.contains("'rf-widget-notice'"), core);
        assertTrue(core.contains("setAttribute('role', 'status')"), core);
        assertTrue(core.contains("setAttribute('aria-live', 'polite')"), core);
    }

    private String readAsset() throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream("assets/rf-core.js")) {
            if (in == null) {
                throw new IllegalStateException("rf-core.js not found on classpath");
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
