package org.hjug.gdg;

import org.hjug.cbc.CostBenefitCalculator;
import org.hjug.cbc.RankedDisharmony;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GraphDataGenerator {

    private CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator();
    private List<RankedDisharmony> calculateCostBenefitValues = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        String path = "C:\\Code\\myfaces-tobago";
        GraphDataGenerator graphDataGenerator = new GraphDataGenerator();

        List<String> output = new ArrayList<>();
        output.add(graphDataGenerator.generateJavaScript(path));

        Files.write(Paths.get("chart.html"), output, StandardCharsets.UTF_8);
    }

    public String generateJavaScript(String repositoryPath) {
        String open = "<html>\n" +
                "  <head>\n" +
                "    <script type=\"text/javascript\" src=\"https://www.gstatic.com/charts/loader.js\"></script>\n" +
                "    <script type=\"text/javascript\">\n";

        String scriptStart = getScriptStart();

        String middle = generateBubbleChartData(repositoryPath);

        String scriptEnd =
                getScriptEnd();
        String endHead = "</script>" +
                        "  </head>\n" +
                        "  <body>\n" ;
        String chartDiv = getChartDiv();

        String close =
                        "  </body>\n" +
                        "</html>";

        return open + scriptStart + middle + scriptEnd + endHead + chartDiv + close;
    }

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
                "        width: 1800, " +
                "        hAxis: {title: 'Effort'},\n" +
                "        vAxis: {title: 'Change Proneness'},\n" +
                "        colorAxis: {colors: ['blue', 'green']},\n" +
                "        bubble: {textStyle: {fontSize: 11}}      };\n" +
                "\n" +
                "      var chart = new google.visualization.BubbleChart(document.getElementById('series_chart_div'));\n" +
                "      chart.draw(data, options);\n" +
                "    }\n";
    }

    public String getChartDiv() {
        return "    <div id=\"series_chart_div\" style=\"width: 900px; height: 500px;\"></div>\n";
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
