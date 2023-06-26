package org.hjug.gdg;

import java.util.List;
import org.hjug.cbc.RankedDisharmony;

public class GraphDataGenerator {

    public String getGodClassScriptStart() {
        return "      google.charts.load('current', {'packages':['corechart']});\n"
                + "      google.charts.setOnLoadCallback(drawSeriesChart);\n"
                + "\n"
                + "    function drawSeriesChart() {\n"
                + "\n"
                + "      var data = google.visualization.arrayToDataTable([";
    }

    public String getGodClassScriptEnd() {
        return "]);\n" + "\n"
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
    }

    public String getCBOScriptStart() {
        return "      google.charts.load('current', {'packages':['corechart']});\n"
                + "      google.charts.setOnLoadCallback(drawSeriesChart);\n"
                + "\n"
                + "    function drawSeriesChart() {\n"
                + "\n"
                + "      var data2 = google.visualization.arrayToDataTable([";
    }

    public String getCBOScriptEnd() {
        return "]);\n" + "\n"
                + "      var options = {\n"
                + "        title: 'Priority Ranking for Refactoring Highly Coupled Classes - ' +\n"
                + "               'Start with Priority 1',\n"
                + "        height: 900, "
                + "        width: 1200, "
                + "        explorer: {}, "
                + "        hAxis: {title: 'Coupling Count'},\n"
                + "        vAxis: {title: 'Change Proneness'},\n"
                + "        colorAxis: {colors: ['green', 'red']},\n"
                + "        bubble: {textStyle: {fontSize: 11}}      };\n"
                + "\n"
                + "      var chart2 = new google.visualization.BubbleChart(document.getElementById('series_chart_div_2'));\n"
                + "      chart2.draw(data2, options);\n"
                + "    }\n";
    }

    public String generateGodClassBubbleChartData(List<RankedDisharmony> rankedDisharmonies, int maxPriority) {

        StringBuilder chartData = new StringBuilder();
        chartData.append("[ 'ID', 'Effort', 'Change Proneness', 'Priority', 'Priority (Visual)'], ");

        for (int i = 0; i < rankedDisharmonies.size(); i++) {
            RankedDisharmony rankedDisharmony = rankedDisharmonies.get(i);
            chartData.append("[");
            chartData.append("'");
            chartData.append(rankedDisharmony.getFileName());
            chartData.append("',");
            chartData.append(rankedDisharmony.getEffortRank());
            chartData.append(",");
            chartData.append(rankedDisharmony.getChangePronenessRank());
            chartData.append(",");
            chartData.append(rankedDisharmony.getPriority());
            chartData.append(",");
            chartData.append(maxPriority - rankedDisharmony.getPriority());
            chartData.append("]");
            if (i + 1 < rankedDisharmonies.size()) {
                chartData.append(",");
            }
        }
        return chartData.toString();
    }

    public String generateCBOBubbleChartData(List<RankedDisharmony> rankedDisharmonies, int maxPriority) {

        StringBuilder chartData = new StringBuilder();
        chartData.append("[ 'ID', 'Coupling Count', 'Change Proneness', 'Priority', 'Priority (Visual)'], ");

        for (int i = 0; i < rankedDisharmonies.size(); i++) {
            RankedDisharmony rankedDisharmony = rankedDisharmonies.get(i);
            chartData.append("[");
            chartData.append("'");
            chartData.append(rankedDisharmony.getFileName());
            chartData.append("',");
            chartData.append(rankedDisharmony.getEffortRank());
            chartData.append(",");
            chartData.append(rankedDisharmony.getChangePronenessRank());
            chartData.append(",");
            chartData.append(rankedDisharmony.getPriority());
            chartData.append(",");
            chartData.append(maxPriority - rankedDisharmony.getPriority());
            chartData.append("]");
            if (i + 1 < rankedDisharmonies.size()) {
                chartData.append(",");
            }
        }
        return chartData.toString();
    }
}
