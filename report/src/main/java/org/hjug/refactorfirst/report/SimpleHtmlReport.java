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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.*;
import org.hjug.dsm.CircularReferenceChecker;
import org.hjug.feedback.SuperTypeToken;
import org.hjug.feedback.arc.pageRank.PageRankFAS;
import org.hjug.feedback.vertex.kernelized.DirectedFeedbackVertexSetResult;
import org.hjug.feedback.vertex.kernelized.DirectedFeedbackVertexSetSolver;
import org.hjug.feedback.vertex.kernelized.EnhancedParameterComputer;
import org.hjug.git.GitLogReader;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.metrics.DisharmonyTypes;
import org.hjug.metrics.DisharmonyInstance;
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

    public final String[] cboTableHeadings = {
        "Class", "Priority", "Change Proneness Rank", "Coupling Count", "Most Recent Commit Date", "Commit Count"
    };

    public final String[] classCycleTableHeadings = {"Classes", "Relationships"};

    Graph<String, DefaultWeightedEdge> classGraph;
    Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles;
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
        Map<DefaultWeightedEdge, CycleNode> sourceNodeInfos = new HashMap<>();
        Map<DefaultWeightedEdge, CycleNode> targetNodeInfos = new HashMap<>();
        for (DefaultWeightedEdge defaultWeightedEdge : edgesToRemove) {
            String edgeSource = classGraph.getEdgeSource(defaultWeightedEdge);
            CycleNode sourceNode = cycleRanker.classToCycleNode(edgeSource);
            sourceNodeInfos.put(defaultWeightedEdge, sourceNode);

            String edgeTarget = classGraph.getEdgeTarget(defaultWeightedEdge);
            CycleNode targetNode = cycleRanker.classToCycleNode(edgeTarget);
            targetNodeInfos.put(defaultWeightedEdge, targetNode);
        }

        List<RankedDisharmony> edgeDisharmonies = List.of();

        // Ordered (type, anchorId, displayTitle, isMethodLevel) for all disharmonies
        final List<DisharmonySpec> disharmonySpecs = List.of(
                new DisharmonySpec(DisharmonyTypes.GOD_CLASS, "GOD", "God Classes", false),
                new DisharmonySpec(DisharmonyTypes.DATA_CLASS, "DATA_CLASS", "Data Classes", false),
                new DisharmonySpec(DisharmonyTypes.BRAIN_CLASS, "BRAIN_CLASS", "Brain Classes", false),
                new DisharmonySpec(DisharmonyTypes.REFUSED_PARENT_BEQUEST, "RPB", "Refused Parent Bequest", false),
                new DisharmonySpec(DisharmonyTypes.TRADITION_BREAKER, "TB", "Tradition Breakers", false),
                new DisharmonySpec(
                        DisharmonyTypes.SIGNIFICANT_DUPLICATION, "SIG_DUP", "Significant Duplication", false),
                new DisharmonySpec(DisharmonyTypes.BRAIN_METHOD, "BRAIN_METHOD", "Brain Methods", true),
                new DisharmonySpec(DisharmonyTypes.FEATURE_ENVY, "FEATURE_ENVY", "Feature Envy", true),
                new DisharmonySpec(DisharmonyTypes.LONG_METHOD, "LONG_METHOD", "Long Methods", true),
                new DisharmonySpec(
                        DisharmonyTypes.INTENSIVE_COUPLING, "INTENSIVE_COUPLING", "Intensive Coupling", true),
                new DisharmonySpec(
                        DisharmonyTypes.DISPERSED_COUPLING, "DISPERSED_COUPLING", "Dispersed Coupling", true),
                new DisharmonySpec(DisharmonyTypes.SHOTGUN_SURGERY, "SHOTGUN_SURGERY", "Shotgun Surgery", true));

        Map<String, List<RankedDisharmony>> rankedDisharmoniesByAnchor = new LinkedHashMap<>();

        log.info("Identifying Object Oriented Disharmonies");

        CodebaseGraphDTO codebaseGraphDTO = cycleRanker.getCodebaseGraphDTO();
        try (CostBenefitCalculator costBenefitCalculator =
                new CostBenefitCalculator(projectBaseDir, codebaseGraphDTO.getClassToSourceFilePathMapping())) {
            edgeDisharmonies = costBenefitCalculator.calculateSourceNodeCostBenefitValues(
                    classGraph, edgeToRemoveCycleCounts, codebaseGraphDTO, vertexesToRemove);

            for (DisharmonySpec spec : disharmonySpecs) {
                List<DisharmonyInstance> instances = spec.methodLevel()
                        ? costBenefitCalculator.getMethodDisharmonies(codebaseGraphDTO, spec.type())
                        : costBenefitCalculator.getClassDisharmonies(codebaseGraphDTO, spec.type());
                if (!instances.isEmpty()) {
                    rankedDisharmoniesByAnchor.put(
                            spec.anchorId(), costBenefitCalculator.calculateDisharmonyCostBenefitValues(instances));
                }
            }

        } catch (Exception e) {
            log.error("Error running analysis.");
            throw new RuntimeException(e);
        }

        // TODO: Incorporate node information and guidance into Edge Infos
        // - Source / target vertex in list of vertexes to remove
        // - How many cycles is the edge present in
        // - Edge weight
        // - Provide guidance on where to move the method if one is in the list to remove

        boolean hasAnyDisharmony = !edgesToRemove.isEmpty()
                || !rankedCycles.isEmpty()
                || !rankedDisharmoniesByAnchor.isEmpty();

        if (!hasAnyDisharmony) {
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

        stringBuilder.append("<header>\n" + "<nav>\n" + " <ul>\n");

        if (!edgesToRemove.isEmpty()) {
            stringBuilder.append("<li><a href=\"#EDGES\">Edges To Remove</a></li>\n");
        }

        if (!disharmonySpecs.isEmpty()) {
            stringBuilder.append("<li><a href=\"#\">Disharmonies</a>\n" + "                <ul>");
        }

        for (DisharmonySpec spec : disharmonySpecs) {
            if (rankedDisharmoniesByAnchor.containsKey(spec.anchorId())) {
                stringBuilder
                        .append("<li><a href=\"#")
                        .append(spec.anchorId())
                        .append("\">")
                        .append(spec.title())
                        .append("</a></li>\n");
            }
        }

        if (!disharmonySpecs.isEmpty()) {
            stringBuilder.append("</ul>\n" + "            </li>");
        }

        if (!rankedCycles.isEmpty()) {
            stringBuilder.append("<li><a href=\"#CYCLES\">Class Cycles</a></li>\n");
        }

        stringBuilder.append("</ul>\n" + "</nav>\n" + "</header>\n");
        log.info("Generating HTML Report");

        stringBuilder.append(renderClassGraphVisuals());
        stringBuilder.append("<br/>\n");
        stringBuilder.append(renderGithubButtons());

        stringBuilder.append("<br/>\n");
        if (!edgeDisharmonies.isEmpty()) {
            stringBuilder.append(renderEdgeDisharmonies(edgeDisharmonies));
            stringBuilder.append("<br/>\n" + "<br/>\n" + "<br/>\n" + "<br/>\n" + "<hr/>\n" + "<br/>\n" + "<br/>\n");
        }

        for (DisharmonySpec spec : disharmonySpecs) {
            List<RankedDisharmony> rankedForType = rankedDisharmoniesByAnchor.get(spec.anchorId());
            if (rankedForType != null && !rankedForType.isEmpty()) {
                stringBuilder.append(renderDisharmonyInfo(
                        spec.anchorId(), spec.title(), spec.methodLevel(), showDetails, rankedForType));
                stringBuilder.append("<br/>\n" + "<br/>\n" + "<br/>\n" + "<br/>\n" + "<hr/>\n" + "<br/>\n" + "<br/>\n");
            }
        }

        if (!rankedCycles.isEmpty()) {
            stringBuilder.append(renderCycles(rankedCycles));
        }

        log.debug(stringBuilder.toString());
        return stringBuilder;
    }

    private String renderCycles(List<RankedCycle> rankedCycles) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(renderClassCycleSummary(rankedCycles));

        rankedCycles.stream().limit(1).map(this::renderSingleCycle).forEach(stringBuilder::append);

        return stringBuilder.toString();
    }

    private String renderEdgeDisharmonies(List<RankedDisharmony> edgeDisharmonies) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(
                "<div style=\"text-align: center;\"><a id=\"EDGES\"><h1>Relationship Removal Priority</h1></a></div>\n");
        stringBuilder.append("<h2 align=\"center\">Refactor Starting with Priority 1</h2>\n");
        stringBuilder.append("<div style=\"text-align: center;\">\n");
        stringBuilder.append("Current Cycle Count: ").append(cycles.size()).append("<br>\n");

        stringBuilder
                .append("Number of Relationships to Remove: ")
                .append(edgesToRemove.size())
                .append("<br>\n");
        stringBuilder.append("Classes in bold should be broken apart").append("<br>\n");
        stringBuilder.append("</div>\n");

        // Content
        stringBuilder.append("<div align=\"center\">");
        stringBuilder.append("<table align=\"center\" border=\"5px\">\n");
        stringBuilder.append("<thead>\n<tr>\n");
        for (String heading : getEdgeDisharmonyTableHeadings()) {
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
        stringBuilder.append("</div>\n");

        return stringBuilder.toString();
    }

    private String[] getEdgeDisharmonyTableHeadings() {
        return new String[] {
            "Relationship",
            "Priority",
            "In Cycles",
            "Edge<br>Weight",
            "Source<br>Disharmony Count",
            "Target<br>Disharmony Count",
        };
    }

    private String[] getEdgeDisharmony(RankedDisharmony edgeInfo) {
        return new String[] {
            renderEdge(edgeInfo.getEdge()),
            String.valueOf(edgeInfo.getPriority()),
            String.valueOf(edgeInfo.getCycleCount()),
            String.valueOf(edgeInfo.getEffortRank()),
            String.valueOf(edgeInfo.getChangePronenessRank()),
            String.valueOf(edgeInfo.getEdgeTargetChangePronenessRank()),
        };
    }

    private String renderClassCycleSummary(List<RankedCycle> rankedCycles) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("<div style=\"text-align: center;\"><a id=\"CYCLES\"><h1>Class Cycles</h1></a></div>\n");
        /*if (rankedCycles.size() > 10) {
            stringBuilder.append(
                    "<div style=\"text-align: center;\">10 largest cycles are shown in the sections below</div>\n");
        }*/

        stringBuilder.append("<h2 align=\"center\">Class Cycles by the numbers:</h2>\n");
        stringBuilder.append("<div align=\"center\">");
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

                if (edgesToRemove.contains(edge)) {
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
        stringBuilder.append("<div>");
        return stringBuilder.toString();
    }

    private String renderEdge(DefaultWeightedEdge edge) {
        StringBuilder edgesToCut = new StringBuilder();
        String[] vertexes = extractVertexes(edge);

        String startVertex = vertexes[0].trim();
        String start;
        if (vertexesToRemove.contains(startVertex)) {
            start = "<strong>" + getClassName(startVertex) + "</strong>";
        } else {
            start = getClassName(startVertex);
        }

        String endVertex = vertexes[1].trim();
        String end;
        if (vertexesToRemove.contains(endVertex)) {
            end = "<strong>" + getClassName(endVertex) + "</strong>";
        } else {
            end = getClassName(endVertex);
        }

        // &#8594; is HTML "Right Arrow" code
        return edgesToCut
                .append(start + " &#8594; " + end + " : " + (int) classGraph.getEdgeWeight(edge))
                .toString();
    }

    private String[] getCycleSummaryTableHeadings() {
        return new String[] {"Cycle Name", "Priority", "Class Count", "Relationship Count" /*, "Minimum Cuts"*/};
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

        stringBuilder.append(
                "<h2 align=\"center\">Largest Class Cycle : " + getClassName(cycle.getCycleName()) + "</h2>\n");
        stringBuilder.append(
                "<h3 align=\"center\">Limiting number of cycles displayed to 1 to keep page load time fast</h3>\n");
        stringBuilder.append(renderCycleVisuals(cycle));

        stringBuilder.append("<div align=\"center\">");
        stringBuilder.append("<strong>");
        stringBuilder.append("Bold text indicates class or relationship to remove to decompose cycle");
        stringBuilder.append("</strong>");
        int classCount = cycle.getCycleNodes().size();
        int relationshipCount = cycle.getEdgeSet().size();
        stringBuilder.append("<div align=\"center\">Number of classes: " + classCount + "  Number of relationships: "
                + relationshipCount + "<br></div>");
        stringBuilder.append("</div>\n");

        stringBuilder.append("<div align=\"center\">");
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
            String className = getClassName(vertex);
            if (vertexesToRemove.contains(vertex)) {
                className = "<strong>" + className + "</strong>";
            }

            stringBuilder.append(drawTableCell(className));
            StringBuilder edges = new StringBuilder();
            for (DefaultWeightedEdge edge : cycle.getEdgeSet()) {
                if (edge.toString().startsWith("(" + vertex + " :")) {

                    if (edgesToRemove.contains(edge)) {
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
        stringBuilder.append("</div>");
        return stringBuilder.toString();
    }

    public String renderClassGraphVisuals() {
        return ""; // empty on purpose
    }

    public String renderCycleVisuals(RankedCycle cycle) {
        return ""; // empty on purpose
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
                + "      <div id=\"contentBox\">\n"
                + "<h2 align=\"center\"><a href=\"https://github.com/refactorfirst/refactorfirst\" target=\"_blank\" "
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

    /**
     * Renders a table section for any non-God-Class disharmony type.
     * Column headers are derived from the ranked metrics carried on each RankedDisharmony.
     */
    public String renderDisharmonyInfo(
            String anchorId, String title, boolean methodLevel, boolean showDetails, List<RankedDisharmony> ranked) {
        if (ranked.isEmpty()) {
            return "";
        }

        int maxPriority = ranked.get(ranked.size() - 1).getPriority();

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"text-align: center;\"><a id=\"")
                .append(anchorId)
                .append("\"><h1>")
                .append(title)
                .append("</h1></a></div>\n");

        sb.append(renderDisharmonyChart(anchorId, title, ranked, maxPriority));

        sb.append("<h2 align=\"center\">")
                .append(title)
                .append(" by the numbers: (Refactor Starting with Priority 1)</h2>\n");
        sb.append("<div align=\"center\">");
        sb.append("<table align=\"center\" border=\"5px\">\n");

        // Build headers from the first item's ranked metrics
        List<org.hjug.graphbuilder.metrics.DisharmonyMetric> sampleMetrics =
                ranked.get(0).getRankedMetrics();

        boolean showPartners = ranked.get(0).getDuplicationPartners() != null;

        sb.append("<thead><tr>");
        sb.append("<th>Class</th>\n");
        if (methodLevel) {
            sb.append("<th>Method</th>\n");
        }
        sb.append("<th>Priority</th>\n");
        if (showDetails) {
            sb.append("<th>Raw Priority</th>\n");
            sb.append("<th>Description</th>\n");
        }
        sb.append("<th>Change Proneness Rank</th>\n");
        sb.append("<th>Effort Rank</th>\n");
        if (showDetails) {
            for (org.hjug.graphbuilder.metrics.DisharmonyMetric m : sampleMetrics) {
                sb.append("<th>").append(m.getName()).append("</th>\n");
                sb.append("<th>").append(m.getName()).append(" Rank</th>\n");
            }
        }
        if (showPartners) {
            sb.append("<th>Duplicate Partners</th>\n");
        }
        sb.append("<th>Most Recent Commit Date</th>\n");
        sb.append("<th>Commit Count</th>\n");
        if (showDetails) {
            sb.append("<th>Date of First Commit</th>\n");
            sb.append("<th>Full Path</th>\n");
        }
        sb.append("</tr>\n</thead>\n");

        sb.append("<tbody>\n");
        for (RankedDisharmony rd : ranked) {
            sb.append("<tr>\n");
            sb.append(drawTableCell(rd.getFileName()));
            if (methodLevel) {
                String sig = rd.getMethodSignature();
                if (!showDetails && sig != null) {
                    // simplify the method signature to just the name and type
                    sig = getSimpleMethodSignature(sig);
                }
                sb.append(drawTableCell(sig != null ? sig : ""));
            }
            sb.append(drawTableCell(rd.getPriority().toString()));
            if (showDetails) {
                sb.append(drawTableCell(rd.getRawPriority().toString()));
                sb.append(drawTableCell(rd.getDescription() != null ? rd.getDescription() : ""));
            }
            sb.append(drawTableCell(rd.getChangePronenessRank().toString()));
            sb.append(drawTableCell(rd.getEffortRank().toString()));
            if (showDetails) {
                for (org.hjug.graphbuilder.metrics.DisharmonyMetric m : rd.getRankedMetrics()) {
                    double v = m.getValue();
                    String formatted = (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
                    sb.append(drawTableCell(formatted));
                    sb.append(drawTableCell(m.getRank() != null ? m.getRank().toString() : ""));
                }
            }
            if (showPartners) {
                String duplicationPartners = rd.getDuplicationPartners();
                if (!showDetails && duplicationPartners != null) {
                    duplicationPartners = simplifyDuplicatePartners(duplicationPartners);
                }
                sb.append(drawTableCell(
                        rd.getDuplicationPartners() != null ? duplicationPartners.replace(";", "<br>") : ""));
            }
            sb.append(drawTableCell(formatter.format(rd.getMostRecentCommitTime())));
            sb.append(drawTableCell(rd.getCommitCount().toString()));
            if (showDetails) {
                sb.append(drawTableCell(formatter.format(rd.getFirstCommitTime())));
                sb.append(drawTableCell(rd.getPath()));
            }
            sb.append("</tr>\n");
        }
        sb.append("</tbody>\n");
        sb.append("</table>\n");
        sb.append("</div>");
        return sb.toString();
    }

    String getSimpleMethodSignature(String sig) {
        if (sig == null) {
            return null;
        }

        int openParenIdx = sig.indexOf('(');
        int closeParenIdx = sig.lastIndexOf(')');
        // If we can't find parentheses, just return the original string
        if (openParenIdx == -1 || closeParenIdx == -1 || closeParenIdx < openParenIdx) {
            return sig;
        }

        String methodName = sig.substring(0, openParenIdx).trim();
        String paramsSection = sig.substring(openParenIdx + 1, closeParenIdx).trim();

        // Collapse malformed spoon generic type parameter strings
        // e.g., "Generic{R extends hudson.model.AbstractBuild}, Generic{R}>}" -> "R"
        paramsSection = paramsSection.replaceAll("Generic\\{([^} ]+)[^}]*\\},\\s*Generic\\{\\1\\}>?\\}?", "$1");

        // Clean up remaining normal generic representations
        // e.g., "Generic{T extends hudson.model.TopLevelItem}" -> "T"
        paramsSection = paramsSection.replaceAll("Generic\\{([^} ]+)[^}]*\\}", "$1");

        // Empty parameter list
        if (paramsSection.isEmpty()) {
            return methodName + "()";
        }

        // Split on commas that are not inside generic brackets
        // Simple approach: split on ',' then trim each part
        String[] rawParams = paramsSection.split(",");
        for (int i = 0; i < rawParams.length; i++) {
            String param = rawParams[i].trim();

            // Remove package qualifiers from the type name.
            // This also works for generic types like java.util.List<java.lang.String>
            // by repeatedly stripping fully‑qualified names.
            // We replace any sequence of characters ending with a dot followed by an identifier
            // with just the identifier.
            param = param.replaceAll("([\\w]+\\.)+([\\w]+)", "$2");

            rawParams[i] = param;
        }

        return methodName + "(" + String.join(",", rawParams) + ")";
    }

    // upWaitQueue(com.tonikelope.megabasterd.Transference) ↔
    // TransferenceManager.downWaitQueue(com.tonikelope.megabasterd.Transference)
    // should become upWaitQueue(Transference) ↔ TransferenceManager.downWaitQueue(Transference)
    String simplifyDuplicatePartners(String duplicationPartners) {
        if (duplicationPartners == null) {
            return null;
        }
        // Split the string on the arrow symbol (↔) to handle each partner separately
        String[] parts = duplicationPartners.split("↔");
        List<String> simplifiedParts = new ArrayList<>();
        // Pattern to capture methodName(params) where params may contain fully‑qualified class names
        Pattern pattern = Pattern.compile("(\\w+)\\(([^)]*)\\)");
        for (String part : parts) {
            String trimmed = part.trim();
            Matcher matcher = pattern.matcher(trimmed);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String methodName = matcher.group(1);
                String params = matcher.group(2);
                // Replace fully‑qualified class names inside the parentheses with simple names
                String simplifiedParams = params.replaceAll("([\\w]+\\.)+([\\w]+)", "$2");
                matcher.appendReplacement(sb, Matcher.quoteReplacement(methodName + "(" + simplifiedParams + ")"));
            }
            matcher.appendTail(sb);
            simplifiedParts.add(sb.toString().trim());
        }
        return String.join(" ↔ ", simplifiedParts);
    }

    String renderDisharmonyChart(String anchorId, String title, List<RankedDisharmony> ranked, int maxPriority) {
        return ""; // empty on purpose; overridden in HtmlReport
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

    static final class DisharmonySpec {
        final String type;
        final String anchorId;
        final String title;
        final boolean methodLevel;

        DisharmonySpec(String type, String anchorId, String title, boolean methodLevel) {
            this.type = type;
            this.anchorId = anchorId;
            this.title = title;
            this.methodLevel = methodLevel;
        }

        String type() {
            return type;
        }

        String anchorId() {
            return anchorId;
        }

        String title() {
            return title;
        }

        boolean methodLevel() {
            return methodLevel;
        }
    }
}
