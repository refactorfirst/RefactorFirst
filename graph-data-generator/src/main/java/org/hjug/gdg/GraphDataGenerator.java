package org.hjug.gdg;

import org.hjug.cbc.CostBenefitCalculator;
import org.hjug.cbc.RankedDisharmony;

import java.util.ArrayList;
import java.util.List;

public class GraphDataGenerator {

    private final CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator();
    private final List<RankedDisharmony> calculateCostBenefitValues = new ArrayList<>();

    public String getScriptStart() {
        return
                "      google.charts.load('current', {'packages':['corechart']});\n" +
                "      google.charts.setOnLoadCallback(drawSeriesChart);\n" +
                "\n" +
                "    function drawSeriesChart() {\n" +
                "\n" +
                "      var data = google.visualization.arrayToDataTable([";
    }

    public String getScriptEnd() {
        return "]);\n" +
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
    }

    public String generateBubbleChartData(String repositoryPath) {

        List<RankedDisharmony> rankedDisharmonies = getRankedDisharmonies(repositoryPath);

        StringBuilder chartData = new StringBuilder();
        chartData.append("[ 'ID', 'Effort', 'Change Proneness', 'Priority', 'Weighted Method Count'], ");

        for (int i = 0; i < rankedDisharmonies.size(); i++) {
            RankedDisharmony rankedDisharmony = rankedDisharmonies.get(i);
            chartData.append("[");
            chartData.append("'");
            chartData.append(rankedDisharmony.getClassName());
            chartData.append("',");
            chartData.append(rankedDisharmony.getEffortRank());
            chartData.append(",");
            chartData.append(rankedDisharmony.getChangePronenessRank());
            chartData.append(",");
            chartData.append(rankedDisharmony.getPriority());
            chartData.append(",");
            chartData.append(rankedDisharmony.getWmc());
            chartData.append("]");
            if (i < rankedDisharmonies.size()) {
                chartData.append(",");
            }
        }
        return chartData.toString();
    }

    public List<RankedDisharmony> getRankedDisharmonies(String repositoryPath) {
        if(calculateCostBenefitValues.isEmpty()){
            calculateCostBenefitValues.addAll(costBenefitCalculator.calculateCostBenefitValues(repositoryPath));
        }

        return calculateCostBenefitValues;
    }
}
