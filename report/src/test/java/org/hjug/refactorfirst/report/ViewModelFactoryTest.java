package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.hjug.refactorfirst.report.model.ChartJsBubbleDTO;
import org.hjug.refactorfirst.report.model.ClassCyclesDTO;
import org.hjug.refactorfirst.report.model.ClassRelationshipDTO;
import org.hjug.refactorfirst.report.model.ClassRelationshipsToRemoveDTO;
import org.hjug.refactorfirst.report.model.CycleBreakdownRowDTO;
import org.hjug.refactorfirst.report.model.CycleSummaryDTO;
import org.hjug.refactorfirst.report.model.DisharmonyChartDTO;
import org.hjug.refactorfirst.report.model.DisharmonySectionDTO;
import org.hjug.refactorfirst.report.model.DisharmonyTableDTO;
import org.hjug.refactorfirst.report.model.DisharmonyTableRowDTO;
import org.hjug.refactorfirst.report.model.GraphVisualDTO;
import org.hjug.refactorfirst.report.model.HierarchyDTO;
import org.hjug.refactorfirst.report.model.LargestCycleDTO;
import org.hjug.refactorfirst.report.model.PackageRelationshipsToRemoveDTO;
import org.hjug.refactorfirst.report.model.ProjectMetadataDTO;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
import org.junit.jupiter.api.Test;

/**
 * Pins the view-model contract the Mustache templates render against, mirroring the page repo's
 * {@code prepareReportData}: the report DTO is translated into the nested view-model maps the
 * Mustache sections, tables and graph buttons consume.
 */
class ViewModelFactoryTest {

    private final ViewModelFactory viewModelFactory = new ViewModelFactory();

    @Test
    @SuppressWarnings("unchecked")
    void nestedClassFlagsOverridePackageFlags() {
        RefactorFirstReportDTO report = fixtureReport();
        report.setPackageRelationshipsToRemove(PackageRelationshipsToRemoveDTO.builder()
                .relationships(List.of(org.hjug.refactorfirst.report.model.PackageRelationshipDTO.builder()
                        .sourceMarked(true)
                        .targetMarked(false)
                        .classRelationshipsToBreakPackage(List.of(ClassRelationshipDTO.builder()
                                .sourceMarked(false)
                                .targetMarked(true)
                                .build()))
                        .build()))
                .build());
        Map<String, Object> section =
                (Map<String, Object>) viewModelFactory.createViewModel(report).get("packageRelationshipsToRemove");
        Map<String, Object> parent = ((List<Map<String, Object>>) section.get("relationships")).get(0);
        Map<String, Object> child = ((List<Map<String, Object>>) parent.get("classRelationshipsToBreakPackage")).get(0);
        assertEquals(false, child.get("sourceMarked"));
        assertEquals(true, child.get("targetMarked"));
    }

    @Test
    void sourceUrlsMatchBrowserEncoding() {
        assertEquals(
                "https://example.org/src/A%20%23%25%C3%A9.java",
                ReportModelBuilder.sourceUrl("https://example.org", "/src/A #%é.java"));
        assertEquals(
                "https://example.org/src/A.java", ReportModelBuilder.sourceUrl("https://example.org/", "src/A.java"));
        assertEquals("https://example.org/!'()*~", ReportModelBuilder.sourceUrl("https://example.org", "!'()*~"));
    }

    /** Exposes project metadata, disharmony flags and graph thresholds through the view model. */
    @Test
    void givenReportDto_whenCreateViewModel_thenProjectFlagsAndGraphMetadataAreExposed() {
        // Given: a report with disharmony findings, a class relationship to remove and graph data
        RefactorFirstReportDTO report = fixtureReport();

        // When: the view model is built
        Map<String, Object> view = viewModelFactory.createViewModel(report);

        // Then: the project block carries the metadata and flags
        Map<String, Object> project = (Map<String, Object>) view.get("project");
        assertNotNull(project);
        assertEquals("TestProject", project.get("name"));
        assertEquals("1.0.0", project.get("version"));
        assertEquals("https://github.com/test/test/blob/", project.get("repoUrl"));
        assertTrue((Boolean) project.get("hasAnyDisharmony"));
        assertFalse((Boolean) project.get("analysisFailed"));

        // Then: the top-level flags and graph blocks keep their names
        assertTrue((Boolean) view.get("hasDisharmonies"));
        Map<String, Object> classMap = (Map<String, Object>) view.get("classMap");
        assertNotNull(classMap);
        assertEquals("classGraph", classMap.get("graphId"));
        assertEquals(10, ((Number) classMap.get("classCount")).intValue());
        assertFalse((Boolean) classMap.get("dotThresholdExceeded"));

        Map<String, Object> classRels = (Map<String, Object>) view.get("classRelationshipsToRemove");
        assertNotNull(classRels);
        assertTrue((Boolean) classRels.get("hasRelationships"));

        Map<String, Object> packageMap = (Map<String, Object>) view.get("packageMap");
        assertNotNull(packageMap);
        assertFalse((Boolean) packageMap.get("hasEdges"));

        Map<String, Object> packageRels = (Map<String, Object>) view.get("packageRelationshipsToRemove");
        assertNotNull(packageRels);
        assertFalse((Boolean) packageRels.get("hasRelationships"));

        Map<String, Object> classCycles = (Map<String, Object>) view.get("classCycles");
        assertNotNull(classCycles);
        assertTrue((Boolean) classCycles.get("hasCycles"));
    }

