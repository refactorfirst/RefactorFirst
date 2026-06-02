package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.git.ScmLogInfo;
import org.hjug.graphbuilder.metrics.DisharmonyMetric;
import org.hjug.graphbuilder.metrics.DisharmonyMetric.Direction;
import org.hjug.metrics.DisharmonyInstance;
import org.junit.jupiter.api.Test;

class DisharmonyRenderingTest {

    private final SimpleHtmlReport simpleReport = new SimpleHtmlReport();
    private final HtmlReport htmlReport = new HtmlReport();

    // ── table rendering ────────────────────────────────────────────────────────

    @Test
    void renderDisharmonyInfoContainsTitle() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("BrainClass.java", null, 1, 57.0, 3.0, 0.3));

        String html = simpleReport.renderDisharmonyInfo("BRAIN", "Brain Classes", false, false, ranked);

        assertTrue(html.contains("Brain Classes"), "HTML must contain the section title");
        assertTrue(html.contains("id=\"BRAIN\""), "HTML must contain the anchor id");
    }

    @Test
    void simpleModeShowsDescriptionColumnNotMetricColumns() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("BrainClass.java", null, 1, 57.0, 3.0, 0.3));
        ranked.get(0).setDescription("Brain Class detected: Brain Methods=1, LOC=200, WMC=3, TCC=0.3");

        String html = simpleReport.renderDisharmonyInfo("BRAIN", "Brain Classes", false, false, ranked);

        assertTrue(html.contains("<table"), "HTML must contain a table");
        assertTrue(html.contains("Description"), "Simple view must show the Description column");
        assertFalse(html.contains("<th>BrainMethods</th>"), "Simple view must not show raw metric columns");
        assertFalse(html.contains("<th>WMC</th>"), "Simple view must not show raw metric columns");
    }

    @Test
    void renderDisharmonyInfoShowsDetailedColumnsWhenRequested() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("BrainClass.java", null, 1, 57.0, 3.0, 0.3));

        String simple = simpleReport.renderDisharmonyInfo("BRAIN", "Brain Classes", false, false, ranked);
        String detailed = simpleReport.renderDisharmonyInfo("BRAIN", "Brain Classes", false, true, ranked);

        assertFalse(simple.contains("BrainMethods Rank"), "Simple mode should not show rank columns");
        assertFalse(simple.contains("<th>BrainMethods</th>"), "Simple mode should not show metric value columns");
        assertTrue(detailed.contains("BrainMethods Rank"), "Detailed mode must show metric rank columns");
        assertTrue(detailed.contains("<th>BrainMethods</th>"), "Detailed mode must show metric value columns");
        assertTrue(detailed.contains("Raw Priority"), "Detailed mode must show Raw Priority");
        assertTrue(detailed.contains("Full Path"), "Detailed mode must show Full Path");
    }

    @Test
    void renderDisharmonyInfoForMethodLevelShowsMethodColumn() {
        List<RankedDisharmony> ranked =
                List.of(makeRankedDisharmony("BrainClass.java", "heavyMethod()", 1, 70.0, 5.0, 5.0));

        String html = simpleReport.renderDisharmonyInfo("BRAIN_METHOD", "Brain Methods", true, false, ranked);

        assertTrue(html.contains("Method"), "Method-level rendering must include a Method column header");
        assertTrue(html.contains("heavyMethod()"), "Method-level rendering must include the method signature");
    }

    @Test
    void renderDisharmonyInfoForClassLevelDoesNotShowMethodColumn() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("BrainClass.java", null, 1, 57.0, 3.0, 0.3));

        String html = simpleReport.renderDisharmonyInfo("BRAIN", "Brain Classes", false, false, ranked);

        // Class-level should not have an empty method cell (null signature)
        assertFalse(html.contains("null"), "Class-level rendering must not have null method signature cells");
    }

    // ── chart rendering in HtmlReport ─────────────────────────────────────────

    @Test
    void renderDisharmonyChartInSimpleReportIsEmpty() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("BrainClass.java", null, 1, 57.0, 3.0, 0.3));

        String chart = simpleReport.renderDisharmonyChart("BRAIN", "Brain Classes", ranked, 1);

        assertEquals("", chart, "SimpleHtmlReport.renderDisharmonyChart must return empty string");
    }

    @Test
    void renderDisharmonyChartInHtmlReportContainsDivAndScript() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("BrainClass.java", null, 1, 57.0, 3.0, 0.3));

        String chart = htmlReport.renderDisharmonyChart("BRAIN", "Brain Classes", ranked, 1);

        assertTrue(chart.contains("<div"), "HtmlReport chart must contain a div element");
        assertTrue(chart.contains("<script"), "HtmlReport chart must contain a script element");
        assertTrue(chart.contains("BRAIN") || chart.contains("brain"), "chart must reference the anchor/slug");
    }

    @Test
    void twoDisharmonyTypeChartsHaveUniqueIds() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("BrainClass.java", null, 1, 57.0, 3.0, 0.3));

        String brainChart = htmlReport.renderDisharmonyChart("BRAIN", "Brain Classes", ranked, 1);
        String feChart = htmlReport.renderDisharmonyChart("FEATURE_ENVY", "Feature Envy", ranked, 1);

        // The div id or function name must differ
        assertNotEquals(brainChart, feChart, "Charts for different types must differ");
        assertFalse(
                brainChart.contains("FEATURE_ENVY") || brainChart.contains("feature_envy"),
                "Brain class chart must not reference Feature Envy slug");
    }

    // ── Duplicate Partners column ──────────────────────────────────────────────

    @Test
    void significantDuplicationTableShowsDuplicatePartnersColumn() {
        RankedDisharmony rd = makeRankedDisharmony("DupClass.java", null, 1, 7.0, 14.0, 0.0);
        rd.setDuplicationPartners("computeResult(int) ↔ CrossClassB.computeResult(int)");

        String html =
                simpleReport.renderDisharmonyInfo("SIG_DUP", "Significant Duplication", false, false, List.of(rd));

        assertTrue(html.contains("Duplicate Partners"), "Table must show 'Duplicate Partners' column header");
        assertTrue(html.contains("CrossClassB"), "Table must show partner class name in the Duplicate Partners cell");
    }

    @Test
    void otherDisharmonyTableOmitsDuplicatePartnersColumn() {
        RankedDisharmony rd = makeRankedDisharmony("BrainClass.java", null, 1, 57.0, 3.0, 0.3);

        String html = simpleReport.renderDisharmonyInfo("BRAIN", "Brain Classes", false, false, List.of(rd));

        assertFalse(html.contains("Duplicate Partners"), "Non-duplication table must not show 'Duplicate Partners'");
    }

    // ── helper ─────────────────────────────────────────────────────────────────

    private RankedDisharmony makeRankedDisharmony(
            String fileName, String methodSignature, int priority, double metric1, double metric2, double metric3) {
        List<DisharmonyMetric> metrics = new java.util.ArrayList<>();
        metrics.add(new DisharmonyMetric("BrainMethods", metric1, Direction.ASCENDING));
        metrics.add(new DisharmonyMetric("LOC", 200.0, Direction.ASCENDING));
        metrics.add(new DisharmonyMetric("WMC", metric2, Direction.ASCENDING));
        metrics.add(new DisharmonyMetric("TCC", metric3, Direction.DESCENDING));
        // Set ranks on metrics
        for (int i = 0; i < metrics.size(); i++) {
            metrics.get(i).setRank(i + 1);
        }

        DisharmonyInstance instance = new DisharmonyInstance(
                "Brain Class", "com.example.BrainClass", fileName, "com.example", methodSignature, metrics);
        instance.setSumOfRanks(10);
        instance.setOverallRank(priority);

        ScmLogInfo scmLogInfo = new ScmLogInfo(fileName, "com.example.BrainClass", 1000000, 1000001, 5);
        scmLogInfo.setChangePronenessRank(3);

        RankedDisharmony rd = new RankedDisharmony(instance, scmLogInfo);
        rd.setPriority(priority);
        return rd;
    }
}
