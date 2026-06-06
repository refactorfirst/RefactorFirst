package org.hjug.gdg;

import java.util.List;
import org.hjug.cbc.RankedDisharmony;

public class GraphDataGenerator {

    public String getDisharmonyScriptStart(String functionName, String dataVar) {
        return "      google.charts.load('current', {'packages':['corechart']});\n"
                + "      google.charts.setOnLoadCallback(" + functionName + ");\n"
                + "\n"
                + "    function " + functionName + "() {\n"
                + "\n"
                + "      var " + dataVar + " = google.visualization.arrayToDataTable([";
    }

    public String getDisharmonyScriptEnd(
            String functionName, String chartVar, String divId, String dataVar, String title, String xAxisLabel) {
        return "]);\n" + "\n"
                + "      var options = {\n"
                + "        title: '" + title + " - Start with Priority 1',\n"
                + "        height: 900, "
                + "        width: 1200, "
                + "        explorer: {}, "
                + "        hAxis: {title: '" + xAxisLabel + "'},\n"
                + "        vAxis: {title: 'Change Proneness'},\n"
                + "        colorAxis: {colors: ['green', 'red']},\n"
                + "        bubble: {textStyle: {fontSize: 11}}      };\n"
                + "\n"
                + "      var " + chartVar + " = new google.visualization.BubbleChart(document.getElementById('"
                + divId + "'));\n"
                + "      " + chartVar + ".draw(" + dataVar + ", options);\n"
                + "    }\n";
    }

    public String generateBubbleChartData(
            List<RankedDisharmony> rankedDisharmonies, int maxPriority, String xAxisLabel) {
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
}
