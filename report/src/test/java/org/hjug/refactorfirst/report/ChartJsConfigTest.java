package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.InputStream;
import org.hjug.refactorfirst.report.model.ChartJsBubbleDTO;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T4.2: the rf-data block exposes the Chart.js contract the page repo consumes —
 * {@code disharmonies[n].chart} with {@code canvasId}, the {@code xaxisLabel}/{@code yaxisLabel}
 * defaults ("Effort to refactor" / "Relative churn (impact)"; the viewer appends U+27F6), and
 * bubbles {@code {x, y, r, color, borderColor, label, priority, path}} whose colors come from the
 * single-source {@link ReportModelBuilder#createBubble} — pinned against the page repo's
 * junit4-report.json fixture (e.g. priority 2 of 14 → {@code rgba(220, 72, 55, 0.75)}).
 */
class ChartJsConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @DisplayName("createBubble reproduces every parity fixture bubble, including the color ramp")
    @Test
    void createBubbleMatchesParityFixtureGoldens() throws Exception {
        // Given: the page repo's junit4 fixture loaded through the DTO pipeline
        RefactorFirstReportDTO dto = loadFixtureDto();

        // When: every fixture chart bubble is rebuilt through the single-source createBubble
        for (var disharmony : dto.getDisharmonies()) {
            var chart = disharmony.getChart();
            assertNotNull(chart, "fixture sections carry charts");
            int maxPriority = chart.getBubbles().stream()
                    .mapToInt(ChartJsBubbleDTO::getPriority)
                    .max()
                    .orElse(0);
            for (ChartJsBubbleDTO bubble : chart.getBubbles()) {
                ChartJsBubbleDTO rebuilt = ReportModelBuilder.createBubble(
                        bubble.getId(),
                        bubble.getLabel(),
                        bubble.getEffortRank(),
                        bubble.getChangePronenessRank(),
                        bubble.getPriority(),
                        maxPriority,
                        bubble.getPath());

                // Then: every field, including the priority color ramp, matches the fixture
                assertEquals(bubble.getId(), rebuilt.getId(), bubble.getLabel());
                assertEquals(bubble.getLabel(), rebuilt.getLabel(), bubble.getLabel());
                assertEquals(bubble.getX(), rebuilt.getX(), bubble.getLabel());
                assertEquals(bubble.getY(), rebuilt.getY(), bubble.getLabel());
                assertEquals(bubble.getR(), rebuilt.getR(), bubble.getLabel());
                assertEquals(bubble.getPriority(), rebuilt.getPriority(), bubble.getLabel());
                assertEquals(bubble.getPath(), rebuilt.getPath(), bubble.getLabel());
                assertEquals(bubble.getColor(), rebuilt.getColor(), bubble.getLabel());
                assertEquals(bubble.getBorderColor(), rebuilt.getBorderColor(), bubble.getLabel());
            }
        }
    }

    @DisplayName("rf-data block exposes the page-repo chart contract with exact axis label strings")
    @Test
    void rfDataBlockExposesChartContract() throws Exception {
        // Given: the parity fixture DTO turned into the rf-data payload
        RefactorFirstReportDTO dto = loadFixtureDto();
        String rfData = new RfDataBlock().build(dto);
        JsonNode root = objectMapper.readTree(rfData);

        // Then: the chart contract keys match the page repo's report data shape
        JsonNode chart = root.get("disharmonies").get(0).get("chart");
        assertEquals("chart_GOD", chart.get("canvasId").asText());
        assertEquals("Effort to refactor", chart.get("xaxisLabel").asText());
        assertEquals("Relative churn (impact)", chart.get("yaxisLabel").asText());

        // And: the bubbles carry the Chart.js fields, with the priority 2 of 14 ramp golden
        JsonNode rpbChart = root.get("disharmonies").get(2).get("chart");
        JsonNode bubble = null;
        for (JsonNode candidate : rpbChart.get("bubbles")) {
            if (candidate.get("priority").asInt() == 2) {
                bubble = candidate;
                break;
            }
        }
        assertNotNull(bubble, "fixture RPB section carries a priority 2 bubble");
        assertEquals("rgba(220, 72, 55, 0.75)", bubble.get("color").asText());
        assertEquals("rgb(220, 72, 55)", bubble.get("borderColor").asText());
        assertEquals(2, bubble.get("priority").asInt());
        assertNotNull(bubble.get("path").asText());

        // And: the payload is HTML-script safe (no raw closing-tag sequence)
        assertEquals(false, rfData.contains("</"), rfData);
    }

    private RefactorFirstReportDTO loadFixtureDto() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/parity/junit4-report.json")) {
            if (is == null) {
                throw new IllegalStateException("Parity fixture not found in classpath: /parity/junit4-report.json");
            }
            return objectMapper.readValue(is, RefactorFirstReportDTO.class);
        }
    }
}
