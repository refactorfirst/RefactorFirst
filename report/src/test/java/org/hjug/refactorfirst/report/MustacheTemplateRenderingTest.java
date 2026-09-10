package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hjug.refactorfirst.report.model.*;
import org.junit.jupiter.api.Test;

class MustacheTemplateRenderingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String loadTemplate() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/templates/refactor-first-report.mustache")) {
            if (is == null) {
                throw new IllegalStateException("Template not found in classpath");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // HTML entity for > is \u0026gt; (rendered from "A -> B -> C -> A" where > is escaped)
    private static final String CYCLE_NAME_ESCAPED = "A -\u0026gt; B -\u0026gt; C -\u0026gt; A";

    @Test
    void testTemplateRendersProjectHeaderAndNav() throws Exception {
        String template = loadTemplate();

        ProjectMetadataDTO project = ProjectMetadataDTO.builder()
                .name("TestProject")
                .version("1.0.0")
                .repoUrl("https://github.com/test/test")
                .baseDir("/test")
                .scanTimestamp("9/8/26, 7:34 PM")
                .hasAnyDisharmony(true)
                .build();

        GraphVisualDTO classMap = GraphVisualDTO.builder()
                .graphId("classGraph")
                .classCount(10)
                .relationshipCount(20)
                .dot("digraph G {}")
                .dotThresholdExceeded(false)
                .build();

        RefactorFirstReportDTO report = RefactorFirstReportDTO.builder()
                .project(project)
                .classMap(classMap)
                .classRelationshipsToRemove(ClassRelationshipsToRemoveDTO.builder()
                        .cycleCount(0)
                        .relationshipsToRemoveCount(0)
                        .relationships(List.of())
                        .build())
                .packageMap(GraphVisualDTO.builder().hasEdges(false).build())
                .packageRelationshipsToRemove(PackageRelationshipsToRemoveDTO.builder()
                        .cycleCount(0)
                        .relationshipsToRemoveCount(0)
                        .relationships(List.of())
                        .build())
                .disharmonies(List.of())
                .classCycles(ClassCyclesDTO.builder().hasCycles(false).build())
                .build();

        String rendered = renderTemplate(template, report);

        // Verify project header
        assertTrue(rendered.contains("<h1 align=\"center\">"));
        assertTrue(rendered.contains("RefactorFirst</a> Report for"));
        assertTrue(
                rendered.contains("<a href=\"https://github.com/test/test\" target=\"_blank\">TestProject 1.0.0</a>"));

        // Verify GitHub badges
        assertTrue(rendered.contains("Show RefactorFirst some"));
        assertTrue(rendered.contains("data-icon=\"octicon-star\""));
        assertTrue(rendered.contains("data-icon=\"octicon-repo-forked\""));
        assertTrue(rendered.contains("data-icon=\"octicon-eye\""));
        assertTrue(rendered.contains("data-icon=\"octicon-issue-opened\""));
        assertTrue(rendered.contains("data-icon=\"octicon-heart\""));

        // Verify navigation
        assertTrue(rendered.contains("<a href=\"#CLASSMAP\">Class Map</a>"));
    }

    @Test
    void testTemplateRendersClassRelationshipTable() throws Exception {
        String template = loadTemplate();

        ProjectMetadataDTO project = ProjectMetadataDTO.builder()
                .name("TestProject")
                .version("1.0.0")
                .repoUrl("https://github.com/test/test")
                .baseDir("/test")
                .scanTimestamp("9/8/26, 7:34 PM")
                .hasAnyDisharmony(true)
                .build();

        GraphVisualDTO classMap = GraphVisualDTO.builder()
                .graphId("classGraph")
                .classCount(10)
                .relationshipCount(20)
                .dot("digraph G {}")
                .dotThresholdExceeded(false)
                .build();

        ClassRelationshipDTO rel = ClassRelationshipDTO.builder()
                .sourceClass("com.example.A")
                .targetClass("com.example.B")
                .sourceMarked(true)
                .targetMarked(false)
                .weight(5)
                .renderedLabel("<a href=\"...\">A</a> &rarr; <a href=\"...\">B</a>")
                .priority(1)
                .cycleCount(3)
                .effortRank(2)
                .alsoRemovesPackageRelationship(true)
                .packageCycleCount(1)
                .build();

        ClassRelationshipsToRemoveDTO classRels = ClassRelationshipsToRemoveDTO.builder()
                .cycleCount(5)
                .relationshipsToRemoveCount(3)
                .relationships(List.of(rel))
                .build();

        RefactorFirstReportDTO report = RefactorFirstReportDTO.builder()
                .project(project)
                .classMap(classMap)
                .classRelationshipsToRemove(classRels)
                .packageMap(GraphVisualDTO.builder().hasEdges(false).build())
                .packageRelationshipsToRemove(PackageRelationshipsToRemoveDTO.builder()
                        .cycleCount(0)
                        .relationshipsToRemoveCount(0)
                        .relationships(List.of())
                        .build())
                .disharmonies(List.of())
                .classCycles(ClassCyclesDTO.builder().hasCycles(false).build())
                .build();

        String rendered = renderTemplate(template, report);

        // Verify table headers
        assertTrue(rendered.contains("<th>Class Relationship</th>"));
        assertTrue(rendered.contains("<th>Priority</th>"));
        assertTrue(rendered.contains("In Class<br>Cycles"));
        assertTrue(rendered.contains("Relationship<br>Strength"));
        assertTrue(rendered.contains("Also Removes Pkg<br>Cycle Relationship"));
        assertTrue(rendered.contains("In Package<br>Cycles"));

        // Verify table data - alsoRemovesPackageRelationship renders <strong>true</strong>
        assertTrue(rendered.contains("<strong>true</strong>"));
    }

    @Test
    void testTemplateRendersDisharmonyCanvases() throws Exception {
        String template = loadTemplate();

        ProjectMetadataDTO project = ProjectMetadataDTO.builder()
                .name("TestProject")
                .version("1.0.0")
                .repoUrl("https://github.com/test/test")
                .baseDir("/test")
                .scanTimestamp("9/8/26, 7:34 PM")
                .hasAnyDisharmony(true)
                .build();

        GraphVisualDTO classMap = GraphVisualDTO.builder()
                .graphId("classGraph")
                .classCount(10)
                .relationshipCount(20)
                .dot("digraph G {}")
                .dotThresholdExceeded(false)
                .build();

        ChartJsBubbleDTO bubble = ChartJsBubbleDTO.builder()
                .id("TestClass")
                .label("TestClass.java")
                .x(5)
                .y(10)
                .r(18)
                .priority(1)
                .effortRank(5)
                .changePronenessRank(10)
                .color("rgba(235, 64, 52, 0.75)")
                .borderColor("rgb(235, 64, 52)")
                .build();

        DisharmonyChartDTO chart = DisharmonyChartDTO.builder()
                .canvasId("chart_GOD")
                .xAxisLabel("Effort to refactor")
                .yAxisLabel("Relative churn")
                .bubbles(List.of(bubble))
                .build();

        DisharmonyTableDTO table = DisharmonyTableDTO.builder()
                .headers(List.of("Class", "Priority"))
                .rows(List.of(DisharmonyTableRowDTO.builder()
                        .cells(List.of(
                                DisharmonyTableCellDTO.builder()
                                        .content("TestClass.java")
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

        RefactorFirstReportDTO report = RefactorFirstReportDTO.builder()
                .project(project)
                .classMap(classMap)
                .classRelationshipsToRemove(ClassRelationshipsToRemoveDTO.builder()
                        .cycleCount(0)
                        .relationshipsToRemoveCount(0)
                        .relationships(List.of())
                        .build())
                .packageMap(GraphVisualDTO.builder().hasEdges(false).build())
                .packageRelationshipsToRemove(PackageRelationshipsToRemoveDTO.builder()
                        .cycleCount(0)
                        .relationshipsToRemoveCount(0)
                        .relationships(List.of())
                        .build())
                .disharmonies(List.of(section))
                .classCycles(ClassCyclesDTO.builder().hasCycles(false).build())
                .build();

        String rendered = renderTemplate(template, report);

        // Verify disharmony section
        assertTrue(rendered.contains("<a id=\"GOD\"><h1>God Classes</h1></a>"));
        assertTrue(rendered.contains("<strong>Problem:</strong>"));
        assertTrue(rendered.contains("God Classes take on too much responsibility"));
        assertTrue(rendered.contains("<strong>Solution:</strong>"));
        assertTrue(rendered.contains("Extract related functionality"));

        // Verify Chart.js canvas
        assertTrue(rendered.contains("<canvas id=\"chart_GOD\" width=\"1100\" height=\"500\"></canvas>"));

        // Verify chart legend
        assertTrue(rendered.contains("<strong>X-Axis:</strong> Effort to refactor"));
        assertTrue(rendered.contains("<strong>Y-Axis:</strong> Relative churn"));
        assertTrue(rendered.contains("<strong>Color:</strong> Priority of what to fix first"));
        assertTrue(rendered.contains("<strong>Circle size:</strong> Priority (Visual) of what to fix first"));

        // Verify table
        assertTrue(rendered.contains("<th>Class</th>"));
        assertTrue(rendered.contains("<th>Priority</th>"));
    }

    @Test
    void testTemplateRendersCycleMapAndBreakdown() throws Exception {
        String template = loadTemplate();

        ProjectMetadataDTO project = ProjectMetadataDTO.builder()
                .name("TestProject")
                .version("1.0.0")
                .repoUrl("https://github.com/test/test")
                .baseDir("/test")
                .scanTimestamp("9/8/26, 7:34 PM")
                .hasAnyDisharmony(true)
                .build();

        GraphVisualDTO classMap = GraphVisualDTO.builder()
                .graphId("classGraph")
                .classCount(10)
                .relationshipCount(20)
                .dot("digraph G {}")
                .dotThresholdExceeded(false)
                .build();

        CycleSummaryDTO cycleSummary = CycleSummaryDTO.builder()
                .cycleName("A -> B -> C -> A")
                .priority(1)
                .classCount(3)
                .relationshipCount(3)
                .build();

        CycleBreakdownRowDTO breakdownRow = CycleBreakdownRowDTO.builder()
                .className("<a href=\"...\">A</a><strong>*</strong>")
                .edgesHtml("<strong>A &rarr; B<strong>*</strong></strong><br/>")
                .build();

        LargestCycleDTO largestCycle = LargestCycleDTO.builder()
                .hasCycleMap(true)
                .cycleName("A -> B -> C -> A")
                .cycleIdentifier("graph_A_B_C_A_abc123")
                .classCount(3)
                .relationshipCount(3)
                .dotThresholdExceeded(false)
                .dot("digraph G { A -> B; B -> C; C -> A; }")
                .breakdown(List.of(breakdownRow))
                .build();

        ClassCyclesDTO classCycles = ClassCyclesDTO.builder()
                .hasCycles(true)
                .summary(List.of(cycleSummary))
                .largestCycle(largestCycle)
                .build();

        RefactorFirstReportDTO report = RefactorFirstReportDTO.builder()
                .project(project)
                .classMap(classMap)
                .classRelationshipsToRemove(ClassRelationshipsToRemoveDTO.builder()
                        .cycleCount(0)
                        .relationshipsToRemoveCount(0)
                        .relationships(List.of())
                        .build())
                .packageMap(GraphVisualDTO.builder().hasEdges(false).build())
                .packageRelationshipsToRemove(PackageRelationshipsToRemoveDTO.builder()
                        .cycleCount(0)
                        .relationshipsToRemoveCount(0)
                        .relationships(List.of())
                        .build())
                .disharmonies(List.of())
                .classCycles(classCycles)
                .build();

        String rendered = renderTemplate(template, report);

        // Verify cycles summary table
        assertTrue(rendered.contains("<a id=\"CYCLES\"><h1>Class Cycles</h1></a>"));
        assertTrue(rendered.contains("<th>Cycle Name</th>"));
        assertTrue(rendered.contains("<th>Priority</th>"));
        assertTrue(rendered.contains("<th>Class Count</th>"));
        assertTrue(rendered.contains("<th>Relationship Count</th>"));
        // Mustache escapes HTML by default: > becomes >
        assertTrue(rendered.contains(CYCLE_NAME_ESCAPED));

        // Verify cycle map section
        assertTrue(rendered.contains("<a id=\"CYCLEMAP\">Largest Class Cycle"));
        assertTrue(rendered.contains("Limiting number of cycles displayed to 1"));
        assertTrue(rendered.contains("Show " + CYCLE_NAME_ESCAPED + " 3D Popup"));
        assertTrue(rendered.contains("Show " + CYCLE_NAME_ESCAPED + " 2D Popup"));

        // Verify cycle breakdown table
        assertTrue(rendered.contains("<th>Classes</th>"));
        assertTrue(rendered.contains("<th>Relationships</th>"));
        assertTrue(rendered.contains("<strong>*</strong>"));
    }

    private String renderTemplate(String template, RefactorFirstReportDTO data) throws Exception {
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache mustache = mf.compile(new StringReader(template), "test");
        StringWriter writer = new StringWriter();
        mustache.execute(writer, data).flush();
        return writer.toString();
    }
}
