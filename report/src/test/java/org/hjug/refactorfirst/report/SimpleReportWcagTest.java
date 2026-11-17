package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T3.3: WCAG semantics gate for the simple report — one {@code <h1>}, no skipped heading
 * levels, every table captioned with {@code scope="col"} headers, nav landmark labelled
 * "Report sections", discernible link text, and a CSS-only {@code <style>} block providing
 * sticky {@code thead th} and {@code :focus-visible} outlines (no scripts).
 */
class SimpleReportWcagTest {

    private final TemplateEngine templateEngine = new TemplateEngine();

    private static final String TEMPLATE_RESOURCE = "templates/refactor-first-simple-report.mustache";

    private static final Pattern HEADING_TAG = Pattern.compile("<h([1-6])\\b");
    private static final Pattern TABLE_TAG = Pattern.compile("<table\\b");
    private static final Pattern CAPTION_TAG = Pattern.compile("<caption\\b");
    private static final Pattern COLUMN_HEADER_TAG = Pattern.compile("<th\\b[^>]*>");
    private static final Pattern EMPTY_ANCHOR_TAG = Pattern.compile("<a\\b[^>]*>\\s*</a>");

    @DisplayName("Simple report has exactly one h1 and no skipped heading levels")
    @Test
    void hasSingleH1AndNoSkippedHeadingLevels() throws Exception {
        // Given: the simple report rendered with all sections present
        String html = templateEngine.render(TEMPLATE_RESOURCE, view());

        // Then: exactly one h1 carries the report title
        Matcher headings = HEADING_TAG.matcher(html);
        List<Integer> levels = new ArrayList<>();
        while (headings.find()) {
            levels.add(Integer.parseInt(headings.group(1)));
        }
        assertEquals(1, levels.stream().filter(level -> level == 1).count(), "exactly one h1: " + html);

        // And: heading levels never skip (each level is at most one deeper than the previous)
        for (int i = 1; i < levels.size(); i++) {
            assertTrue(
                    levels.get(i) <= levels.get(i - 1) + 1,
                    "heading level jump from h" + levels.get(i - 1) + " to h" + levels.get(i));
        }
    }

    @DisplayName("Every simple report table has a caption and scope=col headers")
    @Test
    void everyTableHasCaptionAndScopedColumnHeaders() throws Exception {
        // Given: the simple report rendered with relationship, disharmony and cycle tables
        String html = templateEngine.render(TEMPLATE_RESOURCE, view());

        // Then: each table carries a caption
        int tables = count(TABLE_TAG, html);
        assertTrue(tables >= 4, "expected the relationship, disharmony, package and cycle tables: " + tables);
        assertEquals(tables, count(CAPTION_TAG, html), "every table must have a <caption>: " + html);

        // And: every column header is scoped
        Matcher headers = COLUMN_HEADER_TAG.matcher(html);
        StringBuilder unscoped = new StringBuilder();
        while (headers.find()) {
            if (!headers.group().contains("scope=\"col\"")) {
                unscoped.append(headers.group()).append("; ");
            }
        }
        assertEquals("", unscoped.toString(), "every <th> must carry scope=\"col\"");
    }

    @DisplayName("Simple report nav landmark is labelled Report sections")
    @Test
    void navLandmarkIsLabelled() throws Exception {
        // Given: the simple report rendered
        String html = templateEngine.render(TEMPLATE_RESOURCE, view());

        // Then: the nav carries the accessible name
        assertTrue(
                Pattern.compile("<nav\\b[^>]*aria-label=\"Report sections\"")
                        .matcher(html)
                        .find(),
                "nav must have aria-label=\"Report sections\": " + html);
    }

    @DisplayName("Simple report links carry discernible text")
    @Test
    void linksHaveDiscernibleText() throws Exception {
        // Given: the simple report rendered
        String html = templateEngine.render(TEMPLATE_RESOURCE, view());

        // Then: no anchor renders empty
        assertFalse(EMPTY_ANCHOR_TAG.matcher(html).find(), "every link must carry discernible text: " + html);
    }

    @DisplayName("Simple report style block provides sticky thead th and focus-visible outlines")
    @Test
    void styleBlockProvidesStickyHeadersAndFocusOutlines() throws Exception {
        // Given: the simple report rendered
        String html = templateEngine.render(TEMPLATE_RESOURCE, view());

        // Then: the CSS-only sticky header and focus outline rules are present
        assertTrue(html.contains("<style"), "report must embed a <style> block");
        assertTrue(
                Pattern.compile("thead\\s+th[^}]*position\\s*:\\s*sticky", Pattern.CASE_INSENSITIVE)
                        .matcher(html)
                        .find(),
                "thead th must be position: sticky");
        assertTrue(html.contains(":focus-visible"), "interactive elements must show :focus-visible outlines");
    }

    // ── fixture helpers ───────────────────────────────────────────────────────

