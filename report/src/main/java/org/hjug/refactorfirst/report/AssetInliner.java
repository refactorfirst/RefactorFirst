package org.hjug.refactorfirst.report;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Inlines the classpath report assets (CSS/JS) into the rendered interactive
 * report page — plan D8/M2: the report is a single self-contained file that
 * opens via {@code file://}, so no asset is ever loaded via {@code src=}/
 * {@code href=} from the local disk.
 *
 * <p>The template carries {@code <!-- rf:inlined-css -->} and
 * {@code <!-- rf:inlined-js -->} markers; each is replaced by the ordered
 * asset blocks. The ordering is deterministic (T4.4): CSS
 * palette → tables → graphs → report; JS core → theme → hierarchy-dot →
 * tables → csv → copy → charts → graphs → main. Phases P6/P7 grow the
 * lists in those slots. Each block carries an {@code rf:asset:<name>} id
 * comment so the injection is idempotent and greppable.
 */
public class AssetInliner {

    /** Ordered classpath CSS assets inlined at the {@code rf:inlined-css} marker. */
    private static final List<String> CSS_ASSETS =
            List.of("assets/rf-palette.css", "assets/rf-tables.css", "assets/rf-graphs.css", "assets/rf-core.css");

    /** Ordered classpath JS assets inlined at the {@code rf:inlined-js} marker. */
    private static final List<String> JS_ASSETS = List.of(
            "assets/rf-core.js",
            "assets/rf-theme.js",
            "assets/rf-hierarchy-dot.js",
            "assets/rf-tables.js",
            "assets/rf-csv.js",
            "assets/rf-copy.js",
            "assets/rf-charts.js",
            "assets/rf-graphs-sigma.js",
            "assets/rf-graphs-3d.js",
            "assets/rf-graphs-vizdom.js",
            "assets/rf-main.js");

    private static final String CSS_MARKER = "<!-- rf:inlined-css -->";
    private static final String JS_MARKER = "<!-- rf:inlined-js -->";

    /** Inlines assets into a trusted template source, before report data is interpolated. */
    public String inline(String html) {
        String withCss = html.replace(CSS_MARKER, renderCssBlocks());
        String withJs = withCss.replace(JS_MARKER, renderJsBlocks());
        return withJs;
    }

    /** Builds the ordered CSS asset blocks (each opened with its id comment). */
    String renderCssBlocks() {
        StringBuilder blocks = new StringBuilder();
        for (String asset : CSS_ASSETS) {
            blocks.append("<!-- rf:asset:")
                    .append(asset)
                    .append(" -->\n")
                    .append("<style>\n")
                    .append(readAsset(asset))
                    .append("\n</style>\n");
        }
        return blocks.toString();
    }

    /** Builds the ordered JS asset blocks (each opened with its id comment). */
    String renderJsBlocks() {
        StringBuilder blocks = new StringBuilder();
        for (String asset : JS_ASSETS) {
            blocks.append("<!-- rf:asset:")
                    .append(asset)
                    .append(" -->\n")
                    .append("<script>\n")
                    .append(readAsset(asset))
                    .append("\n</script>\n");
        }
        return blocks.toString();
    }

    private String readAsset(String asset) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(asset)) {
            if (in == null) {
                throw new IllegalStateException("Report asset not found on classpath: " + asset);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read report asset: " + asset, e);
        }
    }
}
