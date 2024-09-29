package org.hjug.refactorfirst.report;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.CostBenefitCalculator;
import org.hjug.cbc.RankedCycle;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.gdg.GraphDataGenerator;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Slf4j
public class HtmlReport extends SimpleHtmlReport {

    public static final String GOD_CLASS_CHART_LEGEND =
            "       <h2>God Class Chart Legend:</h2>" + "       <table border=\"5px\">\n"
                    + "          <tbody>\n"
                    + "            <tr><td><strong>X-Axis:</strong> Effort to refactor to a non-God class</td></tr>\n"
                    + "            <tr><td><strong>Y-Axis:</strong> Relative churn</td></tr>\n"
                    + "            <tr><td><strong>Color:</strong> Priority of what to fix first</td></tr>\n"
                    + "            <tr><td><strong>Circle size:</strong> Priority (Visual) of what to fix first</td></tr>\n"
                    + "          </tbody>\n"
                    + "        </table>"
                    + "        <br/>";

    public static final String COUPLING_BETWEEN_OBJECT_CHART_LEGEND =
            "       <h2>Coupling Between Objects Chart Legend:</h2>" + "       <table border=\"5px\">\n"
                    + "          <tbody>\n"
                    + "            <tr><td><strong>X-Axis:</strong> Number of objects the class is coupled to</td></tr>\n"
                    + "            <tr><td><strong>Y-Axis:</strong> Relative churn</td></tr>\n"
                    + "            <tr><td><strong>Color:</strong> Priority of what to fix first</td></tr>\n"
                    + "            <tr><td><strong>Circle size:</strong> Priority (Visual) of what to fix first</td></tr>\n"
                    + "          </tbody>\n"
                    + "        </table>"
                    + "        <br/>";

    @Override
    public void printHead(StringBuilder stringBuilder) {
        stringBuilder.append("<script async defer src=\"https://buttons.github.io/buttons.js\"></script>"
                + "<script type=\"text/javascript\" src=\"https://www.gstatic.com/charts/loader.js\">"
                + "</script><script type=\"text/javascript\" src=\"./gchart.js\"></script>\n"
                + "<script type=\"text/javascript\" src=\"./gchart2.js\"></script>\n"
                + "<script src=\"https://d3js.org/d3.v5.min.js\"></script>\n"
                + "<script src=\"https://unpkg.com/d3-graphviz@3.0.5/build/d3-graphviz.min.js\"></script>\n"
                + "<script src=\"https://unpkg.com/@hpcc-js/wasm@0.3.11/dist/index.min.js\"></script>\n"
                + "  </head>\n");
    }

    @Override
    public void printTitle(String projectName, String projectVersion, StringBuilder stringBuilder) {
        stringBuilder
                .append("<title>Refactor First Report for ")
                .append(projectName)
                .append(" ")
                .append(projectVersion)
                .append(" </title>\n");
    }

    @Override
    void renderGithubButtons(StringBuilder stringBuilder) {
        stringBuilder.append("<div align=\"center\">\n");
        stringBuilder.append("Show RefactorFirst some &#10084;&#65039;\n");
        stringBuilder.append("<br/>\n");
        stringBuilder.append(
                "<a class=\"github-button\" href=\"https://github.com/refactorfirst/refactorfirst\" data-icon=\"octicon-star\" data-size=\"large\" data-show-count=\"true\" aria-label=\"Star refactorfirst/refactorfirst on GitHub\">Star</a>\n");
        stringBuilder.append(
                "<a class=\"github-button\" href=\"https://github.com/refactorfirst/refactorfirst/fork\" data-icon=\"octicon-repo-forked\" data-size=\"large\" data-show-count=\"true\" aria-label=\"Fork refactorfirst/refactorfirst on GitHub\">Fork</a>\n");
        stringBuilder.append(
                "<a class=\"github-button\" href=\"https://github.com/refactorfirst/refactorfirst/subscription\" data-icon=\"octicon-eye\" data-size=\"large\" data-show-count=\"true\" aria-label=\"Watch refactorfirst/refactorfirst on GitHub\">Watch</a>\n");
        stringBuilder.append(
                "<a class=\"github-button\" href=\"https://github.com/refactorfirst/refactorfirst/issues\" data-icon=\"octicon-issue-opened\" data-size=\"large\" data-show-count=\"false\" aria-label=\"Issue refactorfirst/refactorfirst on GitHub\">Issue</a>\n");
        stringBuilder.append(
                "<a class=\"github-button\" href=\"https://github.com/sponsors/jimbethancourt\" data-icon=\"octicon-heart\" data-size=\"large\" aria-label=\"Sponsor @jimbethancourt on GitHub\">Sponsor</a>\n");
        stringBuilder.append("</div>");
    }