    private int count(Pattern pattern, String html) {
        int count = 0;
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private Map<String, Object> view() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("project", project());
        view.put("classMap", classMap());
        view.put("classRelationshipsToRemove", classRelationshipsToRemove());
        view.put("packageMap", packageMap());
        view.put("packageRelationshipsToRemove", packageRelationshipsToRemove());
        view.put("hasDisharmonies", true);
        view.put("disharmonies", List.of(disharmonySection()));
        view.put("classCycles", classCycles());
        return view;
    }

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

    private Map<String, Object> classRelationshipsToRemove() {
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

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("cycleCount", 1);
        value.put("relationshipsToRemoveCount", 1);
        value.put("hasRelationships", true);
        value.put("relationships", List.of(row));
        return value;
    }

    private Map<String, Object> packageRelationshipsToRemove() {
        Map<String, Object> brokenClassRelationship = new LinkedHashMap<>();
        brokenClassRelationship.put("sourceClass", "com.example.A");
        brokenClassRelationship.put("targetClass", "com.example.B");
        brokenClassRelationship.put("sourceClassPath", "src/main/java/com/example/A.java");
        brokenClassRelationship.put("targetClassPath", "src/main/java/com/example/B.java");
        brokenClassRelationship.put("simpleSourceClassName", "A");
        brokenClassRelationship.put("simpleTargetClassName", "B");
        brokenClassRelationship.put("weight", 5);

        Map<String, Object> packageRow = new LinkedHashMap<>();
        packageRow.put("sourcePackage", "com.example");
        packageRow.put("targetPackage", "com.other");
        packageRow.put("sourcePackagePath", "src/main/java/com/example");
        packageRow.put("targetPackagePath", "src/main/java/com/other");
        packageRow.put("sourceMarked", true);
        packageRow.put("targetMarked", false);
        packageRow.put("weight", 7);
        packageRow.put("priority", 1);
        packageRow.put("cycleCount", 2);
        packageRow.put("effortRank", 1);
        packageRow.put("classRelationshipsToBreakPackage", List.of(brokenClassRelationship));

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("cycleCount", 1);
        value.put("relationshipsToRemoveCount", 1);
        value.put("hasRelationships", true);
        value.put("relationships", List.of(packageRow));
        return value;
    }

    private Map<String, Object> disharmonySection() {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("canvasId", "chart_GOD");
        chart.put("xaxisLabel", "Effort to refactor");
        chart.put("yAxisLabel", "Relative churn");
        chart.put("bubbles", new ArrayList<>());

        Map<String, Object> table = new LinkedHashMap<>();
        table.put("headers", List.of("Class", "Priority", "Change Proneness Rank", "Effort Rank"));
        List<Map<String, Object>> cells = new ArrayList<>();
        Map<String, Object> classCell = new LinkedHashMap<>();
        classCell.put("content", "TestClass.java");
        classCell.put("path", "src/main/java/TestClass.java");
        classCell.put("align", "left");
        cells.add(classCell);
        Map<String, Object> priorityCell = new LinkedHashMap<>();
        priorityCell.put("content", "1");
        priorityCell.put("align", "right");
        cells.add(priorityCell);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cells", cells);

        Map<String, Object> tableView = new LinkedHashMap<>();
        tableView.put("headers", table.get("headers"));
        tableView.put("rows", List.of(row));

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("type", "God Class");
        section.put("anchorId", "GOD");
        section.put("title", "God Classes");
        section.put("methodLevel", false);
        section.put("problem", "God Classes take on too much responsibility");
        section.put("solution", "Extract related functionality");
        section.put("maxPriority", 5);
        section.put("chart", chart);
        section.put("table", tableView);
        return section;
    }

    private Map<String, Object> classCycles() {
        Map<String, Object> summaryRow = new LinkedHashMap<>();
        summaryRow.put("cycleName", "com.example.A");
        summaryRow.put("priority", 1);
        summaryRow.put("classCount", 2);
        summaryRow.put("relationshipCount", 2);

        Map<String, Object> breakdownRow = new LinkedHashMap<>();
        breakdownRow.put("className", "com.example.A");
        breakdownRow.put("classPath", "src/main/java/com/example/A.java");
        breakdownRow.put("marked", true);
        breakdownRow.put(
                "edgesHtml",
                "<a href=\"https://github.com/test/test/blob/src/main/java/com/example/A.java\" "
                        + "target=\"_blank\">A</a> &#8594; <a href=\"https://github.com/test/test/blob/src/main/java/com/example/B.java\" "
                        + "target=\"_blank\">B</a> : 5");

        Map<String, Object> largestCycle = new LinkedHashMap<>();
        largestCycle.put("hasCycleMap", true);
        largestCycle.put("cycleName", "com.example.A");
        largestCycle.put("cycleIdentifier", "com.example.A");
        largestCycle.put("classCount", 2);
        largestCycle.put("relationshipCount", 2);
        largestCycle.put("dotThresholdExceeded", false);
        largestCycle.put("breakdown", List.of(breakdownRow));

        Map<String, Object> classCycles = new LinkedHashMap<>();
        classCycles.put("hasCycles", true);
        classCycles.put("summary", List.of(summaryRow));
        classCycles.put("largestCycle", largestCycle);
        return classCycles;
    }
}