    /** Exposes the disharmony anchor, problem, solution and chart/table data per section. */
    @Test
    void givenReportDtoWithDisharmony_whenCreateViewModel_thenDisharmonySectionIsExposed() {
        // Given: a report whose only disharmony is a God Class
        RefactorFirstReportDTO report = fixtureReport();

        // When: the view model is built
        Map<String, Object> view = viewModelFactory.createViewModel(report);
        List<Map<String, Object>> disharmonies = (List<Map<String, Object>>) view.get("disharmonies");
        assertNotNull(disharmonies);
        assertEquals(1, disharmonies.size());

        Map<String, Object> section = disharmonies.get(0);
        assertEquals("GOD", section.get("anchorId"));
        assertEquals("God Classes", section.get("title"));
        assertEquals("God Class", section.get("type"));
        assertFalse((Boolean) section.get("methodLevel"));
        assertEquals("God Classes take on too much responsibility", section.get("problem"));
        assertEquals("Extract related functionality", section.get("solution"));
        assertEquals(5, ((Number) section.get("maxPriority")).intValue());

        Map<String, Object> chart = (Map<String, Object>) section.get("chart");
        assertNotNull(chart);
        assertEquals("chart_GOD", chart.get("canvasId"));
        assertEquals("Effort to refactor", chart.get("xaxisLabel"));
        assertEquals("Relative churn", chart.get("yaxisLabel"));

        List<Map<String, Object>> bubbles = (List<Map<String, Object>>) chart.get("bubbles");
        assertNotNull(bubbles);
        assertEquals(1, bubbles.size());
        Map<String, Object> bubble = bubbles.get(0);
        assertEquals("TestClass", bubble.get("id"));
        assertEquals("TestClass.java", bubble.get("label"));
        assertEquals(24, ((Number) bubble.get("r")).intValue());

        Map<String, Object> table = (Map<String, Object>) section.get("table");
        assertNotNull(table);
        List<String> headers = (List<String>) table.get("headers");
        assertNotNull(headers);
        assertTrue(headers.contains("Class"));
        assertTrue(headers.contains("Priority"));
        List<Map<String, Object>> rows = (List<Map<String, Object>>) table.get("rows");
        assertNotNull(rows);
        assertEquals(1, rows.size());
        List<Map<String, Object>> cells = (List<Map<String, Object>>) ((Map<String, Object>) rows.get(0)).get("cells");
        assertNotNull(cells);
        assertEquals(2, cells.size());
    }

    /** Exposes class relationship rows with source, target and removal markers. */
    @Test
    void givenReportDtoWithClassRelationship_whenCreateViewModel_thenRelationshipRowIsExposed() {
        // Given: a report whose class relationship to remove links two classes
        RefactorFirstReportDTO report = fixtureReport();

        // When: the view model is built
        Map<String, Object> view = viewModelFactory.createViewModel(report);
        Map<String, Object> classRels = (Map<String, Object>) view.get("classRelationshipsToRemove");
        List<Map<String, Object>> relationships = (List<Map<String, Object>>) classRels.get("relationships");
        assertNotNull(relationships);
        assertEquals(1, relationships.size());

        Map<String, Object> relationship = relationships.get(0);
        assertEquals("com.example.A", relationship.get("sourceClass"));
        assertEquals("com.example.B", relationship.get("targetClass"));
        assertEquals("src/main/java/com/example/A.java", relationship.get("sourceClassPath"));
        assertEquals("A", relationship.get("simpleSourceClassName"));
        assertTrue((Boolean) relationship.get("sourceMarked"));
        assertEquals(5, ((Number) relationship.get("weight")).intValue());
    }

