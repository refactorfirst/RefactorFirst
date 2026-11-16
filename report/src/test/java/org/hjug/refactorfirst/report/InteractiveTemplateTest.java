package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T4.1: the interactive template renders the page-repo report skeleton — single
 * {@code <h1>}, skip link, {@code main#app}, one section per disharmony carrying a
 * {@code <canvas id="chart_…">} with {@code role="img"} and an aria-label, graph button
 * rows (2D/3D), popup container, footer — and carries zero Google Charts references
 * anywhere in the output (the feature-8 regression gate, kept forever).
 */
class InteractiveTemplateTest {

    private final TemplateEngine templateEngine = new TemplateEngine();

    private static final String TEMPLATE_RESOURCE = "templates/refactor-first-report.mustache";
    private static final Pattern GOOGLE_CHARTS = Pattern.compile("gstatic\\.com|google\\.charts");
    private static final Pattern H1_TAG = Pattern.compile("<h1\\b");

    @DisplayName("Interactive template renders the page-repo report skeleton")
    @Test
    void rendersInteractiveSkeleton() throws Exception {
        // Given: the fixture view model
        Map<String, Object> view = InteractiveTemplateFixture.viewModel();

        // When: the bundled template is rendered
        String html = templateEngine.render(TEMPLATE_RESOURCE, view);

        // Then: the skeleton carries the page-repo landmarks
        assertEquals(1, count(H1_TAG, html), "exactly one h1: " + html);
        assertTrue(html.contains("<main id=\"app\""), html);
        assertTrue(html.contains("rf-skip-link"), "skip link present");
        assertTrue(html.contains("<canvas id=\"chart_GOD\""), "chart canvas present: " + html);
        assertTrue(
                html.contains("role=\"img\"") && html.contains("aria-label=\"Bubble chart ranking God Classes"),
                "canvas carries role=img + aria-label: " + html);
        assertTrue(html.contains("data-popup-3d"), "graph button rows carry 3D buttons");
        assertTrue(html.contains("data-popup-2d"), "graph button rows carry 2D buttons");
        assertTrue(html.contains("data-popup-close"), "popup close controls present");
        assertTrue(html.contains("<footer>"), "footer present");
        assertTrue(html.contains("Last Published: 9/8/26, 7:34 PM"), "footer timestamp present");
    }

    @DisplayName("Interactive template embeds the rf-data JSON block")
    @Test
    void embedsRfDataBlock() throws Exception {
        // Given: the fixture view model carrying an rf-data payload
        Map<String, Object> view = InteractiveTemplateFixture.viewModel();

        // When: the bundled template is rendered
        String html = templateEngine.render(TEMPLATE_RESOURCE, view);

        // Then: the rf-data block embeds the payload for the page scripts
        assertTrue(html.contains("<script type=\"application/json\" id=\"rf-data\">"), html);
        assertTrue(html.contains("\"graphs\":{\"classGraph\":\"digraph {}\"}"), html);
    }

    @DisplayName("Interactive output contains zero Google Charts references")
    @Test
    void containsZeroGoogleChartsReferences() throws Exception {
        // Given: the fixture view model
        Map<String, Object> view = InteractiveTemplateFixture.viewModel();

        // When: the bundled template is rendered
        String html = templateEngine.render(TEMPLATE_RESOURCE, view);

        // Then: no gstatic.com / google.charts reference survives (feature-8 gate)
        Matcher matcher = GOOGLE_CHARTS.matcher(html);
        StringBuilder found = new StringBuilder();
        while (matcher.find()) {
            found.append(matcher.group()).append("; ");
        }
        assertEquals("", found.toString(), "Google Charts references must be gone");
        assertFalse(html.contains("loader.js"), "the Google Charts loader must be gone");
    }

    @DisplayName("Package class relationships render on separate lines and retain hyperlinks")
    @Test
    void rendersPackageClassRelationshipsOnSeparateLinesWithHyperlinks() throws Exception {
        // Given: a package relationship with four class relationships to break it
        Map<String, Object> view = InteractiveTemplateFixture.viewModel();
        List<Map<String, Object>> classRelationships = List.of(
                classRelationship("CycleRemovalComputer", "DirectedFeedbackVertexSetResult", 2),
                classRelationship("CycleRemovalComputer", "DirectedFeedbackVertexSetSolver", 2),
                classRelationship("CycleRemovalComputer", "EnhancedParameterComputer", 2),
                classRelationship("CycleRemovalComputer", "EnhancedParameterComputer$EnhancedParameters", 4));
        Map<String, Object> packageRelationship = new LinkedHashMap<>();
        packageRelationship.put("sourcePackage", "com.example.source");
        packageRelationship.put("targetPackage", "com.example.target");
        packageRelationship.put("sourceMarked", false);
        packageRelationship.put("targetMarked", false);
        packageRelationship.put("weight", 10);
        packageRelationship.put("priority", 1);
        packageRelationship.put("cycleCount", 1);
        packageRelationship.put("effortRank", 1);
        packageRelationship.put("classRelationshipsToBreakPackage", classRelationships);

        Map<String, Object> packageRelationshipsToRemove = new LinkedHashMap<>();
        packageRelationshipsToRemove.put("cycleCount", 1);
        packageRelationshipsToRemove.put("relationshipsToRemoveCount", 1);
        packageRelationshipsToRemove.put("hasRelationships", true);
        packageRelationshipsToRemove.put("relationships", List.of(packageRelationship));
        view.put("packageRelationshipsToRemove", packageRelationshipsToRemove);

        // When: the interactive report and its styles render
        String html = templateEngine.renderWithAssets(TEMPLATE_RESOURCE, view);

        // Then: each relationship has its own block and its source/target links survive
        assertTrue(
                Pattern.compile("\\.rf-break-relationship\\s*\\{\\s*display:\\s*block;")
                        .matcher(html)
                        .find(),
                "relationships are block-level");
        assertEquals(4, count(Pattern.compile("<span class=\"rf-break-relationship\">"), html));
        assertEquals(
                4,
                count(
                        Pattern.compile(
                                "<a href=\"https://github.com/test/test/blob/src/main/java/com/example/CycleRemovalComputer\\.java\""),
                        html));
        assertTrue(
                html.contains(
                        "href=\"https://github.com/test/test/blob/src/main/java/com/example/EnhancedParameterComputer%24EnhancedParameters.java\""),
                html);
        assertTrue(html.contains("EnhancedParameterComputer$EnhancedParameters"), html);
    }

    private Map<String, Object> classRelationship(String source, String target, int weight) {
        Map<String, Object> relationship = new LinkedHashMap<>();
        relationship.put("sourceClassPath", "src/main/java/com/example/" + source + ".java");
        relationship.put("targetClassPath", "src/main/java/com/example/" + target + ".java");
        relationship.put(
                "sourceClassUrl", "https://github.com/test/test/blob/src/main/java/com/example/" + source + ".java");
        relationship.put(
                "targetClassUrl",
                "https://github.com/test/test/blob/src/main/java/com/example/" + target.replace("$", "%24") + ".java");
        relationship.put("simpleSourceClassName", source);
        relationship.put("simpleTargetClassName", target);
        relationship.put("weight", weight);
        relationship.put("sourceMarked", false);
        relationship.put("targetMarked", false);
        return relationship;
    }

    private int count(Pattern pattern, String html) {
        int count = 0;
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
