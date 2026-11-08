package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.wilsonl.minifyhtml.Configuration;
import in.wilsonl.minifyhtml.MinifyHtml;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T3.2 (Red): the strict-HTML compliance gate for the simple report — no script, no javascript:
 * URIs, no inline event handlers, no iframes; parses via minify-html; UTF-8 charset declared.
 */
class StrictHtmlComplianceTest {

    private final TemplateEngine templateEngine = new TemplateEngine();

    private static final String TEMPLATE_RESOURCE = "templates/refactor-first-simple-report.mustache";
    private static final Pattern INLINE_EVENT_HANDLER = Pattern.compile("(?is)\\son[a-zA-Z]+\\s*=");

    @DisplayName("Rendered simple report is strictly HTML: no script, handlers, iframes or javascript: URIs")
    @Test
    void renderedSimpleReportIsStrictlyHtml() throws Exception {
        // Given: the simple report rendered from the view model
        String html = templateEngine.render(TEMPLATE_RESOURCE, strictView());

        // Then: the strict-HTML gate holds
        assertStrictlyHtml(html);

        // And: the markup parses via the minify-html parser without error and declares UTF-8
        String minified = minify(html);
        assertTrue(minified.length() > 0);
        assertTrue(
                html.toLowerCase().contains("charset=\"utf-8\"")
                        || html.toLowerCase().contains("charset=utf-8"),
                "strict simple report must declare a UTF-8 charset");
    }

    @DisplayName("Populated simple report (including Kotlin-shaped data) stays strictly HTML")
    @Test
    void populatedSimpleReportStaysStrictlyHtml() throws Exception {
        // Given: a fully populated view model whose data carries Kotlin-style identifiers
        String html = templateEngine.render(TEMPLATE_RESOURCE, populatedView());

        // Then: the strict-HTML gate holds on every section
        assertStrictlyHtml(html);
        assertTrue(html.contains("id=\"GOD\""), html);
        assertTrue(html.contains("Class Relationship Removal Priority"), html);
        assertTrue(html.contains("id=\"CYCLEMAP\""), html);
    }

    /** The strict-HTML gate: no scripts, handlers, iframes or javascript: URIs, and it parses. */
    private void assertStrictlyHtml(String html) {
        assertFalse(html.contains("<script"), "strict simple report must not contain <script>: " + html);
        assertFalse(html.contains("javascript:"), "strict simple report must not contain javascript: URIs");
        assertFalse(
                INLINE_EVENT_HANDLER.matcher(html).find(),
                "strict simple report must not contain inline event handler attributes");
        assertFalse(html.contains("<iframe"), "strict simple report must not contain <iframe>");
        assertDoesNotThrow(() -> MinifyHtml.minify(html, new Configuration.Builder().build()));
    }

    private String minify(String html) {
        Configuration config = new Configuration.Builder().build();
        return assertDoesNotThrow(() -> MinifyHtml.minify(html, config));
    }

    /** Minimal view model for the compliance fixtures. */
    private java.util.Map<String, Object> strictView() {
        return new java.util.LinkedHashMap<>();
    }

    /** Fully populated view model with Kotlin-shaped identifiers (anonymous classes, $ names). */
    private java.util.Map<String, Object> populatedView() {
        java.util.Map<String, Object> project = new java.util.LinkedHashMap<>();
        project.put("name", "KotlinProject");
        project.put("version", "1.0.0");
        project.put("repoUrl", "https://github.com/test/test/blob/");
        project.put("baseDir", "/test");
        project.put("scanTimestamp", "9/8/26, 7:34 PM");
        project.put("hasAnyDisharmony", true);
        project.put("analysisFailed", false);

        java.util.Map<String, Object> relationship = new java.util.LinkedHashMap<>();
        relationship.put("sourceClass", "com.example.Outer$1");
        relationship.put("targetClass", "com.example.B");
        relationship.put("sourceClassPath", "src/main/java/com/example/Outer.java");
        relationship.put("targetClassPath", "src/main/java/com/example/B.java");
        relationship.put("simpleSourceClassName", "Outer$1");
        relationship.put("simpleTargetClassName", "B");
        relationship.put("sourceMarked", true);
        relationship.put("targetMarked", false);
        relationship.put("weight", 5);
        relationship.put("priority", 1);
        relationship.put("cycleCount", 3);
        relationship.put("effortRank", 2);
        relationship.put("alsoRemovesPackageRelationship", false);
        relationship.put("packageCycleCount", 0);

        java.util.Map<String, Object> classRelationshipsToRemove = new java.util.LinkedHashMap<>();
        classRelationshipsToRemove.put("cycleCount", 1);
        classRelationshipsToRemove.put("relationshipsToRemoveCount", 1);
        classRelationshipsToRemove.put("hasRelationships", true);
        classRelationshipsToRemove.put("relationships", java.util.List.of(relationship));

        java.util.Map<String, Object> cell = new java.util.LinkedHashMap<>();
        // Cell contents arrive pre-escaped from the DTO layer
        cell.put("content", "Shape$<anonymous>");
        cell.put("path", "src/main/java/Shape.kt");
        cell.put("align", "left");
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("cells", java.util.List.of(cell));
        java.util.Map<String, Object> table = new java.util.LinkedHashMap<>();
        table.put("headers", java.util.List.of("Class", "Priority"));
        table.put("rows", java.util.List.of(row));

        java.util.Map<String, Object> section = new java.util.LinkedHashMap<>();
        section.put("type", "God Class");
        section.put("anchorId", "GOD");
        section.put("title", "God Classes");
        section.put("methodLevel", false);
        section.put("problem", "God Classes take on too much responsibility,");
        section.put(
                "solution",
                "Extract related islands of functionality into separate classes.  "
                        + "Leave God classes that don't change often alone.");
        section.put("maxPriority", 5);
        section.put("chart", new java.util.LinkedHashMap<>());
        section.put("table", table);

        java.util.Map<String, Object> breakdownRow = new java.util.LinkedHashMap<>();
        breakdownRow.put("className", "Outer$1");
        breakdownRow.put("classPath", "src/main/java/com/example/Outer.java");
        breakdownRow.put("marked", true);
        breakdownRow.put("edgesHtml", "Outer$1 &#8594; B : 5<br/>\n");
        java.util.Map<String, Object> largestCycle = new java.util.LinkedHashMap<>();
        largestCycle.put("hasCycleMap", true);
        largestCycle.put("cycleName", "Outer$1");
        largestCycle.put("cycleIdentifier", "cycle_1");
        largestCycle.put("classCount", 2);
        largestCycle.put("relationshipCount", 1);
        largestCycle.put("dotThresholdExceeded", false);
        largestCycle.put("breakdown", java.util.List.of(breakdownRow));
        java.util.Map<String, Object> classCycles = new java.util.LinkedHashMap<>();
        classCycles.put("hasCycles", true);
        classCycles.put("summary", new java.util.ArrayList<>());
        classCycles.put("largestCycle", largestCycle);

        java.util.Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("project", project);
        view.put("classMap", new java.util.LinkedHashMap<>());
        view.put("classRelationshipsToRemove", classRelationshipsToRemove);
        view.put("packageMap", new java.util.LinkedHashMap<>());
        view.put("packageRelationshipsToRemove", new java.util.LinkedHashMap<>());
        view.put("hasDisharmonies", true);
        view.put("disharmonies", java.util.List.of(section));
        view.put("classCycles", classCycles);
        return view;
    }
}
