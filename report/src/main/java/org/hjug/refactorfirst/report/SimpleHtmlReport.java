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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.*;
import org.hjug.feedback.CycleRemovalComputer;
import org.hjug.feedback.CycleRemovalResult;
import org.hjug.git.GitLogReader;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.metrics.DisharmonyMetric;
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
    Graph<String, DefaultWeightedEdge> packageGraph;
    Map<String, AsSubgraph<String, DefaultWeightedEdge>> classCycles;
    Map<String, AsSubgraph<String, DefaultWeightedEdge>> packageCycles;
    Set<String> classesToRemove = Set.of(); // initialize for unit tests
    Set<String> packagesToRemove = Set.of(); // initialize for unit tests
    Set<DefaultWeightedEdge> classRelationshipsToRemove = Set.of();
    Set<DefaultWeightedEdge> packageRelationshipsToRemove = Set.of();

    DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault());

    private final Configuration htmlMinifierConfig = new Configuration.Builder()
            .setKeepHtmlAndHeadOpeningTags(true)
            .setKeepComments(false)
            .setMinifyJs(true)
            .setMinifyCss(true)
            .build();

    @SneakyThrows
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
            File baseDir)
            throws Exception {

        if (testSourceDirectory == null || testSourceDirectory.isEmpty()) {
            testSourceDirectory = "src" + File.separator + "test";
        }

        String projectBaseDir;
        Optional<File> optionalGitDir;
        if (baseDir != null) {
            projectBaseDir = baseDir.getPath();
            optionalGitDir = Optional.ofNullable(GitLogReader.getGitDir(baseDir));
        } else {
            projectBaseDir = Paths.get("").toAbsolutePath().toString();
            optionalGitDir = Optional.ofNullable(GitLogReader.getGitDir(new File(projectBaseDir)));
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(printOpenBodyTag());
        stringBuilder.append(printScripts());
        stringBuilder.append(printBreadcrumbs());
        stringBuilder.append(printProjectHeader(projectName, projectVersion, projectBaseDir));

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
        List<RankedCycle> rankedClassCycles = List.of();
        //        List<RankedCycle> rankedPackageCycles = List.of();
        CodebaseGraphDTO codebaseGraphDTO;
        if (analyzeCycles) {
            log.info("Analyzing Cycles");
            cycleRanker.generateClassReferencesGraph(excludeTests, testSourceDirectory);
            codebaseGraphDTO = cycleRanker.getCodebaseGraphDTO();
            rankedClassCycles = cycleRanker.rankCycles(codebaseGraphDTO.getClassReferencesGraph());
            //            rankedPackageCycles = cycleRanker.rankCycles(codebaseGraphDTO.getPackageReferencesGraph());
        } else {
            codebaseGraphDTO = cycleRanker.generateClassReferencesGraph(excludeTests, testSourceDirectory);
        }

        classGraph = codebaseGraphDTO.getClassReferencesGraph();

        CycleRemovalComputer cycleRemovalComputer = new CycleRemovalComputer();

        CycleRemovalResult classCycleRemovalResult = cycleRemovalComputer.computeCycleRemovalInformation(classGraph);
        Map<DefaultWeightedEdge, Integer> classEdgeCycleCounts = classCycleRemovalResult.getEdgeCycleCounts();
        classRelationshipsToRemove = classCycleRemovalResult.getEdgesToRemove();
        classesToRemove = classCycleRemovalResult.getVertexesToRemove();
        classCycles = classCycleRemovalResult.getCycles();

        packageGraph = codebaseGraphDTO.getPackageReferencesGraph();

        for (DefaultWeightedEdge defaultWeightedEdge : packageGraph.edgeSet()) {
            log.warn(defaultWeightedEdge.toString() + ": " + packageGraph.getEdgeWeight(defaultWeightedEdge));
        }

        CycleRemovalResult packageCycleRemovalResult =
                cycleRemovalComputer.computeCycleRemovalInformation(packageGraph);
        Map<DefaultWeightedEdge, Integer> packageEdgeCycleCounts = packageCycleRemovalResult.getEdgeCycleCounts();
        packageRelationshipsToRemove = packageCycleRemovalResult.getEdgesToRemove();
        packagesToRemove = packageCycleRemovalResult.getVertexesToRemove();
        packageCycles = packageCycleRemovalResult.getCycles();

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

        List<RankedDisharmony> classRelationshipDisharmonies = List.of();
        List<RankedDisharmony> packageRelationshipDisharmonies = List.of();
        try (CostBenefitCalculator costBenefitCalculator =
                new CostBenefitCalculator(projectBaseDir, codebaseGraphDTO.getClassToSourceFilePathMapping())) {
            classRelationshipDisharmonies = costBenefitCalculator.calculateRelationshipCostBenefitValues(
                    classGraph, classEdgeCycleCounts, codebaseGraphDTO, classesToRemove);
            packageRelationshipDisharmonies = costBenefitCalculator.calculateRelationshipCostBenefitValues(
                    packageGraph, packageEdgeCycleCounts, codebaseGraphDTO, packagesToRemove);

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

        boolean hasAnyDisharmony = !classRelationshipsToRemove.isEmpty()
                || !packageRelationshipsToRemove.isEmpty()
                || !rankedClassCycles.isEmpty()
                || !rankedDisharmoniesByAnchor.isEmpty();

        String repoUrl = getRepoUrl(projectBaseDir);

        if (!hasAnyDisharmony) {
            stringBuilder
                    .append("<div style=\"text-align: center;\">Congratulations!  ")
                    .append(projectName)
                    .append(" ")
                    .append(projectVersion)
                    .append(" has no Cycles or Disharmonies!</div>");
            stringBuilder.append(renderClassGraphVisuals(repoUrl, codebaseGraphDTO));
            stringBuilder.append(renderGithubButtons());
            log.info("Done! No Disharmonies found!");
            return stringBuilder;
        }

        stringBuilder.append("<header>\n" + "<nav>\n" + " <ul>\n");
        stringBuilder.append(createMenu(disharmonySpecs, rankedDisharmoniesByAnchor, rankedClassCycles));
        stringBuilder.append("</ul>\n" + "</nav>\n" + "</header>\n");

        log.info("Generating HTML Report");

        stringBuilder.append(renderClassGraphVisuals(repoUrl, codebaseGraphDTO));
        stringBuilder.append("<br/>\n");
        stringBuilder.append(renderGithubButtons());

        stringBuilder.append("<br/>\n");
        if (!classRelationshipDisharmonies.isEmpty()) {
            stringBuilder.append(renderClassEdgeDisharmonies(classRelationshipDisharmonies, repoUrl, codebaseGraphDTO));
            stringBuilder.append("<br/>\n" + "<br/>\n" + "<br/>\n" + "<br/>\n" + "<hr/>\n" + "<br/>\n" + "<br/>\n");
        }

        stringBuilder.append(renderPackageGraphVisuals(repoUrl, codebaseGraphDTO));
        stringBuilder.append("<br/>\n");
        stringBuilder.append(renderGithubButtons());

        if (!packageRelationshipDisharmonies.isEmpty()) {
            stringBuilder.append(
                    renderPackageEdgeDisharmonies(packageRelationshipDisharmonies, repoUrl, codebaseGraphDTO));
            stringBuilder.append("<br/>\n" + "<br/>\n" + "<br/>\n" + "<br/>\n" + "<hr/>\n" + "<br/>\n" + "<br/>\n");
        } else {
            log.info("No Package Relationship Disharmonies found");
        }

        for (DisharmonySpec spec : disharmonySpecs) {
            List<RankedDisharmony> rankedForType = rankedDisharmoniesByAnchor.get(spec.anchorId());
            if (rankedForType != null && !rankedForType.isEmpty()) {
                stringBuilder.append(renderDisharmonyInfo(
                        repoUrl, spec.anchorId(), spec.title(), spec.methodLevel(), showDetails, rankedForType));
                stringBuilder.append("<br/>\n" + "<br/>\n" + "<br/>\n" + "<br/>\n" + "<hr/>\n" + "<br/>\n" + "<br/>\n");
            }
        }

        if (!rankedClassCycles.isEmpty()) {
            stringBuilder.append(renderCycles(rankedClassCycles, repoUrl, codebaseGraphDTO));
        }

        log.debug(stringBuilder.toString());
        return stringBuilder;
    }

    private static String getRepoUrl(String projectBaseDir) throws Exception {
        String repoUrl;
        try (GitLogReader glr = new GitLogReader(new File(projectBaseDir))) {
            repoUrl = glr.getOriginUrl().replace(".git", "") + "/blob/" + glr.getCurrentCommitHash() + "/";
        }
        return repoUrl;
    }

    StringBuilder createMenu(
            List<DisharmonySpec> disharmonySpecs,
            Map<String, List<RankedDisharmony>> rankedDisharmoniesByAnchor,
            List<RankedCycle> rankedCycles) {
        StringBuilder menu = new StringBuilder();

        if (!classRelationshipsToRemove.isEmpty()) {
            menu.append("<li><a href=\"#\">Classes</a>\n" + "<ul>");
            renderClassMapMenu(menu);
            menu.append("<li><a href=\"#CLASSEDGES\">Class Relationships To Remove</a></li>\n");
            menu.append("</ul>\n" + "</li>");
        } else {
            renderClassMapMenu(menu);
        }

        if (!packageRelationshipsToRemove.isEmpty()) {
            menu.append("<li><a href=\"#\">Packages</a>\n" + "<ul>");
            if (!packageGraph.edgeSet().isEmpty()) {
                renderPackageMapMenu(menu);
            }
            menu.append("<li><a href=\"#PACKAGEEDGES\">Package Relationships To Remove</a></li>\n");
            menu.append("</ul>\n" + "</li>");
        } else if (!packageGraph.edgeSet().isEmpty()) {
            renderPackageMapMenu(menu);
        }

        if (!disharmonySpecs.isEmpty()) {
            menu.append("<li><a href=\"#\">Disharmonies</a>\n" + "<ul>");
        }

        for (DisharmonySpec spec : disharmonySpecs) {
            if (rankedDisharmoniesByAnchor.containsKey(spec.anchorId())) {
                menu.append("<li><a href=\"#")
                        .append(spec.anchorId())
                        .append("\">")
                        .append(spec.title())
                        .append("</a></li>\n");
            }
        }

        if (!disharmonySpecs.isEmpty()) {
            menu.append("</ul>\n" + "</li>");
        }

        if (!rankedCycles.isEmpty()) {
            menu.append("<li><a href=\"#\">Cycles</a>\n" + "<ul>");
            menu.append("<li><a href=\"#CYCLES\">Class Cycles</a></li>\n");
            menu.append("<li><a href=\"#CYCLEMAP\">Cycle Map</a></li>\n");
            menu.append("</ul>\n" + "</li>");
        }
        return menu;
    }

    void renderClassMapMenu(StringBuilder stringBuilder) {}

    void renderPackageMapMenu(StringBuilder stringBuilder) {}

    private String renderCycles(List<RankedCycle> rankedCycles, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(renderClassCycleSummary(rankedCycles));

        rankedCycles.stream()
                .limit(1)
                .map((RankedCycle cycle) -> renderSingleCycle(cycle, repoUrl, codebaseGraphDTO))
                .forEach(stringBuilder::append);

        return stringBuilder.toString();
    }

    private String renderClassEdgeDisharmonies(
            List<RankedDisharmony> edgeDisharmonies, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(
                "<div style=\"text-align: center;\"><a id=\"CLASSEDGES\"><h1>Class Relationship Removal Priority</h1></a></div>\n");
        stringBuilder.append("<h2 align=\"center\">Refactor Starting with Priority 1</h2>\n");
        stringBuilder.append("<div style=\"text-align: center;\">\n");
        stringBuilder
                .append("Current Class Cycle Count: ")
                .append(classCycles.size())
                .append("<br>\n");

        stringBuilder
                .append("Number of Class Relationships to Remove: ")
                .append(classRelationshipsToRemove.size())
                .append("<br>\n");
        stringBuilder
                .append("Classes with <strong>*</strong> should be broken apart")
                .append("<br>\n");
        stringBuilder.append("</div>\n");

        // Content
        stringBuilder.append("<div align=\"center\">");
        stringBuilder.append("<table align=\"center\" border=\"5px\">\n");
        stringBuilder.append("<thead>\n<tr>\n");
        for (String heading : getClassRelationshipDisharmonyTableHeadings()) {
            stringBuilder.append("<th>").append(heading).append("</th>\n");
        }
        stringBuilder.append("</thead>\n");

        stringBuilder.append("<tbody>\n");

        for (RankedDisharmony edge : edgeDisharmonies) {
            stringBuilder.append("<tr>\n");

            for (String rowData : getClassRelationshipDisharmony(edge, repoUrl, codebaseGraphDTO)) {
                stringBuilder.append(drawTableCell(rowData));
            }

            stringBuilder.append("</tr>\n");
        }

        stringBuilder.append("</tbody>\n");
        stringBuilder.append("</table>\n");
        stringBuilder.append("</div>\n");

        return stringBuilder.toString();
    }

    private String renderPackageEdgeDisharmonies(
            List<RankedDisharmony> edgeDisharmonies, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(
                "<div style=\"text-align: center;\"><a id=\"PACKAGEEDGES\"><h1>Package Relationship Removal Priority</h1></a></div>\n");
        stringBuilder.append("<h2 align=\"center\">Refactor Starting with Priority 1</h2>\n");
        stringBuilder.append("<div style=\"text-align: center;\">\n");
        stringBuilder
                .append("Current Package Cycle Count: ")
                .append(packageCycles.size())
                .append("<br>\n");

        stringBuilder
                .append("Number of Package Relationships to Remove: ")
                .append(packageRelationshipsToRemove.size())
                .append("<br>\n");
        stringBuilder
                .append("Packages with <strong>*</strong> should be broken apart")
                .append("<br>\n");
        stringBuilder.append("</div>\n");

        // Content
        stringBuilder.append("<div align=\"center\">");
        stringBuilder.append("<table align=\"center\" border=\"5px\">\n");
        stringBuilder.append("<thead>\n<tr>\n");
        for (String heading : getPackageRelationshipDisharmonyTableHeadings()) {
            stringBuilder.append("<th>").append(heading).append("</th>\n");
        }
        stringBuilder.append("</thead>\n");

        stringBuilder.append("<tbody>\n");

        for (RankedDisharmony edge : edgeDisharmonies) {
            stringBuilder.append("<tr>\n");

            for (String rowData : getPackageRelationshipDisharmony(edge, repoUrl, codebaseGraphDTO)) {
                stringBuilder.append(drawTableCell(rowData));
            }

            stringBuilder.append("</tr>\n");
        }

        stringBuilder.append("</tbody>\n");
        stringBuilder.append("</table>\n");
        stringBuilder.append("</div>\n");

        return stringBuilder.toString();
    }

    private String[] getClassRelationshipDisharmonyTableHeadings() {
        return new String[] {
            "Relationship",
            "Priority",
            "In Cycles",
            "Edge<br>Weight",
            "Source<br>Disharmony Count",
            "Target<br>Disharmony Count",
        };
    }

    private String[] getPackageRelationshipDisharmonyTableHeadings() {
        return new String[] {
            "Package Relationship", "Priority", "In Cycles", "Edge<br>Weight", "Class Relationships",
        };
    }

    private String[] getClassRelationshipDisharmony(
            RankedDisharmony edgeInfo, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        return new String[] {
            renderClassEdge(edgeInfo.getEdge(), repoUrl, codebaseGraphDTO),
            String.valueOf(edgeInfo.getPriority()),
            String.valueOf(edgeInfo.getCycleCount()),
            String.valueOf(edgeInfo.getEffortRank()),
            String.valueOf(edgeInfo.getEdgeSourceDisharmonyCount()),
            String.valueOf(edgeInfo.getEdgeTargetDisharmonyCount()),
        };
    }

    private String[] getPackageRelationshipDisharmony(
            RankedDisharmony edgeInfo, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {

        Set<DefaultWeightedEdge> classRelationshipsInPackageRelationship =
                codebaseGraphDTO.getClassRelationshipsInPackageRelationship().get(edgeInfo.getEdge());
        Set<String> classEdges = new HashSet<>();
        for (DefaultWeightedEdge defaultWeightedEdge : classRelationshipsInPackageRelationship) {
            classEdges.add(renderClassEdge(defaultWeightedEdge, repoUrl, codebaseGraphDTO));
        }

        return new String[] {
            renderPackageEdge(edgeInfo.getEdge(), repoUrl, codebaseGraphDTO),
            String.valueOf(edgeInfo.getPriority()),
            String.valueOf(edgeInfo.getCycleCount()),
            String.valueOf(edgeInfo.getEffortRank()),
            String.join("<br>", classEdges),
        };
    }

    private String renderClassCycleSummary(List<RankedCycle> rankedCycles) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("<div style=\"text-align: center;\"><a id=\"CYCLES\"><h1>Class Cycles</h1></a></div>\n");

        stringBuilder.append("<h2 align=\"center\">Class Cycles by the numbers:</h2>\n");
        stringBuilder.append("<div align=\"center\">");
        stringBuilder.append("<table align=\"center\" border=\"5px\">\n");

        // Content
        stringBuilder.append("<thead>\n<tr>\n");
        for (String heading : getClassCycleSummaryTableHeadings()) {
            stringBuilder.append("<th>").append(heading).append("</th>\n");
        }
        stringBuilder.append("</thead>\n");

        stringBuilder.append("<tbody>\n");
        for (RankedCycle cycle : rankedCycles) {
            stringBuilder.append("<tr>\n");

            StringBuilder edges = new StringBuilder();
            for (DefaultWeightedEdge edge : cycle.getEdgeSet()) {

                if (classRelationshipsToRemove.contains(edge)) {
                    stringBuilder.append("<strong>");
                    edges.append(renderClassEdge(edge));
                    stringBuilder.append("</strong>");
                } else {
                    edges.append(renderClassEdge(edge));
                }
                edges.append("</br>\n");
            }

            for (String rowData : getRankedCycleSummaryData(cycle)) {
                stringBuilder.append(drawTableCell(rowData));
            }

            stringBuilder.append("</tr>\n");
        }

        stringBuilder.append("</tbody>\n");
        stringBuilder.append("</table>\n");
        stringBuilder.append("<div>");
        return stringBuilder.toString();
    }

    private String renderClassEdge(DefaultWeightedEdge edge) {
        StringBuilder edgesToCut = new StringBuilder();
        String[] vertexes = extractVertexes(edge);

        String startVertex = vertexes[0].trim();
        String start;
        if (classesToRemove.contains(startVertex)) {
            start = "<strong>" + getClassName(startVertex) + "</strong>";
        } else {
            start = getClassName(startVertex);
        }

        String endVertex = vertexes[1].trim();
        String end;
        if (classesToRemove.contains(endVertex)) {
            end = "<strong>" + getClassName(endVertex) + "</strong>";
        } else {
            end = getClassName(endVertex);
        }

        // &#8594; is HTML "Right Arrow" code
        return edgesToCut
                .append(start + " &#8594; " + end + " : " + (int) classGraph.getEdgeWeight(edge))
                .toString();
    }

    private String renderClassEdge(DefaultWeightedEdge edge, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        StringBuilder edgesToCut = new StringBuilder();
        String[] vertexes = extractVertexes(edge);

        String startVertex = vertexes[0].trim();
        String start;
        if (classesToRemove.contains(startVertex)) {
            start = hyperlinkClass(startVertex, repoUrl, codebaseGraphDTO) + "<strong>*</strong>";
        } else {
            start = hyperlinkClass(startVertex, repoUrl, codebaseGraphDTO);
        }

        String endVertex = vertexes[1].trim();
        String end;
        if (classesToRemove.contains(endVertex)) {
            end = hyperlinkClass(endVertex, repoUrl, codebaseGraphDTO) + "<strong>*</strong>";
        } else {
            end = hyperlinkClass(endVertex, repoUrl, codebaseGraphDTO);
        }

        // &#8594; is HTML "Right Arrow" code
        return edgesToCut
                .append(start + " &#8594; " + end + " : " + (int) classGraph.getEdgeWeight(edge))
                .toString();
    }

    private String renderPackageEdge(DefaultWeightedEdge edge, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        StringBuilder edgesToCut = new StringBuilder();
        String[] vertexes = extractVertexes(edge);

        String startVertex = vertexes[0].trim();
        String start;
        if (packagesToRemove.contains(startVertex)) {
            start = startVertex + "<strong>*</strong>";
        } else {
            start = startVertex;
        }

        String endVertex = vertexes[1].trim();
        String end;
        if (packagesToRemove.contains(endVertex)) {
            end = endVertex + "<strong>*</strong>";
        } else {
            end = endVertex;
        }

        // &#8594; is HTML "Right Arrow" code
        return edgesToCut
                .append(start + " &#8594; " + end + " : " + (int) packageGraph.getEdgeWeight(edge))
                .toString();
    }

    String hyperlinkClass(String className, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        StringBuilder sb = new StringBuilder();
        String path = codebaseGraphDTO.getClassToSourceFilePathMapping().get(className);
        return sb.append("<a href=" + repoUrl + path + " target=\"_blank\">" + getClassName(className) + "</a>")
                .toString();
    }

    private String[] getClassCycleSummaryTableHeadings() {
        return new String[] {"Cycle Name", "Priority", "Class Count", "Relationship Count"};
    }

    private String[] getRankedCycleSummaryData(RankedCycle rankedCycle) {
        return new String[] {
            // "Cycle Name", "Priority", "Class Count", "Relationship Count"
            getClassName(rankedCycle.getCycleName()),
            rankedCycle.getPriority().toString(),
            String.valueOf(rankedCycle.getCycleNodes().size()),
            String.valueOf(rankedCycle.getEdgeSet().size())
        };
    }

    private String renderSingleCycle(RankedCycle cycle, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("<br/>\n");
        stringBuilder.append("<br/>\n");
        stringBuilder.append("<hr/>\n");
        stringBuilder.append("<br/>\n");
        stringBuilder.append("<br/>\n");

        stringBuilder.append("<a id=\"CYCLEMAP\"><h2 align=\"center\">Largest Class Cycle : "
                + getClassName(cycle.getCycleName()) + "</h2></a>\n");
        stringBuilder.append(
                "<h3 align=\"center\">Limiting number of cycles displayed to 1 to keep page load time fast</h3>\n");
        stringBuilder.append(renderClassCycleVisuals(cycle, repoUrl, codebaseGraphDTO));

        stringBuilder.append("<div align=\"center\">");
        stringBuilder.append("<strong>");
        stringBuilder.append(
                "* indicates class to remove, bold text indicates relationships to remove to decompose cycle");
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
            String className;
            if (classesToRemove.contains(vertex)) {
                className = hyperlinkClass(vertex, repoUrl, codebaseGraphDTO) + "<strong>*</strong>";
            } else {
                className = hyperlinkClass(vertex, repoUrl, codebaseGraphDTO);
            }

            stringBuilder.append(drawTableCell(className));
            StringBuilder edges = new StringBuilder();
            for (DefaultWeightedEdge edge : cycle.getEdgeSet()) {
                if (edge.toString().startsWith("(" + vertex + " :")) {

                    if (classRelationshipsToRemove.contains(edge)) {
                        edges.append("<strong>");
                        edges.append(renderClassEdge(edge));
                        edges.append("</strong>");
                    } else {
                        edges.append(renderClassEdge(edge));
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

    public String renderClassGraphVisuals(String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        return ""; // empty on purpose
    }

    public String renderPackageGraphVisuals(String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        return ""; // empty on purpose
    }

    public String renderClassCycleVisuals(RankedCycle cycle, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
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

    public String printProjectHeader(String projectName, String projectVersion, String projectBaseDir)
            throws Exception {
        String repoUrl = getRepoUrl(projectBaseDir);

        return "</div>\n" + "      <div class=\"xright\">      </div>\n"
                + "    </div>\n"
                + "    <div id=\"bodyColumn\">\n"
                + "      <div id=\"contentBox\">\n"
                + "<h2 align=\"center\"><a href=\"https://github.com/refactorfirst/refactorfirst\" target=\"_blank\" "
                + "title=\"Learn about RefactorFirst\" aria-label=\"RefactorFirst\">RefactorFirst</a> Report for "
                + "<a href=" + repoUrl + " target=\"_blank\">" + projectName + " "
                + projectVersion + "</a></h2>\n";
    }

    public String printProjectFooter() {
        return "      <div class=\"clear\">\n" + "        <hr/>\n" + "      </div>\n"
                + "<span id=\"publishDate\">Last Published: "
                + formatter.format(Instant.now())
                + "      <div class=\"clear\">\n"
                + "        <hr/>\n" + "      </div>\n" + "</span>";
    }

    String renderGithubButtons() {
        return "<div align=\"center\">\n" + "Show RefactorFirst some &#10084;&#65039;\n"
                + "<br/>\n"
                + "<a href=\"https://github.com/refactorfirst/refactorfirst\" aria-label=\"Star refactorfirst/refactorfirst on GitHub\">Star</a>\n"
                + "<a href=\"https://github.com/refactorfirst/refactorfirst/fork\" aria-label=\"Fork refactorfirst/refactorfirst on GitHub\">Fork</a>\n"
                + "<a href=\"https://github.com/refactorfirst/refactorfirst/subscription\" aria-label=\"Watch refactorfirst/refactorfirst on GitHub\">Watch</a>\n"
                + "<a href=\"https://github.com/refactorfirst/refactorfirst/issues\" aria-label=\"Issue refactorfirst/refactorfirst on GitHub\">Issue</a>\n"
                + "<a href=\"https://github.com/sponsors/jimbethancourt\" aria-label=\"Sponsor @jimbethancourt on GitHub\">Sponsor</a>\n"
                + "</div>";
    }

    String getOutputName() {
        // This report will generate simple-report.html when invoked in a project with `mvn site`
        return "refactor-first-report";
    }

    /**
     * Renders a table section for any disharmony type.
     * Column headers are derived from the ranked metrics carried on each RankedDisharmony.
     */
    public String renderDisharmonyInfo(
            String repoUrl,
            String anchorId,
            String title,
            boolean methodLevel,
            boolean showDetails,
            List<RankedDisharmony> ranked) {
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
        List<DisharmonyMetric> sampleMetrics = ranked.get(0).getRankedMetrics();

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
            for (DisharmonyMetric m : sampleMetrics) {
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
            sb.append(drawTableCell(
                    "<a href=" + repoUrl + rd.getPath() + " target=\"_blank\">" + rd.getFileName() + "</a>"));
            if (methodLevel) {
                String sig = rd.getMethodSignature();
                if (!showDetails && sig != null) {
                    // simplify the method signature to just the name and type
                    sig = getSimpleMethodSignature(sig);
                }
                sb.append(drawTableCell(sig != null ? sig.replace("<", "&lt;").replace(">", "&gt;") : ""));
            }
            sb.append(drawTableCell(rd.getPriority().toString()));
            if (showDetails) {
                sb.append(drawTableCell(rd.getRawPriority().toString()));
                sb.append(drawTableCell(rd.getDescription() != null ? rd.getDescription() : ""));
            }
            sb.append(drawTableCell(rd.getChangePronenessRank().toString()));
            sb.append(drawTableCell(rd.getEffortRank().toString()));
            if (showDetails) {
                for (DisharmonyMetric m : rd.getRankedMetrics()) {
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
