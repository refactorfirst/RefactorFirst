package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.wilsonl.minifyhtml.Configuration;
import in.wilsonl.minifyhtml.MinifyHtml;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T4.4: the asset inliner embeds every classpath asset into the rendered page in a
 * deterministic order, never leaves a local file loaded via {@code src=}/{@code href=},
 * marks each block with an idempotent id comment, and the MinifyHtml
 * ({@code setMinifyJs(true)}) round trip leaves the scripts runnable.
 */
class AssetInlinerTest {

    private final TemplateEngine templateEngine = new TemplateEngine();
    private final AssetInliner assetInliner = new AssetInliner();

    private static final String TEMPLATE_RESOURCE = "templates/refactor-first-report.mustache";
    private static final Pattern ASSET_COMMENT = Pattern.compile("<!-- rf:asset:([^ ]+) -->");

    private String renderedAndInlined() throws Exception {
        String rendered = templateEngine.render(TEMPLATE_RESOURCE, InteractiveTemplateFixture.viewModel());
        return assetInliner.inline(rendered);
    }

    @DisplayName("Assets inline in the deterministic order with idempotent id comments")
    @Test
    void assetsInlineInDeterministicOrder() throws Exception {
        // Given: the rendered interactive page
        String html = renderedAndInlined();

        // Then: the markers are gone and every asset appears in order
        assertFalse(html.contains("rf:inlined-css"));
        assertFalse(html.contains("rf:inlined-js"));

        Matcher matcher = ASSET_COMMENT.matcher(html);
        StringBuilder order = new StringBuilder();
        while (matcher.find()) {
            order.append(matcher.group(1)).append('\n');
        }
        assertEquals(
                """
                assets/rf-palette.css
                assets/rf-tables.css
                assets/rf-graphs.css
                assets/rf-core.css
                assets/rf-core.js
                assets/rf-theme.js
                assets/rf-hierarchy-dot.js
                assets/rf-tables.js
                assets/rf-csv.js
                assets/rf-copy.js
                assets/rf-charts.js
                assets/rf-graphs-sigma.js
                assets/rf-graphs-3d.js
                assets/rf-graphs-vizdom.js
                assets/rf-main.js
                """,
                order.toString(),
                "asset id comments must appear in the deterministic order");
    }

    @DisplayName("No report asset is loaded via src= or href= from local files")
    @Test
    void noLocalAssetIsLoadedViaSrcOrHref() throws Exception {
        // Given: the inlined page
        String html = renderedAndInlined();

        // Then: no local asset reference remains
        assertFalse(html.contains("src=\"assets/"), html);
        assertFalse(html.contains("href=\"assets/"), html);
        assertTrue(html.contains("window.RF"), "the core script must be inlined");
    }

    @DisplayName("MinifyHtml round trip keeps the inlined scripts runnable")
    @Test
    void minifyRoundTripKeepsScriptsRunnable() throws Exception {
        // Given: the inlined page and the production minifier configuration
        String html = renderedAndInlined();
        Configuration config = new Configuration.Builder()
                .setKeepHtmlAndHeadOpeningTags(true)
                .setKeepComments(false)
                .setMinifyJs(true)
                .setMinifyCss(true)
                .build();

        // When: the page is minified
        String minified = MinifyHtml.minify(html, config);

        // Then: the RF namespace and the boot call survive the minifier, no
        // marker survives, and the rf-data JSON block is not mangled
        assertTrue(minified.contains("window.RF"), minified);
        assertTrue(minified.contains("rf-data"), "the rf-data block must survive minification");
        assertFalse(minified.contains("rf:inlined-"), "markers are consumed");

        // And: every inlined JS asset body still parses as JavaScript — the
        // authoritative parse check runs in Node (assets-inline.test.cjs, run
        // by the js-tests profile); here we pin that the script blocks exist.
        Pattern scriptBody = Pattern.compile("<script>(.*?)</script>", Pattern.DOTALL);
        Matcher scripts = scriptBody.matcher(minified);
        int inlineScripts = 0;
        while (scripts.find()) {
            inlineScripts++;
        }
        assertTrue(inlineScripts >= 11, "expected the eleven inlined JS assets, found " + inlineScripts);
    }
}
