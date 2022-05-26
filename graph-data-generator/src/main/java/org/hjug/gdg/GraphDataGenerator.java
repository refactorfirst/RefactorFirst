package org.hjug.gdg;

import java.util.List;
import org.hjug.cbc.RankedDisharmony;

public class GraphDataGenerator {

    public String getScriptStart() {
        return "      google.charts.load('current', {'packages':['corechart']});\n"
                + "      google.charts.setOnLoadCallback(drawSeriesChart);\n"
                + "\n"
                + "    function drawSeriesChart() {\n"
                + "\n"
                + "      var data = google.visualization.arrayToDataTable([";
    }

    public String getScriptEnd() {
        return "]);\n" + "\n"
                + "      var options = {\n"
                + "        title: 'Priority Ranking for Refactoring God Classes - ' +\n"
                + "               'Fix Higher Priority Classes First',\n"
                + "        height: 900, "
                + "        width: 1200, "
                + "        explorer: {}, "
                + "        hAxis: {title: 'Effort'},\n"
                + "        vAxis: {title: 'Change Proneness'},\n"
                + "        colorAxis: {colors: ['blue', 'green']},\n"
                + "        bubble: {textStyle: {fontSize: 11}}      };\n"
                + "\n"
                + "      var chart = new google.visualization.BubbleChart(document.getElementById('series_chart_div'));\n"
                + "      chart.draw(data, options);\n"
                + "    }\n";
    }

    public String generateBubbleChartData(List<RankedDisharmony> rankedDisharmonies) {

        StringBuilder chartData = new StringBuilder();
        chartData.append("[ 'ID', 'Effort', 'Change Proneness', 'Priority', 'Method Count'], ");

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
            chartData.append(rankedDisharmony.getWmc());
            chartData.append("]");
            if (i + 1 < rankedDisharmonies.size()) {
                chartData.append(",");
            }
        }
        return chartData.toString();
    }
}
