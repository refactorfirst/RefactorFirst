package org.hjug.refactorfirst.report;

import static org.hjug.refactorfirst.report.ReportWriter.writeReportToDisk;

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
import org.hjug.cbc.RankedCycle;
import org.hjug.cbc.RankedDisharmony;
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
            "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\n" + "  <head>\n";

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

    private Graph<String, DefaultWeightedEdge> classGraph;

    private boolean showDetails = false;

    public void execute(
            boolean showDetails, String projectName, String projectVersion, String outputDirectory, File baseDir) {

        this.showDetails = showDetails;

        final String[] godClassTableHeadings =
                showDetails ? godClassDetailedTableHeadings : godClassSimpleTableHeadings;

        String filename = getOutputName() + ".html";

        log.info("Generating {} for {} - {}", filename, projectName, projectVersion);

        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault());

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(THE_BEGINNING);

        printTitle(projectName, projectVersion, stringBuilder);
        printHead(stringBuilder);
        printBreadcrumbs(stringBuilder);
        printProjectHeader(projectName, projectVersion, stringBuilder);

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
            stringBuilder.append(THE_END);
            writeReportToDisk(outputDirectory, filename, stringBuilder);
            return;
        }

        String parentOfGitDir = gitDir.getParentFile().getPath();
        log.info("Project Base Dir: {} ", projectBaseDir);
        log.info("Parent of Git Dir: {}", parentOfGitDir);

        if (!projectBaseDir.equals(parentOfGitDir)) {
            log.warn("Project Base Directory does not match Git Parent Directory");
            stringBuilder.append("Project Base Directory does not match Git Parent Directory.  "
                    + "Please refer to the report at the root of the site directory.");
            stringBuilder.append(THE_END);
            return;
        }

        List<RankedDisharmony> rankedGodClassDisharmonies;
        List<RankedDisharmony> rankedCBODisharmonies;
        List<RankedCycle> rankedCycles;
        try (CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator(projectBaseDir)) {
            costBenefitCalculator.runPmdAnalysis();
            rankedGodClassDisharmonies = costBenefitCalculator.calculateGodClassCostBenefitValues();
            rankedCBODisharmonies = costBenefitCalculator.calculateCBOCostBenefitValues();
            if (showDetails) {
                rankedCycles = costBenefitCalculator.runCycleAnalysisAndCalculateCycleChurn();
            } else {
                rankedCycles = costBenefitCalculator.runCycleAnalysis();
            }

            classGraph = costBenefitCalculator.getClassReferencesGraph();
        } catch (Exception e) {
            log.error("Error running analysis.");
            throw new RuntimeException(e);
        }

        if (rankedGodClassDisharmonies.isEmpty() && rankedCBODisharmonies.isEmpty() && rankedCycles.isEmpty()) {
            stringBuilder
                    .append("Congratulations!  ")
                    .append(projectName)
                    .append(" ")
                    .append(projectVersion)
                    .append(" has no God classes, highly coupled classes, or cycles!");
            renderGithubButtons(stringBuilder);
            log.info("Done! No Disharmonies found!");
            stringBuilder.append(THE_END);
            writeReportToDisk(outputDirectory, filename, stringBuilder);
            return;
        }

        if (!rankedGodClassDisharmonies.isEmpty() && !rankedCBODisharmonies.isEmpty()) {
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

        if (!rankedGodClassDisharmonies.isEmpty()) {
            renderGodClassInfo(
                    showDetails,
                    outputDirectory,
                    rankedGodClassDisharmonies,
                    stringBuilder,
                    godClassTableHeadings,
                    formatter);
        }

        if (!rankedGodClassDisharmonies.isEmpty() && !rankedCBODisharmonies.isEmpty()) {
            stringBuilder.append("<br/>\n" + "<br/>\n" + "<br/>\n" + "<br/>\n" + "<hr/>\n" + "<br/>\n" + "<br/>\n");
        }

        if (!rankedCBODisharmonies.isEmpty()) {
            renderHighlyCoupledClassInfo(outputDirectory, stringBuilder, rankedCBODisharmonies, formatter);
        }

        if (!rankedCycles.isEmpty()) {
            if (!rankedGodClassDisharmonies.isEmpty() || !rankedCBODisharmonies.isEmpty()) {
                stringBuilder.append("<br/>\n");
                stringBuilder.append("<br/>\n");
                stringBuilder.append("<hr/>\n");
                stringBuilder.append("<br/>\n");
                stringBuilder.append("<br/>\n");
            }
            renderCycles(outputDirectory, stringBuilder, rankedCycles, formatter);
        }

        stringBuilder.append("</section>\n");
        printProjectFooter(stringBuilder, formatter);
        stringBuilder.append(THE_END);

        log.debug(stringBuilder.toString());

        writeReportToDisk(outputDirectory, filename, stringBuilder);
        log.info("Done! View the report at target/site/{}", filename);
    }

    private void renderCycles(
            String outputDirectory,
            StringBuilder stringBuilder,
            List<RankedCycle> rankedCycles,
            DateTimeFormatter formatter) {

        stringBuilder.append("<div style=\"text-align: center;\"><a id=\"CYCLES\"><h1>Class Cycles</h1></a></div>\n");

        stringBuilder.append(
                "<h2 align=\"center\">Class Cycles by the numbers: (Refactor starting with Priority 1)</h2>\n");
        stringBuilder.append(
                "<p align=\"center\">Note: often only one minimum cut relationship needs to be removed</p>");
        stringBuilder.append("<table align=\"center\" border=\"5px\">\n");

        String[] cycleTableHeadings;
        if (showDetails) {
            cycleTableHeadings = new String[] {
                "Cycle Name", "Priority", "Change Proneness Rank", "Class Count", "Relationship Count", "Minimum Cuts"
            };
        } else {
            cycleTableHeadings =
                    new String[] {"Cycle Name", "Priority", "Class Count", "Relationship Count", "Minimum Cuts"};
        }

        // Content
        stringBuilder.append("<thead>\n<tr>\n");
        for (String heading : cycleTableHeadings) {
            stringBuilder.append("<th>").append(heading).append("</th>\n");
        }
        stringBuilder.append("</thead>\n");

        stringBuilder.append("<tbody>\n");
        for (RankedCycle rankedCycle : rankedCycles) {
            stringBuilder.append("<tr>\n");

            StringBuilder edgesToCut = new StringBuilder();
            for (DefaultWeightedEdge minCutEdge : rankedCycle.getMinCutEdges()) {
                edgesToCut.append(minCutEdge + ":" + (int) classGraph.getEdgeWeight(minCutEdge));
                edgesToCut.append("</br>\n");
            }

            String[] rankedCycleData;
            if (showDetails) {
                rankedCycleData = new String[] {
                    // "Cycle Name", "Priority", "Change Proneness Rank", "Class Count", "Relationship Count", "Min
                    // Cuts"
                    rankedCycle.getCycleName(),
                    rankedCycle.getPriority().toString(),
                    rankedCycle.getChangePronenessRank().toString(),
                    String.valueOf(rankedCycle.getCycleNodes().size()),
                    String.valueOf(rankedCycle.getEdgeSet().size()),
                    edgesToCut.toString()
                };
            } else {
                rankedCycleData = new String[] {
                    // "Cycle Name", "Priority", "Class Count", "Relationship Count", "Min Cuts"
                    rankedCycle.getCycleName(),
                    rankedCycle.getPriority().toString(),
                    String.valueOf(rankedCycle.getCycleNodes().size()),
                    String.valueOf(rankedCycle.getEdgeSet().size()),
                    edgesToCut.toString()
                };
            }
            for (String rowData : rankedCycleData) {
                drawTableCell(rowData, stringBuilder);
            }

            stringBuilder.append("</tr>\n");
        }

        stringBuilder.append("</tbody>\n");
        stringBuilder.append("</table>\n");

        for (RankedCycle rankedCycle : rankedCycles) {
            renderSingleCycle(outputDirectory, stringBuilder, rankedCycle, formatter);
        }
    }

    private void renderSingleCycle(
            String outputDirectory, StringBuilder stringBuilder, RankedCycle cycle, DateTimeFormatter formatter) {

        stringBuilder.append("<br/>\n");
        stringBuilder.append("<br/>\n");
        stringBuilder.append("<hr/>\n");
        stringBuilder.append("<br/>\n");
        stringBuilder.append("<br/>\n");

        stringBuilder.append("<h2 align=\"center\">Class Cycle : " + cycle.getCycleName() + "</h2>\n");
        // renderCycleImage(cycle.getCycleName(), stringBuilder, outputDirectory);
        renderCycleImage(classGraph, cycle, stringBuilder);

        stringBuilder.append("<div align=\"center\">");
        stringBuilder.append("<strong>");
        stringBuilder.append("\"*\" indicates relationship(s) to remove to decompose cycle");
        stringBuilder.append("</strong>");
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
            drawTableCell(vertex, stringBuilder);
            StringBuilder edges = new StringBuilder();
            for (org.jgrapht.graph.DefaultWeightedEdge edge : cycle.getEdgeSet()) {
                if (edge.toString().startsWith("(" + vertex + " :")) {
                    if (cycle.getMinCutEdges().contains(edge)) {
                        edges.append("<strong>");
                        edges.append(edge);
                        edges.append(":")
                                .append((int) classGraph.getEdgeWeight(edge))
                                .append("*");
                        edges.append("</strong>");
                    } else {
                        edges.append(edge);
                        edges.append(":").append((int) classGraph.getEdgeWeight(edge));
                    }

                    edges.append("<br/>\n");
                }
            }
            drawTableCell(edges.toString(), stringBuilder);
            stringBuilder.append("</tr>\n");
        }

        stringBuilder.append("</tbody>\n");

        stringBuilder.append("</table>\n");
    }

    public void renderCycleImage(
            Graph<String, DefaultWeightedEdge> classGraph, RankedCycle cycle, StringBuilder stringBuilder) {
        // empty on purpose
    }

    private void renderGodClassInfo(
            boolean showDetails,
            String outputDirectory,
            List<RankedDisharmony> rankedGodClassDisharmonies,
            StringBuilder stringBuilder,
            String[] godClassTableHeadings,
            DateTimeFormatter formatter) {
        int maxGodClassPriority = rankedGodClassDisharmonies
                .get(rankedGodClassDisharmonies.size() - 1)
                .getPriority();

        stringBuilder.append("<div style=\"text-align: center;\"><a id=\"GOD\"><h1>God Classes</h1></a></div>\n");

        renderGodClassChart(outputDirectory, rankedGodClassDisharmonies, maxGodClassPriority, stringBuilder);

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
                drawTableCell(rowData, stringBuilder);
            }

            stringBuilder.append("</tr>\n");
        }

        stringBuilder.append("</tbody>\n");
        stringBuilder.append("</table>\n");
    }

    private void renderHighlyCoupledClassInfo(
            String outputDirectory,
            StringBuilder stringBuilder,
            List<RankedDisharmony> rankedCBODisharmonies,
            DateTimeFormatter formatter) {
        stringBuilder.append(
                "<div style=\"text-align: center;\"><a id=\"CBO\"><h1>Highly Coupled Classes</h1></a></div>");

        int maxCboPriority =
                rankedCBODisharmonies.get(rankedCBODisharmonies.size() - 1).getPriority();

        renderCBOChart(outputDirectory, rankedCBODisharmonies, maxCboPriority, stringBuilder);

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
                drawTableCell(rowData, stringBuilder);
            }

            stringBuilder.append("</tr>");
        }

        stringBuilder.append("</tbody>");
        stringBuilder.append("</table>");
    }

    void drawTableCell(String rowData, StringBuilder stringBuilder) {
        if (isNumber(rowData) || isDateTime(rowData)) {
            stringBuilder.append("<td align=\"right\">").append(rowData).append("</td>\n");
        } else {
            stringBuilder.append("<td align=\"left\">").append(rowData).append("</td>\n");
        }
    }

    boolean isNumber(String rowData) {
        return rowData.matches("-?\\d+(\\.\\d+)?");
    }

    boolean isDateTime(String rowData) {
        return rowData.contains(", ");
    }

    public void printTitle(String projectName, String projectVersion, StringBuilder stringBuilder) {
        // empty on purpose
    }

    public void printHead(StringBuilder stringBuilder) {
        // empty on purpose
    }

    public void printBreadcrumbs(StringBuilder stringBuilder) {
        stringBuilder.append("  <body class=\"composite\">\n"
                + "    <div id=\"banner\">\n"
                + "    </div>\n"
                + "    <div id=\"breadcrumbs\">\n"
                + "      <div class=\"xleft\">\n");
    }

    public void printProjectHeader(String projectName, String projectVersion, StringBuilder stringBuilder) {

        stringBuilder.append("</div>\n" + "      <div class=\"xright\">      </div>\n"
                + "    </div>\n"
                + "    <div id=\"bodyColumn\">\n"
                + "      <div id=\"contentBox\">\n");

        stringBuilder
                .append(
                        "<section>\n"
                                + "<h2><a href=\"https://github.com/refactorfirst/refactorfirst\" target=\"_blank\" title=\"Learn about RefactorFirst\" aria-label=\"RefactorFirst\">RefactorFirst</a> Report for ")
                .append(projectName)
                .append(" ")
                .append(projectVersion)
                .append("</h2>\n");
    }

    public void printProjectFooter(StringBuilder stringBuilder, DateTimeFormatter formatter) {
        stringBuilder
                .append("      <div class=\"clear\">\n" + "        <hr/>\n" + "      </div>\n")
                .append("<span id=\"publishDate\">Last Published: ")
                .append(formatter.format(Instant.now()))
                .append("      <div class=\"clear\">\n" + "        <hr/>\n" + "      </div>\n")
                .append("</span>");
    }

    void renderGithubButtons(StringBuilder stringBuilder) {
        // empty on purpose
    }

    String getOutputName() {
        // This report will generate simple-report.html when invoked in a project with `mvn site`
        return "refactor-first-report";
    }

    void renderGodClassChart(
            String outputDirectory,
            List<RankedDisharmony> rankedGodClassDisharmonies,
            int maxGodClassPriority,
            StringBuilder stringBuilder) {
        // empty on purpose
    }

    String writeGodClassGchartJs(
            List<RankedDisharmony> rankedDisharmonies, int maxPriority, String reportOutputDirectory) {
        // return empty string on purpose
        return "";
    }

    String writeGCBOGchartJs(List<RankedDisharmony> rankedDisharmonies, int maxPriority, String reportOutputDirectory) {
        // return empty string on purpose
        return "";
    }

    void renderCBOChart(
            String outputDirectory,
            List<RankedDisharmony> rankedCBODisharmonies,
            int maxCboPriority,
            StringBuilder stringBuilder) {
        // empty on purpose
    }
}
