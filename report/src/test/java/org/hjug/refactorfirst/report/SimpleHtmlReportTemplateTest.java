package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T3.1 (Red): renders the simple-report mustache template from the view model and asserts
 * section-by-section parity with the current {@link SimpleHtmlReport} string output — nav menu,
 * relationship tables, disharmony sections, cycle summary, largest-cycle breakdown, footer
 * timestamp — before the template lands (red: the simple-report template does not exist yet).
 */
class SimpleHtmlReportTemplateTest {

    private final TemplateEngine templateEngine = new TemplateEngine();

    private static final String TEMPLATE_RESOURCE = "templates/refactor-first-simple-report.mustache";

    @DisplayName("Simple report template renders the disharmony section title, anchor and guidance")
    @Test
    void rendersDisharmonySectionFromViewModel() throws Exception {
        // Given: a God Class disharmony section in the view model
        Map<String, Object> section = disharmonySection();

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("project", project());
        view.put("classMap", classMap());
        view.put("classRelationshipsToRemove", classRelationshipsToRemove());
        view.put("packageMap", packageMap());
        view.put("packageRelationshipsToRemove", packageRelationshipsToRemove());
        view.put("hasDisharmonies", true);
        view.put("disharmonies", List.of(section));
        view.put("classCycles", classCycles());

        // When: the simple-report template renders the section
        String html = templateEngine.render(TEMPLATE_RESOURCE, view);

        // Then: the section carries its anchor, title, problem and solution
        assertTrue(html.contains("God Classes"), html);
        assertTrue(html.contains("id=\"GOD\""), html);
        assertTrue(html.contains("God Classes take on too much responsibility"), html);
        assertTrue(html.contains("Extract related islands of functionality"), html);
    }

    @DisplayName("Simple report template renders the disharmony table headers and rows")
    @Test
    void rendersDisharmonyTableFromViewModel() throws Exception {
        // Given: a God Class disharmony section whose table ranks classes by priority
        Map<String, Object> section = disharmonySection();

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("project", project());
        view.put("classMap", classMap());
        view.put("classRelationshipsToRemove", classRelationshipsToRemove());
        view.put("packageMap", packageMap());
        view.put("packageRelationshipsToRemove", packageRelationshipsToRemove());
        view.put("hasDisharmonies", true);
        view.put("disharmonies", List.of(section));
        view.put("classCycles", classCycles());

        // When: the simple-report template renders the section table
        String html = templateEngine.render(TEMPLATE_RESOURCE, view);

        // Then: the table carries headers and ranked rows
        assertTrue(html.contains("<table"), html);
        assertTrue(html.contains("<th scope=\"col\">Class</th>"), html);
        assertTrue(html.contains("<th scope=\"col\">Priority</th>"), html);
        assertTrue(html.contains("TestClass.java"), html);
    }

    @DisplayName("Simple report template renders navigation menu anchors from the view model")
    @Test
    void rendersNavMenuAnchorsFromViewModel() throws Exception {
        // Given: a view model whose disharmonies and cycles produce menu entries
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("project", project());
        view.put("classMap", classMap());
        view.put("classRelationshipsToRemove", classRelationshipsToRemove());
        view.put("packageMap", packageMap());
        view.put("packageRelationshipsToRemove", packageRelationshipsToRemove());
        view.put("hasDisharmonies", true);
        view.put("disharmonies", List.of(disharmonySection()));
        view.put("classCycles", classCycles());

        // When: the simple-report template renders the navigation
        String html = templateEngine.render(TEMPLATE_RESOURCE, view);

        // Then: the nav carries the section anchors
        assertTrue(html.contains("<nav"), html);
        assertTrue(html.contains("id=\"GOD\""), html);
    }

