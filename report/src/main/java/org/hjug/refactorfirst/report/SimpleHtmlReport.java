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
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.*;
import org.hjug.dsm.CircularReferenceChecker;
import org.hjug.feedback.SuperTypeToken;
import org.hjug.feedback.arc.EdgeInfo;
import org.hjug.feedback.arc.EdgeInfoCalculator;
import org.hjug.feedback.arc.pageRank.PageRankFAS;
import org.hjug.feedback.vertex.kernelized.DirectedFeedbackVertexSetResult;
import org.hjug.feedback.vertex.kernelized.DirectedFeedbackVertexSetSolver;
import org.hjug.feedback.vertex.kernelized.EnhancedParameterComputer;
import org.hjug.git.GitLogReader;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
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
    Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles;
    //    DSM<String, DefaultWeightedEdge> dsm;
    List<DefaultWeightedEdge> edgesAboveDiagonal = List.of(); // initialize for unit tests
    Set<String> vertexesToRemove = Set.of(); // initialize for unit tests
    Set<DefaultWeightedEdge> edgesToRemove = Set.of();

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

        CycleRanker cycleRanker = new CycleRanker(projectBaseDir);
        List<RankedCycle> rankedCycles = List.of();
        if (analyzeCycles) {
            log.info("Analyzing Cycles");
            rankedCycles = cycleRanker.performCycleAnalysis(excludeTests, testSourceDirectory);
        } else {
            cycleRanker.generateClassReferencesGraph(excludeTests, testSourceDirectory);
        }

        classGraph = cycleRanker.getClassReferencesGraph();
        cycles = new CircularReferenceChecker<String, DefaultWeightedEdge>().getCycles(classGraph);

        // Identify vertexes to remove
        log.info("Identifying vertexes to remove");
        EnhancedParameterComputer<String, DefaultWeightedEdge> enhancedParameterComputer =
                new EnhancedParameterComputer<>(new SuperTypeToken<>() {});
        EnhancedParameterComputer.EnhancedParameters<String> parameters =
                enhancedParameterComputer.computeOptimalParameters(classGraph, 4);
        DirectedFeedbackVertexSetSolver<String, DefaultWeightedEdge> vertexSolver =
                new DirectedFeedbackVertexSetSolver<>(
                        classGraph, parameters.getModulator(), null, parameters.getEta(), new SuperTypeToken<>() {});
        DirectedFeedbackVertexSetResult<String> vertexSetResult = vertexSolver.solve(parameters.getK());
        vertexesToRemove = vertexSetResult.getFeedbackVertices();

        // Identify edges to remove
        log.info("Identifying edges to remove");
        PageRankFAS<String, DefaultWeightedEdge> pageRankFAS = new PageRankFAS<>(classGraph, new SuperTypeToken<>() {});
        edgesToRemove = pageRankFAS.computeFeedbackArcSet();

        // capture the number of cycles each edge to remove is in
        Map<DefaultWeightedEdge, Integer> edgeToRemoveCycleCounts = new HashMap<>();
        for (DefaultWeightedEdge edgeToRemove : edgesToRemove) {
            int cycleCount = 0;
            for (AsSubgraph<String, DefaultWeightedEdge> cycle : cycles.values()) {
                if (cycle.containsEdge(edgeToRemove)) {
                    cycleCount++;
                }
            }
            edgeToRemoveCycleCounts.put(edgeToRemove, cycleCount);
        }

        // int edgeWeight = (int) classGraph.getEdgeWeight(defaultWeightedEdge);
        // map sources to CycleNodes to get paths and get churn in try/finally block below
        Map<DefaultWeightedEdge, CycleNode> edgeSourceNodeInfos = new HashMap<>();
        for (DefaultWeightedEdge defaultWeightedEdge : edgesToRemove) {
            String edgeSource = classGraph.getEdgeSource(defaultWeightedEdge);
            CycleNode cycleNode = cycleRanker.classToCycleNode(edgeSource);
            edgeSourceNodeInfos.put(defaultWeightedEdge, cycleNode);
        }

        List<RankedDisharmony> rankedGodClassDisharmonies = List.of();
        List<RankedDisharmony> rankedCBODisharmonies = List.of();
        List<RankedDisharmony> edgeDisharmonies = List.of();
        log.info("Identifying Object Oriented Disharmonies");
        try (CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator(projectBaseDir)) {
            costBenefitCalculator.runPmdAnalysis();
            rankedGodClassDisharmonies = costBenefitCalculator.calculateGodClassCostBenefitValues();
            rankedCBODisharmonies = costBenefitCalculator.calculateCBOCostBenefitValues();
            edgeDisharmonies = costBenefitCalculator.calculateSourceNodeCostBenefitValues(
                    classGraph, edgeSourceNodeInfos, edgeToRemoveCycleCounts, vertexesToRemove);

        } catch (Exception e) {
            log.error("Error running analysis.");
            throw new RuntimeException(e);
        }

        // TODO: Incorporate node information and guidance into Edge Infos
        // - Source / target vertex in list of vertexes to remove
        // - How many cycles is the edge present in
        // - Edge weight
        // - Provide guidance on where to move the method if one is in the list to remove

        //        log.info("Performing edge removal what-if analysis");
        //        EdgeRemovalCalculator edgeRemovalCalculator = new EdgeRemovalCalculator(classGraph, edgesToRemove);
        //        List<EdgeToRemoveInfo> edgeToRemoveInfos = edgeRemovalCalculator.getImpactOfEdges();

        if (edgesToRemove.isEmpty()
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

        if (!edgesToRemove.isEmpty()) {
            stringBuilder.append("<a href=\"#EDGES\">Edges To Remove</a>\n");
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

        stringBuilder.append("<br/>\n");
        if (!edgeDisharmonies.isEmpty()) {
            stringBuilder.append(renderEdgeDisharmonies(edgeDisharmonies));
        }

        // Display impact of each edge if removed
        /*stringBuilder.append("<br/>\n");
        String edgeInfos = renderEdgeToRemoveInfos(edgesToRemove);

        if (!edgesToRemove.isEmpty()) {
            stringBuilder.append(edgeInfos);
            stringBuilder.append(renderGithubButtons());
            stringBuilder.append("<br/>\n" + "<br/>\n" + "<br/>\n" + "<br/>\n" + "<hr/>\n" + "<br/>\n" + "<br/>\n");
        }*/

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

        //        if (!rankedCycles.isEmpty()) {
        //            stringBuilder.append(renderCycles(rankedCycles));
        //        }

        stringBuilder.append("</section>\n");

        log.debug(stringBuilder.toString());
        return stringBuilder;
    }

    private String renderCycles(List<RankedCycle> rankedCycles) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(renderClassCycleSummary(rankedCycles));

        stringBuilder.append("<br/>\n");

        rankedCycles.stream().limit(10).map(this::renderSingleCycle).forEach(stringBuilder::append);
        //        rankedCycles.stream().map(this::renderSingleCycle).forEach(stringBuilder::append);

        return stringBuilder.toString();
    }

    private String renderEdgeDisharmonies(List<RankedDisharmony> edgeDisharmonies) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(
                "<div style=\"text-align: center;\"><a id=\"EDGES\"><h1>Edge Removal Priority</h1></a></div>\n");
        stringBuilder.append("<div style=\"text-align: center;\">\n");
        stringBuilder.append("Current Cycle Count: ").append(cycles.size()).append("<br>\n");

        stringBuilder
                .append("Count of Edges to Remove: ")
                .append(edgesToRemove.size())
                .append("<br>\n");
        stringBuilder.append("</div>\n");
        stringBuilder.append("<table align=\"center\" border=\"5px\">\n");

        // Content
        stringBuilder.append("<thead>\n<tr>\n");
        for (String heading : getEdgeInfoTableHeadings()) {
            stringBuilder.append("<th>").append(heading).append("</th>\n");
        }
        stringBuilder.append("</thead>\n");

        stringBuilder.append("<tbody>\n");

        for (RankedDisharmony edge : edgeDisharmonies) {
            stringBuilder.append("<tr>\n");

            for (String rowData : getEdgeDisharmony(edge)) {
                stringBuilder.append(drawTableCell(rowData));
            }

            stringBuilder.append("</tr>\n");
        }

        stringBuilder.append("</tbody>\n");
        stringBuilder.append("</table>\n");

        return stringBuilder.toString();
    }

    private String renderEdgeToRemoveInfos(Set<DefaultWeightedEdge> edges) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(
                "<div style=\"text-align: center;\"><a id=\"EDGES\"><h1>Edge Removal Priority</h1></a></div>\n");
        stringBuilder.append("<div style=\"text-align: center;\">\n");
        stringBuilder.append("Current Cycle Count: ").append(cycles.size()).append("<br>\n");

        stringBuilder
                .append("Count of Edges to Remove: ")
                .append(edgesToRemove.size())
                .append("<br>\n");
        stringBuilder.append("</div>\n");
        stringBuilder.append("<table align=\"center\" border=\"5px\">\n");

        // Content
        stringBuilder.append("<thead>\n<tr>\n");
        for (String heading : getEdgeInfoTableHeadings()) {
            stringBuilder.append("<th>").append(heading).append("</th>\n");
        }
        stringBuilder.append("</thead>\n");

        stringBuilder.append("<tbody>\n");

        EdgeInfoCalculator edgeInfoCalculator =
                new EdgeInfoCalculator(classGraph, edgesToRemove, vertexesToRemove, cycles);

        for (EdgeInfo edge : edgeInfoCalculator.calculateEdgeInformation()) {
            stringBuilder.append("<tr>\n");

            for (String rowData : getEdgeInfo(edge)) {
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

    private String[] getEdgeInfoTableHeadings() {
        return new String[] {
            "Edge",
            "Priority",
            "In Cycles",
            "Edge Weight",
            "Source Change Proneness Rank",
            "Remove Source",
            "Remove Target"
        };
    }

    private String[] getEdgeInfo(EdgeInfo edgeInfo) {
        return new String[] {
            // "Edge", "In Cycles", "Remove Source", "Remove Target", "Edge Weight"
            renderEdge(edgeInfo.getEdge()),
            String.valueOf(edgeInfo.getPresentInCycleCount()),
            edgeInfo.isRemoveSource() ? "Y" : "N",
            edgeInfo.isRemoveTarget() ? "Y" : "N",
            String.valueOf(edgeInfo.getWeight()),
        };
    }

    private String[] getEdgeDisharmony(RankedDisharmony edgeInfo) {
        return new String[] {
            renderEdge(edgeInfo.getEdge()),
            String.valueOf(edgeInfo.getPriority()),
            String.valueOf(edgeInfo.getCycleCount()),
            String.valueOf(edgeInfo.getEffortRank()),
            String.valueOf(edgeInfo.getChangePronenessRank()),
            String.valueOf(edgeInfo.getSourceNodeShouldBeRemoved()),
            String.valueOf(edgeInfo.getTargetNodeShouldBeRemoved()),
        };
    }

    private String[] getRankedCycleSummaryData(RankedCycle rankedCycle, StringBuilder edgesToCut) {
        return new String[] {
            // "Cycle Name", "Priority", "Class Count", "Relationship Count", "Min Cuts"
            getClassName(rankedCycle.getCycleName()),
            rankedCycle.getPriority().toString(),
            String.valueOf(rankedCycle.getCycleNodes().size()),
            String.valueOf(rankedCycle.getEdgeSet().size())
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
