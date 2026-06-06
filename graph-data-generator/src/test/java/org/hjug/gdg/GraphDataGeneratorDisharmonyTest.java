package org.hjug.gdg;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.git.ScmLogInfo;
import org.hjug.metrics.GodClass;
import org.junit.jupiter.api.Test;

class GraphDataGeneratorDisharmonyTest {

    private final GraphDataGenerator gen = new GraphDataGenerator();

    // ── parameterized script start ─────────────────────────────────────────────

    @Test
    void disharmonyScriptStartContainsFunctionNameAndDataVar() {
        String start = gen.getDisharmonyScriptStart("drawBrainClass", "dataBrainClass");

        assertTrue(start.contains("drawBrainClass"), "script start must contain the function name");
        assertTrue(start.contains("dataBrainClass"), "script start must contain the data variable");
        assertTrue(start.contains("google.charts.load"), "script start must load Google Charts");
        assertTrue(start.contains("arrayToDataTable"), "script start must open the data table");
    }

    @Test
    void twoDisharmonyTypesHaveUniqueScriptStarts() {
        String s1 = gen.getDisharmonyScriptStart("drawBrainClass", "dataBrainClass");
        String s2 = gen.getDisharmonyScriptStart("drawFeatureEnvy", "dataFeatureEnvy");

        assertNotEquals(s1, s2, "script starts for different types must differ");
        assertTrue(s1.contains("drawBrainClass"));
        assertTrue(s2.contains("drawFeatureEnvy"));
    }

    // ── parameterized script end ───────────────────────────────────────────────

    @Test
    void disharmonyScriptEndContainsAllParameterizedParts() {
        String end = gen.getDisharmonyScriptEnd(
                "drawBrainClass",
                "chartBrainClass",
                "chart_div_brain_class",
                "dataBrainClass",
                "Brain Class Priority Ranking",
                "Effort");

        assertTrue(end.contains("chartBrainClass"), "script end must reference the chart variable");
        assertTrue(end.contains("chart_div_brain_class"), "script end must reference the div id");
        assertTrue(end.contains("dataBrainClass"), "script end must reference the data variable");
        assertTrue(end.contains("Brain Class Priority Ranking"), "script end must contain the title");
        assertTrue(end.contains("Effort"), "script end must contain the x-axis label");
        assertTrue(end.contains("BubbleChart"), "script end must create a BubbleChart");
    }

    @Test
    void twoDisharmonyTypesHaveUniqueScriptEnds() {
        String e1 = gen.getDisharmonyScriptEnd(
                "drawBrainClass", "chartBrainClass", "div_brain_class", "dataBrainClass", "Brain Class", "Effort");
        String e2 = gen.getDisharmonyScriptEnd(
                "drawFeatureEnvy", "chartFeatureEnvy", "div_feature_envy", "dataFeatureEnvy", "Feature Envy", "Effort");

        assertNotEquals(e1, e2, "script ends for different types must differ");
        assertFalse(e1.contains("div_feature_envy"), "brain-class script must not reference feature-envy div");
        assertFalse(e2.contains("div_brain_class"), "feature-envy script must not reference brain-class div");
    }

    // ── parameterized bubble chart data ───────────────────────────────────────

    @Test
    void generateBubbleChartDataForTwoPoints() {
        RankedDisharmony rd1 = makeRankedDisharmony(1);
        RankedDisharmony rd2 = makeRankedDisharmony(2);

        String data = gen.generateBubbleChartData(List.of(rd1, rd2), 2, "Effort");

        assertTrue(data.contains(","), "two data points must be comma-separated");
        assertTrue(data.startsWith("[ 'ID', 'Effort', 'Change Proneness', 'Priority', 'Priority (Visual)']"));
    }

    // ── helper ─────────────────────────────────────────────────────────────────

    private RankedDisharmony makeRankedDisharmony(int priority) {
        GodClass gc = new GodClass("SomeClass", "SomeClass.java", "com.example", "ATFD=10, WMC=50, TCC=0.1");
        gc.setOverallRank(0);
        ScmLogInfo info = new ScmLogInfo("SomeClass.java", null, 1000000, 0, 1);
        info.setChangePronenessRank(0);
        RankedDisharmony rd = new RankedDisharmony(gc, info);
        rd.setPriority(priority);
        return rd;
    }
}