    @DisplayName("Simple report template renders the class relationship remediation table from the view model")
    @Test
    void rendersRelationshipRemediationTableFromViewModel() throws Exception {
        // Given: a view model with one class relationship to remove
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("project", project());
        view.put("classMap", classMap());
        view.put("classRelationshipsToRemove", classRelationshipsToRemove());
        view.put("packageMap", packageMap());
        view.put("packageRelationshipsToRemove", packageRelationshipsToRemove());
        view.put("hasDisharmonies", true);
        view.put("disharmonies", List.<Map<String, Object>>of());
        view.put("classCycles", classCycles());

        // When: the simple-report template renders the relationship table
        String html = templateEngine.render(TEMPLATE_RESOURCE, view);

        // Then: the remediation table names source and target classes
        assertTrue(html.contains("Class Relationship Removal Priority"), html);
        assertTrue(html.contains("com.example.A"), html);
    }

    @DisplayName("Simple report omits class links when source paths are empty")
    @Test
    void omitsClassLinksWhenSourcePathsAreEmpty() throws Exception {
        // Given: class relationships and a cycle-break class with names but no source paths
        Map<String, Object> classRow = new LinkedHashMap<>();
        classRow.put("sourceClass", "com.example.A");
        classRow.put("targetClass", "com.example.B");
        classRow.put("sourceClassPath", "");
        classRow.put("targetClassPath", null);
        classRow.put("weight", 1);
        classRow.put("priority", 1);
        classRow.put("cycleCount", 1);
        classRow.put("effortRank", 1);
        classRow.put("sourceMarked", false);
        classRow.put("targetMarked", false);

        Map<String, Object> classRelationships = new LinkedHashMap<>();
        classRelationships.put("cycleCount", 1);
        classRelationships.put("relationshipsToRemoveCount", 1);
        classRelationships.put("hasRelationships", true);
        classRelationships.put("relationships", List.of(classRow));

        Map<String, Object> breakdownRow = new LinkedHashMap<>();
        breakdownRow.put("className", "NoPathClass");
        breakdownRow.put("classPath", "");
        breakdownRow.put("marked", false);
        breakdownRow.put("edgesHtml", "");
        Map<String, Object> largestCycle = new LinkedHashMap<>();
        largestCycle.put("hasCycleMap", true);
        largestCycle.put("cycleName", "A -> B -> A");
        largestCycle.put("classCount", 1);
        largestCycle.put("relationshipCount", 0);
        largestCycle.put("breakdown", List.of(breakdownRow));
        Map<String, Object> classCycles = new LinkedHashMap<>();
        classCycles.put("hasCycles", true);
        classCycles.put("summary", List.of());
        classCycles.put("largestCycle", largestCycle);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("project", project());
        view.put("classMap", classMap());
        view.put("classRelationshipsToRemove", classRelationships);
        view.put("packageMap", packageMap());
        view.put("packageRelationshipsToRemove", packageRelationshipsToRemove());
        view.put("hasDisharmonies", true);
        view.put("disharmonies", List.<Map<String, Object>>of());
        view.put("classCycles", classCycles);

        // When: the simple-report template renders the relationships
        String html = templateEngine.render(TEMPLATE_RESOURCE, view);

        // Then: class names remain visible without linking to the repository root
        assertTrue(html.contains("<td align=\"left\">com.example.A &#8594; com.example.B : 1</td>"), html);
        assertTrue(html.contains("<td align=\"left\">NoPathClass</td>"), html);
        assertFalse(html.contains("href=\"https://github.com/test/test/blob/\">com.example.A"), html);
        assertFalse(html.contains("href=\"https://github.com/test/test/blob/\">NoPathClass"), html);
    }

