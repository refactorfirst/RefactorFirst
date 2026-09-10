package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.hjug.refactorfirst.report.model.*;
import org.junit.jupiter.api.Test;

class ReportDataSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testProjectMetadataSerialization() throws Exception {
        ProjectMetadataDTO project = ProjectMetadataDTO.builder()
                .name("JUnit")
                .version("4.13.3-SNAPSHOT")
                .repoUrl("https://github.com/junit-team/junit4/blob/main/")
                .baseDir("/repo")
                .scanTimestamp("9/8/26, 7:34 PM")
                .hasAnyDisharmony(true)
                .build();

        RefactorFirstReportDTO report =
                RefactorFirstReportDTO.builder().project(project).build();

        String json = objectMapper.writeValueAsString(report);
        assertTrue(json.contains("\"name\":\"JUnit\""));
        assertTrue(json.contains("\"version\":\"4.13.3-SNAPSHOT\""));
        assertTrue(json.contains("\"hasAnyDisharmony\":true"));

        RefactorFirstReportDTO deserialized = objectMapper.readValue(json, RefactorFirstReportDTO.class);
        assertEquals("JUnit", deserialized.getProject().getName());
        assertEquals("4.13.3-SNAPSHOT", deserialized.getProject().getVersion());
        assertTrue(deserialized.getProject().isHasAnyDisharmony());
    }

    @Test
    void testDisharmonyBubbleChartSerialization() throws Exception {
        ChartJsBubbleDTO bubble = ChartJsBubbleDTO.builder()
                .id("ComparisonCompactor")
                .label("ComparisonCompactor.java")
                .x(2)
                .y(14)
                .r(18)
                .priority(1)
                .effortRank(2)
                .changePronenessRank(14)
                .color("rgba(235, 64, 52, 0.75)")
                .borderColor("rgb(235, 64, 52)")
                .build();

        DisharmonyChartDTO chart = DisharmonyChartDTO.builder()
                .canvasId("chart_god")
                .xAxisLabel("Effort to refactor")
                .yAxisLabel("Relative churn")
                .bubbles(List.of(bubble))
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
                .build();

        RefactorFirstReportDTO report =
                RefactorFirstReportDTO.builder().disharmonies(List.of(section)).build();

        String json = objectMapper.writeValueAsString(report);
        assertTrue(json.contains("\"anchorId\":\"GOD\""));
        assertTrue(json.contains("\"bubbles\":["));
        assertTrue(json.contains("\"priority\":1"));
        assertTrue(json.contains("\"r\":18"));

        RefactorFirstReportDTO deserialized = objectMapper.readValue(json, RefactorFirstReportDTO.class);
        assertEquals(1, deserialized.getDisharmonies().size());
        assertEquals(
                18,
                deserialized
                        .getDisharmonies()
                        .get(0)
                        .getChart()
                        .getBubbles()
                        .get(0)
                        .getR());
    }

    @Test
    void testTableRowsSerialization() throws Exception {
        DisharmonyTableCellDTO cell1 = DisharmonyTableCellDTO.builder()
                .content("<a href=\"https://github.com/foo/Bar.java\">Bar.java</a>")
                .align("left")
                .build();
        DisharmonyTableCellDTO cell2 =
                DisharmonyTableCellDTO.builder().content("1").align("right").build();

        DisharmonyTableRowDTO row =
                DisharmonyTableRowDTO.builder().cells(List.of(cell1, cell2)).build();

        DisharmonyTableDTO table = DisharmonyTableDTO.builder()
                .headers(List.of("Class", "Priority"))
                .rows(List.of(row))
                .build();

        DisharmonySectionDTO section = DisharmonySectionDTO.builder()
                .type("Data Class")
                .anchorId("DATA_CLASS")
                .title("Data Classes")
                .table(table)
                .build();

        RefactorFirstReportDTO report =
                RefactorFirstReportDTO.builder().disharmonies(List.of(section)).build();

        String json = objectMapper.writeValueAsString(report);
        assertTrue(json.contains("<a href=\\\"https://github.com/foo/Bar.java\\\">Bar.java</a>"));

        RefactorFirstReportDTO deserialized = objectMapper.readValue(json, RefactorFirstReportDTO.class);
        assertEquals(
                "<a href=\"https://github.com/foo/Bar.java\">Bar.java</a>",
                deserialized
                        .getDisharmonies()
                        .get(0)
                        .getTable()
                        .getRows()
                        .get(0)
                        .getCells()
                        .get(0)
                        .getContent());
    }

    @Test
    void testEmptyReportSerialization() throws Exception {
        ProjectMetadataDTO project = ProjectMetadataDTO.builder()
                .name("CleanProject")
                .version("1.0.0")
                .repoUrl("https://github.com/clean/clean")
                .baseDir("/clean")
                .scanTimestamp("9/8/26, 7:34 PM")
                .hasAnyDisharmony(false)
                .build();

        RefactorFirstReportDTO report = RefactorFirstReportDTO.builder()
                .project(project)
                .disharmonies(List.of())
                .build();

        String json = objectMapper.writeValueAsString(report);
        assertTrue(json.contains("\"hasAnyDisharmony\":false"));
        RefactorFirstReportDTO deserialized = objectMapper.readValue(json, RefactorFirstReportDTO.class);
        assertFalse(deserialized.getProject().isHasAnyDisharmony());
        assertTrue(deserialized.getDisharmonies().isEmpty());
    }
}
