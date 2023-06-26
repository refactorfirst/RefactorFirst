package org.hjug.gdg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.git.ScmLogInfo;
import org.hjug.metrics.GodClass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphDataGeneratorTest {

    private GraphDataGenerator graphDataGenerator;

    @BeforeEach
    public void setUp() {
        graphDataGenerator = new GraphDataGenerator();
    }

    @Test
    void getScriptStart() {
        String scriptStart = "      google.charts.load('current', {'packages':['corechart']});\n"
                + "      google.charts.setOnLoadCallback(drawSeriesChart);\n"
                + "\n"
                + "    function drawSeriesChart() {\n"
                + "\n"
                + "      var data = google.visualization.arrayToDataTable([";
        assertEquals(scriptStart, graphDataGenerator.getGodClassScriptStart());
    }

    @Test
    void getScriptEnd() {
        String scriptEnd = "]);\n" + "\n"
                + "      var options = {\n"
                + "        title: 'Priority Ranking for Refactoring God Classes - ' +\n"
                + "               'Start with Priority 1',\n"
                + "        height: 900, "
                + "        width: 1200, "
                + "        explorer: {}, "
                + "        hAxis: {title: 'Effort'},\n"
                + "        vAxis: {title: 'Change Proneness'},\n"
                + "        colorAxis: {colors: ['green', 'red']},\n"
                + "        bubble: {textStyle: {fontSize: 11}}      };\n"
                + "\n"
                + "      var chart = new google.visualization.BubbleChart(document.getElementById('series_chart_div'));\n"
                + "      chart.draw(data, options);\n"
                + "    }\n";

        assertEquals(scriptEnd, graphDataGenerator.getGodClassScriptEnd());
    }

    @Test
    void generateBubbleChartDataOneDataPoint() {
        GodClass godClass = new GodClass(
                "AttributeHandler",
                "AttributeHandler.java",
                "org.apache.myfaces.tobago.facelets",
                "(WMC=77, ATFD=105, TCC=15.555999755859375)");
        godClass.setOverallRank(0);
        ScmLogInfo scmLogInfo =
                new ScmLogInfo("org/apache/myfaces/tobago/facelets/AttributeHandler.java", 1595275997, 0, 1);
        scmLogInfo.setChangePronenessRank(0);
        RankedDisharmony rankedDisharmony = new RankedDisharmony(godClass, scmLogInfo);
        rankedDisharmony.setPriority(1);

        List<RankedDisharmony> rankedDisharmonies = new ArrayList<>();
        rankedDisharmonies.add(rankedDisharmony);

        String chartData = "[ 'ID', 'Effort', 'Change Proneness', 'Priority', 'Priority (Visual)'], "
                + "['AttributeHandler.java',0,0,1,0]";
        Assertions.assertEquals(chartData, graphDataGenerator.generateGodClassBubbleChartData(rankedDisharmonies, 1));
    }

    // Only testing correct string formatting, not data correctness
    @Test
    void generateBubbleChartDataTwoDataPoints() {
        GodClass godClass = new GodClass(
                "AttributeHandler",
                "AttributeHandler.java",
                "org.apache.myfaces.tobago.facelets",
                "(WMC=77, ATFD=105, TCC=15.555999755859375)");
        godClass.setOverallRank(0);
        ScmLogInfo scmLogInfo =
                new ScmLogInfo("org/apache/myfaces/tobago/facelets/AttributeHandler.java", 1595275997, 0, 1);
        scmLogInfo.setChangePronenessRank(0);
        RankedDisharmony rankedDisharmony = new RankedDisharmony(godClass, scmLogInfo);
        rankedDisharmony.setPriority(1);
        RankedDisharmony rankedDisharmony2 = new RankedDisharmony(godClass, scmLogInfo);
        rankedDisharmony2.setPriority(2);

        List<RankedDisharmony> rankedDisharmonies = new ArrayList<>();
        rankedDisharmonies.add(rankedDisharmony);
        rankedDisharmonies.add(rankedDisharmony2);

        String chartData = "[ 'ID', 'Effort', 'Change Proneness', 'Priority', 'Priority (Visual)'], "
                + "['AttributeHandler.java',0,0,1,0],"
                + "['AttributeHandler.java',0,0,2,0]";
        Assertions.assertEquals(chartData, graphDataGenerator.generateGodClassBubbleChartData(rankedDisharmonies, 1));
    }
}
