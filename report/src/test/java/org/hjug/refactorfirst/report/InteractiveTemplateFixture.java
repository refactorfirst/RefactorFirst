package org.hjug.refactorfirst.report;

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
import org.hjug.refactorfirst.report.model.DisharmonyTableCellDTO;
import org.hjug.refactorfirst.report.model.DisharmonyTableDTO;
import org.hjug.refactorfirst.report.model.DisharmonyTableRowDTO;
import org.hjug.refactorfirst.report.model.GraphVisualDTO;
import org.hjug.refactorfirst.report.model.HierarchyDTO;
import org.hjug.refactorfirst.report.model.LargestCycleDTO;
import org.hjug.refactorfirst.report.model.PackageRelationshipsToRemoveDTO;
import org.hjug.refactorfirst.report.model.ProjectMetadataDTO;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;

/** The small fixture report DTO + view model the interactive-template tests render against. */
final class InteractiveTemplateFixture {

    private InteractiveTemplateFixture() {}

    /** Builds the small fixture report DTO the interactive template tests render against. */
    static RefactorFirstReportDTO reportDto() {
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
                        .packages(List.of())
                        .edges(List.of())
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
                                DisharmonyTableCellDTO.builder()
                                        .content("TestClass.java")
                                        .path("src/main/java/TestClass.java")
                                        .align("left")
                                        .build(),
                                DisharmonyTableCellDTO.builder()
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
                .packageMap(GraphVisualDTO.builder().hasEdges(false).build())
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

    /** Translates the fixture DTO into the view model, plus the pipeline-provided rf-data payload. */
    static Map<String, Object> viewModel() {
        Map<String, Object> view = new ViewModelFactory().createViewModel(reportDto());
        // The rf-data JSON block is pipeline-provided; the fixture pins an inert payload.
        view.put("rfDataBlock", "{\"project\":{\"name\":\"TestProject\"},\"graphs\":{\"classGraph\":\"digraph {}\"}}");
        return view;
    }
}
