package org.hjug.refactorfirst.report;

import static org.hjug.refactorfirst.report.ReportWriter.writeReportToDisk;

import in.wilsonl.minifyhtml.Configuration;
import in.wilsonl.minifyhtml.MinifyHtml;
import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.CostBenefitCalculator;
import org.hjug.cbc.CycleRanker;
import org.hjug.cbc.RankedCycle;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.dsm.DSM;
import org.hjug.dsm.EdgeToRemoveInfo;
import org.hjug.git.GitLogReader;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 * Strictly HTML report that contains no JavaScript
 * Generates only tables
 */
@Slf4j
public class SimpleHtmlReport {

    public static final String THE_BEGINNING =
            "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\n" + "\n";

    public static final String THE_END = "</div>\n" + "    </div>\n" + "  </body>\n" + "</html>\n";

    public final String[] godClassSimpleTableHeadings = {
        "Class",
        "Priority",
        "Change Proneness Rank",
        "Effort Rank",
        "Method Count",
        "Most Recent Commit Date",
        "Commit Count"
    };

    public final String[] godClassDetailedTableHeadings = {
        "Class",
        "Priority",
        "Raw Priority",
        "Change Proneness Rank",
        "Effort Rank",
        "WMC",
        "WMC Rank",
        "ATFD",
        "ATFD Rank",
        "TCC",
        "TCC Rank",
        "Date of First Commit",
        "Most Recent Commit Date",
        "Commit Count",
        "Full Path"
    };

    public final String[] cboTableHeadings = {
        "Class", "Priority", "Change Proneness Rank", "Coupling Count", "Most Recent Commit Date", "Commit Count"
    };

    public final String[] classCycleTableHeadings = {"Classes", "Relationships"};

    Graph<String, DefaultWeightedEdge> classGraph;
    DSM dsm;
    List<DefaultWeightedEdge> edgesAboveDiagonal = List.of(); // initialize for unit tests

    DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault());

    private final Configuration htmlMinifierConfig = new Configuration.Builder()
            .setKeepHtmlAndHeadOpeningTags(true)
            .setKeepComments(false)
            .setMinifyJs(true)
            .setMinifyCss(true)
            .build();

    public void execute(
            int edgeAnalysisCount,
            boolean analyzeCycles,
            boolean showDetails,
            boolean minifyHtml,
            boolean excludeTests,
            String testSourceDirectory,
            String projectName,
            String projectVersion,
            File baseDir,
            String outputDirectory) {

        String filename = getOutputName() + ".html";
        log.info("Generating {} for {} - {}", filename, projectName, projectVersion);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(THE_BEGINNING);

        stringBuilder.append("<head>");
        stringBuilder.append(printTitle(projectName, projectVersion));
        stringBuilder.append(printHead());
        stringBuilder.append("</head>");
        stringBuilder.append(generateReport(
                showDetails,
                edgeAnalysisCount,
                analyzeCycles,
                excludeTests,
                testSourceDirectory,
                projectName,
                projectVersion,
                baseDir));

        stringBuilder.append(printProjectFooter());
        stringBuilder.append(THE_END);

        String reportHtml;
        if (minifyHtml) {
            reportHtml = MinifyHtml.minify(stringBuilder.toString(), htmlMinifierConfig);
        } else {
            reportHtml = stringBuilder.toString();
        }
        writeReportToDisk(outputDirectory, filename, reportHtml);
        log.info("Done! View the report at target/site/{}", filename);
    }

    public StringBuilder generateReport(
            boolean showDetails,
            int edgeAnalysisCount,
            boolean analyzeCycles,
            boolean excludeTests,
            String testSourceDirectory,
            String projectName,
            String projectVersion,
            File baseDir) {

        if (testSourceDirectory == null || testSourceDirectory.isEmpty()) {
            testSourceDirectory = "src" + File.separator + "test";
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(printOpenBodyTag());
        stringBuilder.append(printScripts());
        stringBuilder.append(printBreadcrumbs());
        stringBuilder.append(printProjectHeader(projectName, projectVersion));

        GitLogReader gitLogReader = new GitLogReader();
        String projectBaseDir;
        Optional<File> optionalGitDir;

        if (baseDir != null) {
            projectBaseDir = baseDir.getPath();
            optionalGitDir = Optional.ofNullable(gitLogReader.getGitDir(baseDir));
        } else {
            projectBaseDir = Paths.get("").toAbsolutePath().toString();
            optionalGitDir = Optional.ofNullable(gitLogReader.getGitDir(new File(projectBaseDir)));
        }

        File gitDir;
        if (optionalGitDir.isPresent()) {
            gitDir = optionalGitDir.get();
        } else {
            log.info(
                    "Done! No Git repository found!  Please initialize a Git repository and perform an initial commit.");
            stringBuilder
                    .append("No Git repository found in project ")
                    .append(projectName)
                    .append(" ")
                    .append(projectVersion)
                    .append(".  ");
            stringBuilder.append("Please initialize a Git repository and perform an initial commit.");

            return stringBuilder;
        }

        String parentOfGitDir = gitDir.getParentFile().getPath();
        log.info("Project Base Dir: {} ", projectBaseDir);
        log.info("Parent of Git Dir: {}", parentOfGitDir);

        if (!projectBaseDir.equals(parentOfGitDir)) {
            log.warn("Project Base Directory does not match Git Parent Directory");
            stringBuilder.append("Project Base Directory does not match Git Parent Directory.  "
                    + "Please refer to the report at the root of the site directory.");
            return stringBuilder;
        }

        List<RankedDisharmony> rankedGodClassDisharmonies = List.of();
        List<RankedDisharmony> rankedCBODisharmonies = List.of();
        log.info("Identifying Object Oriented Disharmonies");
        try (CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator(projectBaseDir)) {
            costBenefitCalculator.runPmdAnalysis();
            rankedGodClassDisharmonies = costBenefitCalculator.calculateGodClassCostBenefitValues();
            rankedCBODisharmonies = costBenefitCalculator.calculateCBOCostBenefitValues();
        } catch (Exception e) {
            log.error("Error running analysis.");
            throw new RuntimeException(e);
        }

        CycleRanker cycleRanker = new CycleRanker(projectBaseDir);
        List<RankedCycle> rankedCycles = List.of();
        if (analyzeCycles) {
            log.info("Analyzing Cycles");
            rankedCycles = cycleRanker.performCycleAnalysis(excludeTests, testSourceDirectory);
        } else {
            cycleRanker.generateClassReferencesGraph(excludeTests, testSourceDirectory);
        }

        classGraph = cycleRanker.getClassReferencesGraph();
        dsm = new DSM(classGraph);
        edgesAboveDiagonal = dsm.getEdgesAboveDiagonal();

        log.info("Performing edge removal what-if analysis");
        List<EdgeToRemoveInfo> edgeToRemoveInfos = dsm.getImpactOfEdgesAboveDiagonalIfRemoved(edgeAnalysisCount);

        if (edgeToRemoveInfos.isEmpty()
                && rankedGodClassDisharmonies.isEmpty()
                && rankedCBODisharmonies.isEmpty()
                && rankedCycles.isEmpty()) {
            stringBuilder
                    .append("Congratulations!  ")
                    .append(projectName)
                    .append(" ")
                    .append(projectVersion)
                    .append(" has no Back Edges, God classes, Highly Coupled Classes, or Cycles!");
            stringBuilder.append(renderGithubButtons());
            log.info("Done! No Disharmonies found!");
            return stringBuilder;
        }

        if (!edgeToRemoveInfos.isEmpty()) {
            stringBuilder.append("<a href=\"#EDGES\">Back Edges</a>\n");
            stringBuilder.append("<br/>\n");
        }

        if (!rankedGodClassDisharmonies.isEmpty()) {
            stringBuilder.append("<a href=\"#GOD\">God Classes</a>\n");
            stringBuilder.append("<br/>\n");
        }

        if (!rankedCBODisharmonies.isEmpty()) {
            stringBuilder.append("<a href=\"#CBO\">Highly Coupled Classes</a>\n");
            stringBuilder.append("<br/>\n");
        }

        if (!rankedCycles.isEmpty()) {
            stringBuilder.append("<a href=\"#CYCLES\">Class Cycles</a>\n");
        }

        log.info("Generating HTML Report");

        stringBuilder.append(renderClassGraphVisuals());
        stringBuilder.append("<br/>\n");
        stringBuilder.append(renderGithubButtons());

        // Display impact of each edge if removed
        stringBuilder.append("<br/>\n");
        String edgeInfos = renderEdgeToRemoveInfos(edgeToRemoveInfos);

        if (!edgeToRemoveInfos.isEmpty()) {
            stringBuilder.append(edgeInfos);
            stringBuilder.append(renderGithubButtons());
            stringBuilder.append("<br/>\n" + "<br/>\n" + "<br/>\n" + "<br/>\n" + "<hr/>\n" + "<br/>\n" + "<br/>\n");
        }

        if (!rankedGodClassDisharmonies.isEmpty()) {
            final String[] godClassTableHeadings =
                    showDetails ? godClassDetailedTableHeadings : godClassSimpleTableHeadings;
            stringBuilder.append(renderGodClassInfo(showDetails, rankedGodClassDisharmonies, godClassTableHeadings));
            stringBuilder.append("<br/>\n" + "<br/>\n" + "<br/>\n" + "<br/>\n" + "<hr/>\n" + "<br/>\n" + "<br/>\n");
        }

        if (!rankedCBODisharmonies.isEmpty()) {
            stringBuilder.append(renderHighlyCoupledClassInfo(rankedCBODisharmonies));
            stringBuilder.append("<br/>\n" + "<br/>\n" + "<br/>\n" + "<br/>\n" + "<hr/>\n" + "<br/>\n" + "<br/>\n");
        }

        if (!rankedCycles.isEmpty()) {
            stringBuilder.append(renderCycles(rankedCycles));
        }

        stringBuilder.append("</section>\n");

        log.debug(stringBuilder.toString());
        return stringBuilder;
    }

    private String renderCycles(List<RankedCycle> rankedCycles) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(renderClassCycleSummary(rankedCycles));

        stringBuilder.append("<br/>\n");

        //        rankedCycles.stream().limit(10).map(this::renderSingleCycle).forEach(stringBuilder::append);
        rankedCycles.stream().map(this::renderSingleCycle).forEach(stringBuilder::append);

        return stringBuilder.toString();
    }

    private String renderEdgeToRemoveInfos(List<EdgeToRemoveInfo> edges) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(
                "<div style=\"text-align: center;\"><a id=\"EDGES\"><h1>Backward Edge Removal Impact</h1></a></div>\n");
        stringBuilder.append("<div style=\"text-align: center;\">\n");

        stringBuilder
                .append("Current Cycle Count: ")
                .append(dsm.getCycles().size())
                .append("<br>\n");
        stringBuilder
                .append("Current Average Cycle Node Count: ")
                .append(dsm.getAverageCycleNodeCount())
                .append("<br>\n");
        stringBuilder
                .append("Current Total Back Edge Count: ")
                .append(dsm.getEdgesAboveDiagonal().size())
                .append("<br>\n");
        stringBuilder
                .append("Current Total Min Weight Back Edge Count: ")
                .append(dsm.getMinimumWeightEdgesAboveDiagonal().size())
                .append("<br>\n");
        stringBuilder.append("</div>\n");

        stringBuilder.append("<table align=\"center\" border=\"5px\">\n");

        // Content
        stringBuilder.append("<thead>\n<tr>\n");
        for (String heading : getEdgesToRemoveInfoTableHeadings()) {
            stringBuilder.append("<th>").append(heading).append("</th>\n");
        }
        stringBuilder.append("</thead>\n");

        stringBuilder.append("<tbody>\n");
        for (EdgeToRemoveInfo edge : edges) {
            stringBuilder.append("<tr>\n");

            for (String rowData : getEdgeToRemoveInfos(edge)) {
                stringBuilder.append(drawTableCell(rowData));
            }

            stringBuilder.append("</tr>\n");
        }

        stringBuilder.append("</tbody>\n");
        stringBuilder.append("</table>\n");

        return stringBuilder.toString();
    }

    private String renderClassCycleSummary(List<RankedCycle> rankedCycles) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("<div style=\"text-align: center;\"><a id=\"CYCLES\"><h1>Class Cycles</h1></a></div>\n");
        if (rankedCycles.size() > 10) {
            stringBuilder.append(
                    "<div style=\"text-align: center;\">10 largest cycles are shown in the sections below</div>\n");
        }

        stringBuilder.append("<h2 align=\"center\">Class Cycles by the numbers:</h2>\n");
        //        stringBuilder.append("<p align=\"center\"><strong>Bold edges are backward edges causing
        // cycles</strong></p>");
        stringBuilder.append("<table align=\"center\" border=\"5px\">\n");

        // Content
        stringBuilder.append("<thead>\n<tr>\n");
        for (String heading : getCycleSummaryTableHeadings()) {
            stringBuilder.append("<th>").append(heading).append("</th>\n");
        }
        stringBuilder.append("</thead>\n");

        stringBuilder.append("<tbody>\n");
        for (RankedCycle cycle : rankedCycles) {
            stringBuilder.append("<tr>\n");

            StringBuilder edges = new StringBuilder();
            for (DefaultWeightedEdge edge : cycle.getMinCutEdges()) {

                if (edgesAboveDiagonal.contains(edge)) {
                    stringBuilder.append("<strong>");
                    edges.append(renderEdge(edge));
                    stringBuilder.append("</strong>");
                } else {
                    edges.append(renderEdge(edge));
                }
                edges.append("</br>\n");
            }

            for (String rowData : getRankedCycleSummaryData(cycle, edges)) {
                stringBuilder.append(drawTableCell(rowData));
            }

            stringBuilder.append("</tr>\n");
        }

        stringBuilder.append("</tbody>\n");
        stringBuilder.append("</table>\n");

        return stringBuilder.toString();
    }

    private String renderEdge(DefaultWeightedEdge edge) {
        StringBuilder edgesToCut = new StringBuilder();
        String[] vertexes = extractVertexes(edge);
        String start = getClassName(vertexes[0].trim());
        String end = getClassName(vertexes[1].trim());

        // &#8594; is HTML "Right Arrow" code
        return edgesToCut
                .append(start + " &#8594; " + end + " : " + (int) classGraph.getEdgeWeight(edge))
                .toString();
    }

    private String[] getCycleSummaryTableHeadings() {
        return new String[] {"Cycle Name", "Priority", "Class Count", "Relationship Count" /*, "Minimum Cuts"*/};
    }

    private String[] getEdgesToRemoveInfoTableHeadings() {
        return new String[] {
            "Edge",
            "Edge Weight",
            "In # of Cycles",
            "New Cycle Count",
            "New Avg Cycle Node Count",
            "Avg Node &Delta; &divide; Effort"
        };
    }

    private String[] getEdgeToRemoveInfos(EdgeToRemoveInfo edgeToRemoveInfo) {
        return new String[] {
            // "Edge", "Edge Weight", "In # of Cycles", "New Cycle Count", "New Avg Cycle Node Count", "Avg Node Count /
            // Effort"
            renderEdge(edgeToRemoveInfo.getEdge()),
            String.valueOf((int) edgeToRemoveInfo.getEdgeWeight()),
            String.valueOf(edgeToRemoveInfo.getEdgeInCycleCount()),
            String.valueOf(edgeToRemoveInfo.getNewCycleCount()),
            String.valueOf(edgeToRemoveInfo.getAverageCycleNodeCount()),
            String.valueOf(edgeToRemoveInfo.getPayoff())
        };
    }

    private String[] getRankedCycleSummaryData(RankedCycle rankedCycle, StringBuilder edgesToCut) {
        return new String[] {
            // "Cycle Name", "Priority", "Class Count", "Relationship Count", "Min Cuts"
            getClassName(rankedCycle.getCycleName()),
            rankedCycle.getPriority().toString(),
            String.valueOf(rankedCycle.getCycleNodes().size()),
            String.valueOf(rankedCycle.getEdgeSet().size()) // ,
            //            edgesToCut.toString()
        };
    }

    private String renderSingleCycle(RankedCycle cycle) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("<br/>\n");
        stringBuilder.append("<br/>\n");
        stringBuilder.append("<hr/>\n");
        stringBuilder.append("<br/>\n");
        stringBuilder.append("<br/>\n");

        stringBuilder.append("<h2 align=\"center\">Class Cycle : " + getClassName(cycle.getCycleName()) + "</h2>\n");
        stringBuilder.append(renderCycleVisuals(cycle));

        stringBuilder.append("<div align=\"center\">");
        stringBuilder.append("<strong>");
        stringBuilder.append("Bold text indicates back edge to remove to decompose cycle");
        stringBuilder.append("</strong>");
        int classCount = cycle.getCycleNodes().size();
        int relationshipCount = cycle.getEdgeSet().size();
        stringBuilder.append("<div align=\"center\">Number of classes: " + classCount + "  Number of relationships: "
                + relationshipCount + "<br></div>");
        stringBuilder.append("</div>\n");

        stringBuilder.append("<table align=\"center\" border=\"5px\">\n");

        // Content
        stringBuilder.append("<thead>\n<tr>\n");
        for (String heading : classCycleTableHeadings) {
            stringBuilder.append("<th>").append(heading).append("</th>\n");
        }
        stringBuilder.append("</thead>\n");

        stringBuilder.append("<tbody>\n");

        for (String vertex : cycle.getVertexSet()) {
            stringBuilder.append("<tr>");
            stringBuilder.append(drawTableCell(getClassName(vertex)));
            StringBuilder edges = new StringBuilder();
            for (DefaultWeightedEdge edge : cycle.getEdgeSet()) {
                if (edge.toString().startsWith("(" + vertex + " :")) {

                    if (edgesAboveDiagonal.contains(edge)) {
                        edges.append("<strong>");
                        edges.append(renderEdge(edge));
                        if (cycle.getMinCutEdges().contains(edge)) {
                            edges.append("*");
                        }
                        edges.append("</strong>");
                    } else {
                        edges.append(renderEdge(edge));
                    }

                    edges.append("<br/>\n");
                }
            }
            stringBuilder.append(drawTableCell(edges.toString()));
            stringBuilder.append("</tr>\n");
        }

        stringBuilder.append("</tbody>\n");

        stringBuilder.append("</table>\n");

        return stringBuilder.toString();
    }

    public String renderClassGraphVisuals() {
        return ""; // empty on purpose
    }

    public String renderCycleVisuals(RankedCycle cycle) {
        return ""; // empty on purpose
    }

    private String renderGodClassInfo(
            boolean showDetails, List<RankedDisharmony> rankedGodClassDisharmonies, String[] godClassTableHeadings) {
        int maxGodClassPriority = rankedGodClassDisharmonies
                .get(rankedGodClassDisharmonies.size() - 1)
                .getPriority();

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<div style=\"text-align: center;\"><a id=\"GOD\"><h1>God Classes</h1></a></div>\n");

        stringBuilder.append(renderGodClassChart(rankedGodClassDisharmonies, maxGodClassPriority));

        stringBuilder.append(
                "<h2 align=\"center\">God classes by the numbers: (Refactor Starting with Priority 1)</h2>\n");
        stringBuilder.append("<table align=\"center\" border=\"5px\">\n");

        // Content
        stringBuilder.append("<thead><tr>");
        for (String heading : godClassTableHeadings) {
            stringBuilder.append("<th>").append(heading).append("</th>\n");
        }
        stringBuilder.append("</tr>\n</thead>\n");

        stringBuilder.append("<tbody>\n");
        for (RankedDisharmony rankedGodClassDisharmony : rankedGodClassDisharmonies) {
            stringBuilder.append("<tr>\n");

            String[] simpleRankedGodClassDisharmonyData = {
                rankedGodClassDisharmony.getFileName(),
                rankedGodClassDisharmony.getPriority().toString(),
                rankedGodClassDisharmony.getChangePronenessRank().toString(),
                rankedGodClassDisharmony.getEffortRank().toString(),
                rankedGodClassDisharmony.getWmc().toString(),
                formatter.format(rankedGodClassDisharmony.getMostRecentCommitTime()),
                rankedGodClassDisharmony.getCommitCount().toString()
            };

            String[] detailedRankedGodClassDisharmonyData = {
                rankedGodClassDisharmony.getFileName(),
                rankedGodClassDisharmony.getPriority().toString(),
                rankedGodClassDisharmony.getRawPriority().toString(),
                rankedGodClassDisharmony.getChangePronenessRank().toString(),
                rankedGodClassDisharmony.getEffortRank().toString(),
                rankedGodClassDisharmony.getWmc().toString(),
                rankedGodClassDisharmony.getWmcRank().toString(),
                rankedGodClassDisharmony.getAtfd().toString(),
                rankedGodClassDisharmony.getAtfdRank().toString(),
                rankedGodClassDisharmony.getTcc().toString(),
                rankedGodClassDisharmony.getTccRank().toString(),
                formatter.format(rankedGodClassDisharmony.getFirstCommitTime()),
                formatter.format(rankedGodClassDisharmony.getMostRecentCommitTime()),
                rankedGodClassDisharmony.getCommitCount().toString(),
                rankedGodClassDisharmony.getPath()
            };

            final String[] rankedDisharmonyData =
                    showDetails ? detailedRankedGodClassDisharmonyData : simpleRankedGodClassDisharmonyData;

            for (String rowData : rankedDisharmonyData) {
                stringBuilder.append(drawTableCell(rowData));
            }

            stringBuilder.append("</tr>\n");
        }

        stringBuilder.append("</tbody>\n");
        stringBuilder.append("</table>\n");

        return stringBuilder.toString();
    }

    private String renderHighlyCoupledClassInfo(List<RankedDisharmony> rankedCBODisharmonies) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(
                "<div style=\"text-align: center;\"><a id=\"CBO\"><h1>Highly Coupled Classes</h1></a></div>");

        int maxCboPriority =
                rankedCBODisharmonies.get(rankedCBODisharmonies.size() - 1).getPriority();

        stringBuilder.append(renderCBOChart(rankedCBODisharmonies, maxCboPriority));

        stringBuilder.append(
                "<h2 align=\"center\">Highly Coupled classes by the numbers: (Refactor starting with Priority 1)</h2>");
        stringBuilder.append("<table align=\"center\" border=\"5px\">");

        // Content
        stringBuilder.append("<thead><tr>");
        for (String heading : cboTableHeadings) {
            stringBuilder.append("<th>").append(heading).append("</th>");
        }
        stringBuilder.append("</tr></thead>");

        stringBuilder.append("<tbody>");
        for (RankedDisharmony rankedCboClassDisharmony : rankedCBODisharmonies) {
            stringBuilder.append("<tr>");

            String[] rankedCboClassDisharmonyData = {
                rankedCboClassDisharmony.getFileName(),
                rankedCboClassDisharmony.getPriority().toString(),
                rankedCboClassDisharmony.getChangePronenessRank().toString(),
                rankedCboClassDisharmony.getEffortRank().toString(),
                formatter.format(rankedCboClassDisharmony.getMostRecentCommitTime()),
                rankedCboClassDisharmony.getCommitCount().toString()
            };

            for (String rowData : rankedCboClassDisharmonyData) {
                stringBuilder.append(drawTableCell(rowData));
            }

            stringBuilder.append("</tr>");
        }

        stringBuilder.append("</tbody>");
        stringBuilder.append("</table>");

        return stringBuilder.toString();
    }

    String drawTableCell(String rowData) {
        if (isNumber(rowData) || isDateTime(rowData)) {
            return new StringBuilder()
                    .append("<td align=\"right\">")
                    .append(rowData)
                    .append("</td>\n")
                    .toString();
        } else {
            return new StringBuilder()
                    .append("<td align=\"left\">")
                    .append(rowData)
                    .append("</td>\n")
                    .toString();
        }
    }

    boolean isNumber(String rowData) {
        return rowData.matches("-?\\d+(\\.\\d+)?");
    }

    boolean isDateTime(String rowData) {
        return rowData.contains(", ");
    }

    public String printTitle(String projectName, String projectVersion) {
        return ""; // empty on purpose
    }

    public String printHead() {
        return ""; // empty on purpose
    }

    String printScripts() {
        return ""; // empty on purpose
    }

    public String printOpenBodyTag() {
        return "  <body class=\"composite\">\n";
    }

    public String printBreadcrumbs() {
        return "    <div id=\"banner\">\n"
                + "    </div>\n"
                + "    <div id=\"breadcrumbs\">\n"
                + "      <div class=\"xleft\">\n";
    }

    public String printProjectHeader(String projectName, String projectVersion) {
        return "</div>\n" + "      <div class=\"xright\">      </div>\n"
                + "    </div>\n"
                + "    <div id=\"bodyColumn\">\n"
                + "      <div id=\"contentBox\">\n" + "<section>\n"
                + "<h2><a href=\"https://github.com/refactorfirst/refactorfirst\" target=\"_blank\" "
                + "title=\"Learn about RefactorFirst\" aria-label=\"RefactorFirst\">RefactorFirst</a> Report for "
                + projectName
                + " "
                + projectVersion
                + "</h2>\n";
    }

    public String printProjectFooter() {
        return "      <div class=\"clear\">\n" + "        <hr/>\n" + "      </div>\n"
                + "<span id=\"publishDate\">Last Published: "
                + formatter.format(Instant.now())
                + "      <div class=\"clear\">\n"
                + "        <hr/>\n" + "      </div>\n" + "</span>";
    }

    String renderGithubButtons() {
        return ""; // empty on purpose
    }

    String getOutputName() {
        // This report will generate simple-report.html when invoked in a project with `mvn site`
        return "refactor-first-report";
    }

    String renderGodClassChart(List<RankedDisharmony> rankedGodClassDisharmonies, int maxGodClassPriority) {
        return ""; // empty on purpose
    }

    String writeGodClassGchartJs(List<RankedDisharmony> rankedDisharmonies, int maxPriority) {
        // return empty string on purpose
        return "";
    }

    String writeGCBOGchartJs(List<RankedDisharmony> rankedDisharmonies, int maxPriority) {
        // return empty string on purpose
        return "";
    }

    String renderCBOChart(List<RankedDisharmony> rankedCBODisharmonies, int maxCboPriority) {
        return ""; // empty on purpose
    }

    String getClassName(String fqn) {
        // handle no package
        if (!fqn.contains(".")) {
            return fqn;
        }

        int lastIndex = fqn.lastIndexOf(".");
        return fqn.substring(lastIndex + 1);
    }

    static String[] extractVertexes(DefaultWeightedEdge edge) {
        return edge.toString().replace("(", "").replace(")", "").split(":");
    }
}