    // TODO: Move to another class to allow use by Gradle plugin
    @Override
    void writeGodClassGchartJs(
            List<RankedDisharmony> rankedDisharmonies, int maxPriority, String reportOutputDirectory) {
        GraphDataGenerator graphDataGenerator = new GraphDataGenerator();
        String scriptStart = graphDataGenerator.getGodClassScriptStart();
        String bubbleChartData = graphDataGenerator.generateGodClassBubbleChartData(rankedDisharmonies, maxPriority);
        String scriptEnd = graphDataGenerator.getGodClassScriptEnd();

        String javascriptCode = scriptStart + bubbleChartData + scriptEnd;

        File reportOutputDir = new File(reportOutputDirectory);
        if (!reportOutputDir.exists()) {
            reportOutputDir.mkdirs();
        }
        String pathname = reportOutputDirectory + File.separator + "gchart.js";

        File scriptFile = new File(pathname);
        try {
            scriptFile.createNewFile();
        } catch (IOException e) {
            log.error("Failure creating God Class chart script file", e);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(scriptFile))) {
            writer.write(javascriptCode);
        } catch (IOException e) {
            log.error("Error writing chart script file", e);
        }
    }

    @Override
    void writeGCBOGchartJs(List<RankedDisharmony> rankedDisharmonies, int maxPriority, String reportOutputDirectory) {
        GraphDataGenerator graphDataGenerator = new GraphDataGenerator();
        String scriptStart = graphDataGenerator.getCBOScriptStart();
        String bubbleChartData = graphDataGenerator.generateCBOBubbleChartData(rankedDisharmonies, maxPriority);
        String scriptEnd = graphDataGenerator.getCBOScriptEnd();

        String javascriptCode = scriptStart + bubbleChartData + scriptEnd;

        File reportOutputDir = new File(reportOutputDirectory);
        if (!reportOutputDir.exists()) {
            reportOutputDir.mkdirs();
        }
        String pathname = reportOutputDirectory + File.separator + "gchart2.js";

        File scriptFile = new File(pathname);
        try {
            scriptFile.createNewFile();
        } catch (IOException e) {
            log.error("Failure creating CBO chart script file", e);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(scriptFile))) {
            writer.write(javascriptCode);
        } catch (IOException e) {
            log.error("Error writing CBO chart script file", e);
        }
    }

    public String getName(Locale locale) {
        // Name of the report when listed in the project-reports.html page of a project
        return "Refactor First Report";
    }

    public String getDescription(Locale locale) {
        // Description of the report when listed in the project-reports.html page of a project
        return "Ranks the disharmonies in a codebase.  The classes that should be refactored first "
                + " have the highest priority values.";
    }

    @Override
    void renderGodClassChart(
            String outputDirectory,
            List<RankedDisharmony> rankedGodClassDisharmonies,
            int maxGodClassPriority,
            StringBuilder stringBuilder) {
        writeGodClassGchartJs(rankedGodClassDisharmonies, maxGodClassPriority - 1, outputDirectory);
        stringBuilder.append("<div id=\"series_chart_div\" align=\"center\"></div>\n");
        renderGithubButtons(stringBuilder);
        stringBuilder.append(GOD_CLASS_CHART_LEGEND);
    }

    @Override
    void renderCBOChart(
            String outputDirectory,
            List<RankedDisharmony> rankedCBODisharmonies,
            int maxCboPriority,
            StringBuilder stringBuilder) {
        writeGCBOGchartJs(rankedCBODisharmonies, maxCboPriority - 1, outputDirectory);
        stringBuilder.append("<div id=\"series_chart_div_2\" align=\"center\"></div>\n");
        renderGithubButtons(stringBuilder);
        stringBuilder.append(COUPLING_BETWEEN_OBJECT_CHART_LEGEND);
    }

    @Override
    public List<RankedCycle> runCycleAnalysis(CostBenefitCalculator costBenefitCalculator, String outputDirectory) {
        return costBenefitCalculator.runCycleAnalysis();
    }

    @Override
    public void renderCycleImage(
            Graph<String, DefaultWeightedEdge> classGraph, RankedCycle cycle, StringBuilder stringBuilder) {
        String dot = buildDot(classGraph, cycle);

        stringBuilder.append("<div align=\"center\" id=\"" + cycle.getCycleName() + "\"></div>\n");
        stringBuilder.append("<script>\n");
        stringBuilder.append("d3.select(\"#" + cycle.getCycleName() + "\")\n");
        stringBuilder.append(".graphviz()\n");
        stringBuilder.append(".renderDot(" + dot + ");\n");
        stringBuilder.append("</script>\n");
    }

    String buildDot(Graph<String, DefaultWeightedEdge> classGraph, RankedCycle cycle) {
        StringBuilder dot = new StringBuilder();

        dot.append("'strict digraph G {\\n' +\n");

        // render vertices
        // e.g DownloadManager;
        for (String vertex : cycle.getVertexSet()) {
            dot.append("'");
            dot.append(vertex);
            dot.append(";\\n' +\n");
        }

        for (DefaultWeightedEdge edge : cycle.getEdgeSet()) {
            // 'DownloadManager -> Download [ label="1" color="red" ];'

            // render edge
            String[] vertexes =
                    edge.toString().replace("(", "").replace(")", "").split(":");

            String start = vertexes[0].trim();
            String end = vertexes[1].trim();

            dot.append("'");
            dot.append(start);
            dot.append(" -> ");
            dot.append(end);

            // render edge attributes
            dot.append(" [ ");
            dot.append("label = \"");
            dot.append((int) classGraph.getEdgeWeight(edge));
            dot.append("\"");

            if (cycle.getMinCutEdges().contains(edge)) {
                dot.append(" color = \"red\"");
            }

            dot.append(" ];\\n' +\n");
        }

        dot.append("'}'");

        return dot.toString();
    }
}