    @DisplayName("Simple report package relationships link their packages and classes")
    @Test
    void rendersPackageRelationshipLinksFromViewModel() throws Exception {
        // Given: a view model with package and class source paths
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("project", project());
        view.put("classMap", classMap());
        view.put("classRelationshipsToRemove", classRelationshipsToRemove());
        view.put("packageMap", packageMap());
        view.put("packageRelationshipsToRemove", packageRelationshipsToRemoveWithLinks());
        view.put("hasDisharmonies", true);
        view.put("disharmonies", List.<Map<String, Object>>of());
        view.put("classCycles", classCycles());

        // When: the simple-report template renders package relationship remediation
        String html = templateEngine.render(TEMPLATE_RESOURCE, view);

        // Then: both package and nested class names link to their repository paths
        assertTrue(
                html.contains(
                        "<a href=\"https://github.com/test/test/blob/src/main/java/com/example\" target=\"_blank\" rel=\"noopener\">com.example</a>"),
                html);
        assertTrue(
                html.contains(
                        "<a href=\"https://github.com/test/test/blob/src/main/java/com/other\" target=\"_blank\" rel=\"noopener\">com.other</a>"),
                html);
        assertTrue(
                html.contains(
                        "<a href=\"https://github.com/test/test/blob/src/main/java/com/example/A.java\" target=\"_blank\" rel=\"noopener\">A</a>"),
                html);
        assertTrue(
                html.contains(
                        "<a href=\"https://github.com/test/test/blob/src/main/java/com/example/B.java\" target=\"_blank\" rel=\"noopener\">B</a>"),
                html);
    }

    @DisplayName("Simple report template renders the footer publication timestamp")
    @Test
    void rendersFooterTimestampFromViewModel() throws Exception {
        // Given: a view model whose project carries the scan timestamp
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("project", project());
        view.put("classMap", classMap());
        view.put("classRelationshipsToRemove", classRelationshipsToRemove());
        view.put("packageMap", packageMap());
        view.put("packageRelationshipsToRemove", packageRelationshipsToRemove());
        view.put("hasDisharmonies", true);
        view.put("disharmonies", List.<Map<String, Object>>of());
        view.put("classCycles", classCycles());

        // When: the simple-report template renders the footer
        String html = templateEngine.render(TEMPLATE_RESOURCE, view);

        // Then: the footer carries the publication timestamp
        assertTrue(html.contains("Last Published: 9/8/26, 7:34 PM"), html);
    }

    // ── fixture helpers ───────────────────────────────────────────────────────

    private Map<String, Object> project() {
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("name", "TestProject");
        project.put("version", "1.0.0");
        project.put("repoUrl", "https://github.com/test/test/blob/");
        project.put("baseDir", "/test");
        project.put("scanTimestamp", "9/8/26, 7:34 PM");
        project.put("hasAnyDisharmony", true);
        project.put("analysisFailed", false);
        return project;
    }

    private Map<String, Object> classMap() {
        Map<String, Object> classMap = new LinkedHashMap<>();
        classMap.put("graphId", "classGraph");
        classMap.put("classCount", 10);
        classMap.put("relationshipCount", 20);
        classMap.put("dotThreshold", 4000);
        classMap.put("dotThresholdExceeded", false);
        classMap.put("hasEdges", false);
        return classMap;
    }

    private Map<String, Object> classRelationshipsToRemove() {
        Map<String, Object> relationships = new LinkedHashMap<>();
        relationships.put("cycleCount", 1);
        relationships.put("relationshipsToRemoveCount", 1);
        relationships.put("hasRelationships", true);
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sourceClass", "com.example.A");
        row.put("targetClass", "com.example.B");
        row.put("sourceClassPath", "src/main/java/com/example/A.java");
        row.put("targetClassPath", "src/main/java/com/example/B.java");
        row.put("simpleSourceClassName", "A");
        row.put("simpleTargetClassName", "B");
        row.put("sourceMarked", true);
        row.put("targetMarked", false);
        row.put("weight", 5);
        row.put("priority", 1);
        row.put("cycleCount", 3);
        row.put("effortRank", 2);
        row.put("alsoRemovesPackageRelationship", false);
        row.put("packageCycleCount", 0);
        rows.add(row);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("cycleCount", 1);
        value.put("relationshipsToRemoveCount", 1);
        value.put("hasRelationships", true);
        value.put("relationships", rows);
        return value;
    }

