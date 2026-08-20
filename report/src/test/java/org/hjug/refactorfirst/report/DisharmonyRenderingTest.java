package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.git.ScmLogInfo;
import org.hjug.graphbuilder.metrics.DisharmonyMetric;
import org.hjug.graphbuilder.metrics.DisharmonyMetric.Direction;
import org.hjug.graphbuilder.metrics.DisharmonyTypes;
import org.hjug.metrics.DisharmonyInstance;
import org.junit.jupiter.api.Test;

class DisharmonyRenderingTest {

    private final SimpleHtmlReport simpleReport = new SimpleHtmlReport();
    private final HtmlReport htmlReport = new HtmlReport();

    // ── table rendering ────────────────────────────────────────────────────────

    @Test
    void renderDisharmonyInfoContainsTitle() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("BrainClass.java", null, 1, 57.0, 3.0, 0.3));

        SimpleHtmlReport.DisharmonySpec spec = new SimpleHtmlReport.DisharmonySpec(
                DisharmonyTypes.BRAIN_CLASS,
                "BRAIN_CLASS",
                "Brain Classes",
                false,
                "Brain Classes are complex, lack cohesion, and have at least one Brain Method.",
                "Decompose Brain Methods into smaller methods.");
        String html = simpleReport.renderDisharmonyInfo("", spec, false, ranked);

        assertTrue(html.contains("Brain Classes"), "HTML must contain the section title");
        assertTrue(html.contains("id=\"BRAIN_CLASS\""), "HTML must contain the anchor id");
    }

    @Test
    void simpleModeShowsDescriptionColumnNotMetricColumns() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("BrainClass.java", null, 1, 57.0, 3.0, 0.3));
        ranked.get(0).setDescription("Brain Class detected: Brain Methods=1, LOC=200, WMC=3, TCC=0.3");

        SimpleHtmlReport.DisharmonySpec spec = new SimpleHtmlReport.DisharmonySpec(
                DisharmonyTypes.BRAIN_CLASS,
                "BRAIN_CLASS",
                "Brain Classes",
                false,
                "Brain Classes are complex, lack cohesion, and have at least one Brain Method.",
                "Decompose Brain Methods into smaller methods.");
        String html = simpleReport.renderDisharmonyInfo("", spec, false, ranked);

        assertTrue(html.contains("<table"), "HTML must contain a table");
        assertFalse(html.contains("Description"), "Simple view must not show the Description column");
        assertFalse(html.contains("<th>BrainMethods</th>"), "Simple view must not show raw metric columns");
        assertFalse(html.contains("<th>WMC</th>"), "Simple view must not show raw metric columns");
    }

    @Test
    void renderDisharmonyInfoShowsDetailedColumnsWhenRequested() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("BrainClass.java", null, 1, 57.0, 3.0, 0.3));

        SimpleHtmlReport.DisharmonySpec spec = new SimpleHtmlReport.DisharmonySpec(
                DisharmonyTypes.BRAIN_CLASS,
                "BRAIN_CLASS",
                "Brain Classes",
                false,
                "Brain Classes are complex, lack cohesion, and have at least one Brain Method.",
                "Decompose Brain Methods into smaller methods.");
        String simple = simpleReport.renderDisharmonyInfo("", spec, false, ranked);
        String detailed = simpleReport.renderDisharmonyInfo("", spec, true, ranked);

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

        SimpleHtmlReport.DisharmonySpec spec = new SimpleHtmlReport.DisharmonySpec(
                DisharmonyTypes.BRAIN_METHOD,
                "BRAIN_METHOD",
                "Brain Method",
                true,
                "Method is long, complicated, and uses many variables.",
                "- Decompose the method into two or more smaller methods.<br>"
                        + "- If part of the method relies heavily on an outside class, extract that functionality out of the calling method and move it to the called class.");
        String html = simpleReport.renderDisharmonyInfo("", spec, false, ranked);

        assertTrue(html.contains("Method"), "Method-level rendering must include a Method column header");
        assertTrue(html.contains("heavyMethod()"), "Method-level rendering must include the method signature");
    }

    @Test
    void renderDisharmonyInfoForClassLevelDoesNotShowMethodColumn() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("BrainClass.java", null, 1, 57.0, 3.0, 0.3));

        SimpleHtmlReport.DisharmonySpec spec = new SimpleHtmlReport.DisharmonySpec(
                DisharmonyTypes.BRAIN_CLASS,
                "BRAIN_CLASS",
                "Brain Classes",
                false,
                "Brain Classes are complex, lack cohesion, and have at least one Brain Method.",
                "Decompose Brain Methods into smaller methods.");
        String html = simpleReport.renderDisharmonyInfo("", spec, false, ranked);

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

        SimpleHtmlReport.DisharmonySpec spec = new SimpleHtmlReport.DisharmonySpec(
                DisharmonyTypes.BRAIN_CLASS,
                "SIG_DUP",
                "Significant Duplication",
                false,
                "Nearly identical code is found in multiple classes, leading to increased maintenance costs.",
                "- Move duplicated code in the same class into a new method.<br>"
                        + "- Move duplicated code into a separate or parent class.<br>"
                        + "- Move duplicated code in two child classes or in parent/child classes into the parent class.");
        String html = simpleReport.renderDisharmonyInfo("", spec, false, List.of(rd));

        assertTrue(html.contains("Duplicate Partners"), "Table must show 'Duplicate Partners' column header");
        assertTrue(html.contains("CrossClassB"), "Table must show partner class name in the Duplicate Partners cell");
    }

    @Test
    void otherDisharmonyTableOmitsDuplicatePartnersColumn() {
        RankedDisharmony rd = makeRankedDisharmony("BrainClass.java", null, 1, 57.0, 3.0, 0.3);

        SimpleHtmlReport.DisharmonySpec spec = new SimpleHtmlReport.DisharmonySpec(
                DisharmonyTypes.BRAIN_CLASS,
                "BRAIN_CLASS",
                "Brain Classes",
                false,
                "Brain Classes are complex, lack cohesion, and have at least one Brain Method.",
                "Decompose Brain Methods into smaller methods.");
        String html = simpleReport.renderDisharmonyInfo("", spec, false, List.of(rd));

        assertFalse(html.contains("Duplicate Partners"), "Non-duplication table must not show 'Duplicate Partners'");
    }

    // ── Kotlin-specific disharmony types ─────────────────────────

    @Test
    void excessiveExtensionsRendersInReport() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("ExtensionHost.java", null, 1, 15.0, 5.0, 0.0));

        SimpleHtmlReport.DisharmonySpec spec = new SimpleHtmlReport.DisharmonySpec(
                DisharmonyTypes.EXCESSIVE_EXTENSIONS,
                "EXCESSIVE_EXTENSIONS",
                "Excessive Extensions",
                false,
                "Class declares many extension functions across many receiver types, indicating it's trying to extend too many unrelated types.",
                "Consider moving extension functions closer to the types they extend. Group related extensions into separate files or classes.");
        String html = simpleReport.renderDisharmonyInfo("", spec, false, ranked);

        assertTrue(html.contains("Excessive Extensions"), "Report should contain Excessive Extensions title");
        assertTrue(html.contains("id=\"EXCESSIVE_EXTENSIONS\""), "Report should have Excessive Extensions anchor");
        assertTrue(
                html.contains("Class declares many extension functions"), "Report should contain problem description");
        assertTrue(html.contains("Consider moving extension functions"), "Report should contain solution");
        // Verify no method column (class-level)
        assertFalse(html.contains("<th>Method</th>"), "Class-level rendering must not have Method column");
    }

    @Test
    void largeSealedHierarchyRendersInReport() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("Shape.java", null, 1, 12.0, 0.0, 0.0));

        SimpleHtmlReport.DisharmonySpec spec = new SimpleHtmlReport.DisharmonySpec(
                DisharmonyTypes.LARGE_SEALED_HIERARCHY,
                "LARGE_SEALED_HIERARCHY",
                "Large Sealed Hierarchy",
                false,
                "Sealed class has many permitted subtypes, making the hierarchy hard to maintain and exhaustive when expressions unwieldy.",
                "Re-evaluate the domain model. Consider grouping subtypes into intermediate sealed classes or using a different pattern.");
        String html = simpleReport.renderDisharmonyInfo("", spec, false, ranked);

        assertTrue(html.contains("Large Sealed Hierarchy"), "Report should contain Large Sealed Hierarchy title");
        assertTrue(html.contains("id=\"LARGE_SEALED_HIERARCHY\""), "Report should have Large Sealed Hierarchy anchor");
        assertTrue(
                html.contains("Sealed class has many permitted subtypes"), "Report should contain problem description");
        assertTrue(html.contains("Re-evaluate the domain model"), "Report should contain solution");
        assertFalse(html.contains("<th>Method</th>"), "Class-level rendering must not have Method column");
    }

    @Test
    void dataClassWithLogicRendersInReport() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("Money.java", null, 1, 14.0, 3.0, 0.0));

        SimpleHtmlReport.DisharmonySpec spec = new SimpleHtmlReport.DisharmonySpec(
                DisharmonyTypes.DATA_CLASS_WITH_LOGIC,
                "DATA_CLASS_WITH_LOGIC",
                "Data Class with Logic",
                false,
                "Data class contains non-accessor methods with business logic, violating the data carrier principle.",
                "Move business logic to separate service classes. Keep data classes as pure data holders with only accessor methods.");
        String html = simpleReport.renderDisharmonyInfo("", spec, false, ranked);

        assertTrue(html.contains("Data Class with Logic"), "Report should contain Data Class with Logic title");
        assertTrue(html.contains("id=\"DATA_CLASS_WITH_LOGIC\""), "Report should have Data Class with Logic anchor");
        assertTrue(
                html.contains("Data class contains non-accessor methods"), "Report should contain problem description");
        assertTrue(html.contains("Move business logic to separate service classes"), "Report should contain solution");
        assertFalse(html.contains("<th>Method</th>"), "Class-level rendering must not have Method column");
    }

    @Test
    void newKotlinDisharmoniesShowMetricsInDetailedMode() {
        List<RankedDisharmony> ranked = List.of(makeRankedDisharmony("Test.java", null, 1, 10.0, 5.0, 0.5));

        // Test each type in detailed mode
        for (var spec : List.of(
                new SimpleHtmlReport.DisharmonySpec(
                        DisharmonyTypes.EXCESSIVE_EXTENSIONS,
                        "EXCESSIVE_EXTENSIONS",
                        "Excessive Extensions",
                        false,
                        "p",
                        "s"),
                new SimpleHtmlReport.DisharmonySpec(
                        DisharmonyTypes.LARGE_SEALED_HIERARCHY,
                        "LARGE_SEALED_HIERARCHY",
                        "Large Sealed Hierarchy",
                        false,
                        "p",
                        "s"),
                new SimpleHtmlReport.DisharmonySpec(
                        DisharmonyTypes.DATA_CLASS_WITH_LOGIC,
                        "DATA_CLASS_WITH_LOGIC",
                        "Data Class with Logic",
                        false,
                        "p",
                        "s"))) {
            String detailed = simpleReport.renderDisharmonyInfo("", spec, true, ranked);
            assertTrue(detailed.contains("Raw Priority"), "Detailed mode must show Raw Priority for " + spec.title());
            assertTrue(detailed.contains("Full Path"), "Detailed mode must show Full Path for " + spec.title());
        }
    }

    // ── helper ─────────────────────────────────────────────────────────────────

    private RankedDisharmony makeRankedDisharmony(
            String fileName, String methodSignature, int priority, double metric1, double metric2, double metric3) {
        List<DisharmonyMetric> metrics = new ArrayList<>();
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