    /** Exposes the largest-cycle summary and breakdown rows. */
    @Test
    void givenReportDtoWithCycles_whenCreateViewModel_thenCycleDataIsExposed() {
        // Given: a report whose class cycles include a largest cycle with a breakdown
        RefactorFirstReportDTO report = fixtureReport();

        // When: the view model is built
        Map<String, Object> view = viewModelFactory.createViewModel(report);
        Map<String, Object> classCycles = (Map<String, Object>) view.get("classCycles");
        assertTrue((Boolean) classCycles.get("hasCycles"));

        List<Map<String, Object>> summary = (List<Map<String, Object>>) classCycles.get("summary");
        assertNotNull(summary);
        assertEquals(1, summary.size());
        Map<String, Object> cycleSummary = summary.get(0);
        assertEquals("A -> B -> C -> A", cycleSummary.get("cycleName"));
        assertEquals(1, ((Number) cycleSummary.get("priority")).intValue());
        assertEquals(3, ((Number) cycleSummary.get("classCount")).intValue());

        Map<String, Object> largestCycle = (Map<String, Object>) classCycles.get("largestCycle");
        assertNotNull(largestCycle);
        assertTrue((Boolean) largestCycle.get("hasCycleMap"));
        assertEquals(2, ((Number) largestCycle.get("classCount")).intValue());

        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) largestCycle.get("breakdown");
        assertNotNull(breakdown);
        assertEquals(1, breakdown.size());
        assertEquals("ClassA", ((Map<String, Object>) breakdown.get(0)).get("className"));
    }

    private RefactorFirstReportDTO fixtureReport() {
        ProjectMetadataDTO project = ProjectMetadataDTO.builder()
                .name("TestProject")
                .version("1.0.0")
                .repoUrl("https://github.com/test/test/blob/")
                .baseDir("/test")
                .scanTimestamp("9/8/26, 7:34 PM")
                .hasAnyDisharmony(true)
                .analysisFailed(false)
                .build();

        GraphVisualDTO classMap = GraphVisualDTO.builder()
                .graphId("classGraph")
                .classCount(10)
                .relationshipCount(20)
                .hierarchy(HierarchyDTO.builder()
                        .packages(java.util.List.of())
                        .edges(java.util.List.of())
                        .build())
                .dotThreshold(4000)
                .dotThresholdExceeded(false)
                .build();

        ClassRelationshipDTO classRelationship = ClassRelationshipDTO.builder()
                .sourceClass("com.example.A")
                .targetClass("com.example.B")
                .sourceClassPath("src/main/java/com/example/A.java")
                .targetClassPath("src/main/java/com/example/B.java")
                .simpleSourceClassName("A")
                .simpleTargetClassName("B")
                .sourceMarked(true)
                .targetMarked(false)
                .weight(5)
                .priority(1)
                .cycleCount(3)
                .effortRank(2)
                .build();

        ClassRelationshipsToRemoveDTO classRels = ClassRelationshipsToRemoveDTO.builder()
                .cycleCount(1)
                .relationshipsToRemoveCount(1)
                .hasRelationships(true)
                .relationships(List.of(classRelationship))
                .build();

        DisharmonyChartDTO chart = DisharmonyChartDTO.builder()
                .canvasId("chart_GOD")
                .xAxisLabel("Effort to refactor")
                .yAxisLabel("Relative churn")
                .bubbles(List.of(ChartJsBubbleDTO.builder()
                        .id("TestClass")
                        .label("TestClass.java")
                        .x(5)
                        .y(10)
                        .r(24)
                        .priority(1)
                        .effortRank(5)
                        .changePronenessRank(10)
                        .build()))
                .build();

        DisharmonyTableDTO table = DisharmonyTableDTO.builder()
                .headers(List.of("Class", "Priority"))
                .rows(List.of(DisharmonyTableRowDTO.builder()
                        .cells(List.of(
                                org.hjug.refactorfirst.report.model.DisharmonyTableCellDTO.builder()
                                        .content("TestClass.java")
                                        .path("src/main/java/TestClass.java")
                                        .align("left")
                                        .build(),
                                org.hjug.refactorfirst.report.model.DisharmonyTableCellDTO.builder()
                                        .content("1")
                                        .align("right")
                                        .build()))
                        .build()))
                .build();

        DisharmonySectionDTO section = DisharmonySectionDTO.builder()
                .type("God Class")
                .anchorId("GOD")
                .title("God Classes")
                .methodLevel(false)
                .problem("God Classes take on too much responsibility")
                .solution("Extract related functionality")
                .maxPriority(5)
                .chart(chart)
                .table(table)
                .build();

        CycleSummaryDTO cycleSummary = CycleSummaryDTO.builder()
                .cycleName("A -> B -> C -> A")
                .priority(1)
                .classCount(3)
                .relationshipCount(3)
                .build();

        CycleBreakdownRowDTO breakdownRow = CycleBreakdownRowDTO.builder()
                .className("ClassA")
                .classPath("src/main/java/ClassA.java")
                .marked(true)
                .edgesHtml("A &#8594; B : 1")
                .build();

        LargestCycleDTO largestCycle = LargestCycleDTO.builder()
                .hasCycleMap(true)
                .cycleName("A -> B -> C -> A")
                .cycleIdentifier("graph_cycle")
                .classCount(2)
                .relationshipCount(2)
                .breakdown(List.of(breakdownRow))
                .build();

        ClassCyclesDTO classCycles = ClassCyclesDTO.builder()
                .hasCycles(true)
                .summary(List.of(cycleSummary))
                .largestCycle(largestCycle)
                .build();

        return RefactorFirstReportDTO.builder()
                .project(project)
                .classMap(classMap)
                .classRelationshipsToRemove(classRels)
                .packageMap(org.hjug.refactorfirst.report.model.GraphVisualDTO.builder()
                        .hasEdges(false)
                        .build())
                .packageRelationshipsToRemove(PackageRelationshipsToRemoveDTO.builder()
                        .cycleCount(0)
                        .relationshipsToRemoveCount(0)
                        .hasRelationships(false)
                        .build())
                .hasDisharmonies(true)
                .disharmonies(List.of(section))
                .classCycles(classCycles)
                .build();
    }
}
