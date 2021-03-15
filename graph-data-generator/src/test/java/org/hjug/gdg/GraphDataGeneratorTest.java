package org.hjug.gdg;

import org.hjug.cbc.CostBenefitCalculator;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class GraphDataGeneratorTest {

    private GraphDataGenerator graphDataGenerator;
    private CostBenefitCalculator costBenefitCalculator = mock(CostBenefitCalculator.class);

    @Before
    public void setUp() {
        graphDataGenerator = new GraphDataGenerator(costBenefitCalculator);
    }

    @Test
    public void getScriptStart() {
        String scriptStart =
                        "      google.charts.load('current', {'packages':['corechart']});\n" +
                        "      google.charts.setOnLoadCallback(drawSeriesChart);\n" +
                        "\n" +
                        "    function drawSeriesChart() {\n" +
                        "\n" +
                        "      var data = google.visualization.arrayToDataTable([";
        assertEquals(scriptStart, graphDataGenerator.getScriptStart());
    }

    @Test
    public void getScriptEnd() {
        String scriptEnd =
                        "]);\n" +
                        "\n" +
                        "      var options = {\n" +
                        "        title: 'Priority Ranking for Refactoring God Classes - ' +\n" +
                        "               'Fix Higher Priority Classes First',\n" +
                        "        height: 900, " +
                        "        width: 1200, " +
                        "        hAxis: {title: 'Effort'},\n" +
                        "        vAxis: {title: 'Change Proneness'},\n" +
                        "        colorAxis: {colors: ['blue', 'green']},\n" +
                        "        bubble: {textStyle: {fontSize: 11}}      };\n" +
                        "\n" +
                        "      var chart = new google.visualization.BubbleChart(document.getElementById('series_chart_div'));\n" +
                        "      chart.draw(data, options);\n" +
                        "    }\n";

        assertEquals(scriptEnd, graphDataGenerator.getScriptEnd());
    }

    @Test
    public void generateBubbleChartData() {
    }

    @Test
    public void getRankedDisharmonies() {
    }
}