    private Map<String, Object> packageMap() {
        Map<String, Object> packageMap = new LinkedHashMap<>();
        packageMap.put("graphId", "packageGraph");
        packageMap.put("classCount", 0);
        packageMap.put("relationshipCount", 0);
        packageMap.put("dotThreshold", 4000);
        packageMap.put("dotThresholdExceeded", false);
        packageMap.put("hasEdges", false);
        return packageMap;
    }

    private Map<String, Object> packageRelationshipsToRemove() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("cycleCount", 0);
        value.put("relationshipsToRemoveCount", 0);
        value.put("hasRelationships", false);
        value.put("relationships", new ArrayList<Map<String, Object>>());
        return value;
    }

    private Map<String, Object> packageRelationshipsToRemoveWithLinks() {
        Map<String, Object> classRelationship = new LinkedHashMap<>();
        classRelationship.put("sourceClassPath", "src/main/java/com/example/A.java");
        classRelationship.put("targetClassPath", "src/main/java/com/example/B.java");
        classRelationship.put("simpleSourceClassName", "A");
        classRelationship.put("simpleTargetClassName", "B");
        classRelationship.put("weight", 5);

        Map<String, Object> packageRelationship = new LinkedHashMap<>();
        packageRelationship.put("sourcePackage", "com.example");
        packageRelationship.put("targetPackage", "com.other");
        packageRelationship.put("sourcePackagePath", "src/main/java/com/example");
        packageRelationship.put("targetPackagePath", "src/main/java/com/other");
        packageRelationship.put("sourceMarked", true);
        packageRelationship.put("targetMarked", false);
        packageRelationship.put("weight", 7);
        packageRelationship.put("priority", 1);
        packageRelationship.put("cycleCount", 2);
        packageRelationship.put("effortRank", 1);
        packageRelationship.put("classRelationshipsToBreakPackage", List.of(classRelationship));

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("cycleCount", 1);
        value.put("relationshipsToRemoveCount", 1);
        value.put("hasRelationships", true);
        value.put("relationships", List.of(packageRelationship));
        return value;
    }

    private Map<String, Object> classCycles() {
        Map<String, Object> classCycles = new LinkedHashMap<>();
        classCycles.put("hasCycles", false);
        classCycles.put("summary", new ArrayList<Map<String, Object>>());
        classCycles.put("largestCycle", null);
        return classCycles;
    }

    private Map<String, Object> disharmonySection() {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("canvasId", "chart_GOD");
        chart.put("xaxisLabel", "Effort to refactor");
        chart.put("yAxisLabel", "Relative churn");
        List<Map<String, Object>> bubbles = new ArrayList<>();
        Map<String, Object> bubble = new LinkedHashMap<>();
        bubble.put("id", "TestClass");
        bubble.put("label", "TestClass.java");
        bubble.put("x", 5);
        bubble.put("y", 10);
        bubble.put("r", 24);
        bubble.put("priority", 1);
        bubble.put("effortRank", 5);
        bubble.put("changePronenessRank", 10);
        bubbles.add(bubble);
        chart.put("bubbles", bubbles);

        Map<String, Object> table = new LinkedHashMap<>();
        table.put("headers", List.of("Class", "Priority"));
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        List<Map<String, Object>> cells = new ArrayList<>();
        Map<String, Object> cell = new LinkedHashMap<>();
        cell.put("content", "TestClass.java");
        cell.put("path", "src/main/java/TestClass.java");
        cell.put("align", "left");
        cells.add(cell);
        Map<String, Object> cell2 = new LinkedHashMap<>();
        cell2.put("content", "1");
        cell2.put("align", "right");
        cells.add(cell2);
        row.put("cells", cells);
        rows.add(row);
        table.put("rows", rows);

        Map<String, Object> section = new LinkedHashMap<>();
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
        section.put("chart", chart);
        section.put("table", table);
        return section;
    }
}
