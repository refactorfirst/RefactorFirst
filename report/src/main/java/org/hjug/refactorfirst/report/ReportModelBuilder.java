package org.hjug.refactorfirst.report;

import java.io.File;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.CostBenefitCalculator;
import org.hjug.cbc.CycleRanker;
import org.hjug.cbc.RankedCycle;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.feedback.CycleRemovalComputer;
import org.hjug.feedback.CycleRemovalResult;
import org.hjug.git.GitLogReader;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.VertexInfo;
import org.hjug.graphbuilder.metrics.DisharmonyMetric;
import org.hjug.metrics.DisharmonyInstance;
import org.hjug.refactorfirst.report.model.ChartJsBubbleDTO;
import org.hjug.refactorfirst.report.model.ClassCyclesDTO;
import org.hjug.refactorfirst.report.model.ClassRelationshipDTO;
import org.hjug.refactorfirst.report.model.ClassRelationshipsToRemoveDTO;
import org.hjug.refactorfirst.report.model.CycleBreakdownRowDTO;
import org.hjug.refactorfirst.report.model.CycleSummaryDTO;
import org.hjug.refactorfirst.report.model.DisharmonyChartDTO;
import org.hjug.refactorfirst.report.model.DisharmonySectionDTO;
import org.hjug.refactorfirst.report.model.DisharmonyTableCellDTO;
import org.hjug.refactorfirst.report.model.DisharmonyTableDTO;
import org.hjug.refactorfirst.report.model.DisharmonyTableRowDTO;
import org.hjug.refactorfirst.report.model.GraphVisualDTO;
import org.hjug.refactorfirst.report.model.HierarchyDTO;
import org.hjug.refactorfirst.report.model.LargestCycleDTO;
import org.hjug.refactorfirst.report.model.PackageRelationshipDTO;
import org.hjug.refactorfirst.report.model.PackageRelationshipsToRemoveDTO;
import org.hjug.refactorfirst.report.model.ProjectMetadataDTO;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 * Single source of truth for turning an analyzed codebase into the {@link RefactorFirstReportDTO}
 * consumed by the JSON report, the Mustache templates and later the viewer.
 *
 * <p>Extracted from {@link JsonGenerator} in Phase P1 of the feature-parity plan so all report
 * consumers share one analysis → DTO code path.
 */
@Slf4j
public class ReportModelBuilder {
    /** Encodes each source path segment using the same rules as RF.sourceUrl. */
    static String sourceUrl(String repoUrl, String path) {
        if (repoUrl == null || path == null) return null;
        String relative = path.startsWith("/") ? path.substring(1) : path;
        String encoded = Arrays.stream(relative.split("/", -1))
                .map(segment -> java.net.URLEncoder.encode(segment, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("+", "%20")
                        .replace("%21", "!")
                        .replace("%27", "'")
                        .replace("%28", "(")
                        .replace("%29", ")")
                        .replace("%7E", "~"))
                .collect(Collectors.joining("/"));
        return (repoUrl.endsWith("/") ? repoUrl : repoUrl + "/") + encoded;
    }

    private Clock clock = Clock.systemDefaultZone();

    Graph<VertexInfo, DefaultWeightedEdge> classGraph;
    Graph<VertexInfo, DefaultWeightedEdge> packageGraph;
    Map<VertexInfo, AsSubgraph<VertexInfo, DefaultWeightedEdge>> classCycles = Map.of();
    Map<VertexInfo, AsSubgraph<VertexInfo, DefaultWeightedEdge>> packageCycles = Map.of();
    Set<VertexInfo> classesToRemove = Set.of(); // initialize for unit tests
    Set<VertexInfo> packagesToRemove = Set.of(); // initialize for unit tests
    Set<DefaultWeightedEdge> classRelationshipsToRemove = Set.of();
    Set<DefaultWeightedEdge> packageRelationshipsToRemove = Set.of();
    Map<DefaultWeightedEdge, Integer> classEdgeCycleCounts = Map.of();
    Map<DefaultWeightedEdge, Integer> packageEdgeCycleCounts = Map.of();

    List<RankedCycle> rankedClassCycles = List.of();
    Map<String, List<RankedDisharmony>> rankedDisharmoniesByAnchor = new LinkedHashMap<>();
    List<RankedDisharmony> classRelationshipDisharmonies = List.of();
    List<RankedDisharmony> packageRelationshipDisharmonies = List.of();

    int dotGraphThreshold = 4000;

    DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault());

    CodebaseGraphDTO codebaseGraphDTO;

    /** Returns a builder configured with the given clock (used by deterministic tests). */
    public ReportModelBuilder withClock(Clock clock) {
        this.clock = clock;
        return this;
    }

    /** Returns the clock the builder timestamps scans with. */
    public Clock getClock() {
        return clock;
    }

    /** Returns the class graph the last analysis produced. */
    public Graph<VertexInfo, DefaultWeightedEdge> getClassGraph() {
        return classGraph;
    }

    /** Returns the package graph the last analysis produced. */
    public Graph<VertexInfo, DefaultWeightedEdge> getPackageGraph() {
        return packageGraph;
    }

    /** Returns the class cycles the last analysis produced. */
    public Map<VertexInfo, AsSubgraph<VertexInfo, DefaultWeightedEdge>> getClassCycles() {
        return classCycles;
    }

    /** Returns the package cycles the last analysis produced. */
    public Map<VertexInfo, AsSubgraph<VertexInfo, DefaultWeightedEdge>> getPackageCycles() {
        return packageCycles;
    }

    /** Returns the classes marked for removal by the last analysis. */
    public Set<VertexInfo> getClassesToRemove() {
        return classesToRemove;
    }

    /** Returns the packages marked for removal by the last analysis. */
    public Set<VertexInfo> getPackagesToRemove() {
        return packagesToRemove;
    }

    /** Returns the class relationships marked for removal by the last analysis. */
    public Set<DefaultWeightedEdge> getClassRelationshipsToRemove() {
        return classRelationshipsToRemove;
    }

    /** Returns the package relationships marked for removal by the last analysis. */
    public Set<DefaultWeightedEdge> getPackageRelationshipsToRemove() {
        return packageRelationshipsToRemove;
    }

    /** Returns the class-edge cycle membership map from the last analysis. */
    public Map<DefaultWeightedEdge, Integer> getClassEdgeCycleCounts() {
        return classEdgeCycleCounts;
    }

    /** Returns the package-edge cycle membership map from the last analysis. */
    public Map<DefaultWeightedEdge, Integer> getPackageEdgeCycleCounts() {
        return packageEdgeCycleCounts;
    }

    /** Returns the ranked class cycles from the last analysis. */
    public List<RankedCycle> getRankedClassCycles() {
        return rankedClassCycles;
    }

    /** Returns the ranked disharmonies keyed by anchor from the last analysis. */
    public Map<String, List<RankedDisharmony>> getRankedDisharmoniesByAnchor() {
        return rankedDisharmoniesByAnchor;
    }

    /** Returns the class-relationship disharmonies from the last analysis. */
    public List<RankedDisharmony> getClassRelationshipDisharmonies() {
        return classRelationshipDisharmonies;
    }

    /** Returns the package-relationship disharmonies from the last analysis. */
    public List<RankedDisharmony> getPackageRelationshipDisharmonies() {
        return packageRelationshipDisharmonies;
    }

    /** Returns the class-graph dot threshold from the last analysis. */
    public int getDotGraphThreshold() {
        return dotGraphThreshold;
    }

    /** Returns the codebase graph DTO from the last analysis. */
    public CodebaseGraphDTO getCodebaseGraphDTO() {
        return codebaseGraphDTO;
    }

    /**
     * Analyzes a project and converts its findings into serializable report data, using the
     * configured (or default) clock for the scan timestamp.
     */
    public RefactorFirstReportDTO build(
            boolean showDetails,
            int edgeAnalysisCount,
            boolean analyzeCycles,
            boolean excludeTests,
            String testSourceDirectory,
            String projectName,
            String projectVersion,
            File baseDir)
            throws Exception {
        return build(
                showDetails,
                edgeAnalysisCount,
                analyzeCycles,
                excludeTests,
                testSourceDirectory,
                projectName,
                projectVersion,
                baseDir,
                clock);
    }

    /**
     * Analyzes a project and converts its findings into serializable report data.
     *
     * @param clock clock used to timestamp the scan (deterministic under test)
     */
    public RefactorFirstReportDTO build(
            boolean showDetails,
            int edgeAnalysisCount,
            boolean analyzeCycles,
            boolean excludeTests,
            String testSourceDirectory,
            String projectName,
            String projectVersion,
            File baseDir,
            Clock clock)
            throws Exception {

        // A reused builder must not carry findings (or their source paths) from a prior
        // analysis into the next report build.
        rankedDisharmoniesByAnchor = new LinkedHashMap<>();
        rankedClassCycles = List.of();
        classEdgeCycleCounts = Map.of();
        packageEdgeCycleCounts = Map.of();
        classRelationshipsToRemove = Set.of();
        packageRelationshipsToRemove = Set.of();
        classesToRemove = Set.of();
        packagesToRemove = Set.of();
        classGraph = null;
        packageGraph = null;
        classCycles = Map.of();
        packageCycles = Map.of();
        classRelationshipDisharmonies = List.of();
        packageRelationshipDisharmonies = List.of();
        codebaseGraphDTO = null;

        if (testSourceDirectory == null || testSourceDirectory.isEmpty()) {
            testSourceDirectory = "src" + File.separator + "test";
        }

        String projectBaseDir;
        java.util.Optional<File> optionalGitDir;
        if (baseDir != null) {
            projectBaseDir = baseDir.getPath();
            optionalGitDir = java.util.Optional.ofNullable(GitLogReader.getGitDir(baseDir));
        } else {
            projectBaseDir = java.nio.file.Path.of("").toAbsolutePath().toString();
            optionalGitDir = java.util.Optional.ofNullable(GitLogReader.getGitDir(new File(projectBaseDir)));
        }

        String scanTimestamp = formatter.format(clock.instant());

        if (optionalGitDir.isEmpty()) {
            log.info("Done! No Git repository found!");
            return RefactorFirstReportDTO.builder()
                    .project(ProjectMetadataDTO.builder()
                            .name(projectName)
                            .version(projectVersion)
                            .baseDir(projectBaseDir)
                            .repoUrl("")
                            .scanTimestamp(scanTimestamp)
                            .hasAnyDisharmony(false)
                            .analysisFailed(true)
                            .build())
                    .build();
        }

        try {
            File gitDir = optionalGitDir.get();
            String parentOfGitDir = gitDir.getParentFile().getPath();

            CycleRanker cycleRanker = new CycleRanker(projectBaseDir, parentOfGitDir);
            CodebaseGraphDTO codebaseGraphDTO;
            if (analyzeCycles) {
                cycleRanker.generateClassReferencesGraph(excludeTests, testSourceDirectory);
                codebaseGraphDTO = cycleRanker.getCodebaseGraphDTO();
                rankedClassCycles = cycleRanker.rankCycles(codebaseGraphDTO.getClassReferencesGraph());
            } else {
                codebaseGraphDTO = cycleRanker.generateClassReferencesGraph(excludeTests, testSourceDirectory);
            }

            classGraph = codebaseGraphDTO.getClassReferencesGraph();
            CycleRemovalComputer cycleRemovalComputer = new CycleRemovalComputer();

            CycleRemovalResult classCycleRemovalResult =
                    cycleRemovalComputer.computeCycleRemovalInformation(classGraph);
            this.classEdgeCycleCounts = classCycleRemovalResult.getEdgeCycleCounts();
            this.classRelationshipsToRemove = classCycleRemovalResult.getEdgesToRemove();
            this.classesToRemove = classCycleRemovalResult.getVertexesToRemove();
            this.classCycles = classCycleRemovalResult.getCycles();

            packageGraph = codebaseGraphDTO.getPackageReferencesGraph();
            CycleRemovalResult packageCycleRemovalResult =
                    cycleRemovalComputer.computeCycleRemovalInformation(packageGraph);
            this.packageEdgeCycleCounts = packageCycleRemovalResult.getEdgeCycleCounts();
            this.packageRelationshipsToRemove = packageCycleRemovalResult.getEdgesToRemove();
            this.packagesToRemove = packageCycleRemovalResult.getVertexesToRemove();
            this.packageCycles = packageCycleRemovalResult.getCycles();
            this.codebaseGraphDTO = codebaseGraphDTO;

            try (CostBenefitCalculator costBenefitCalculator =
                    new CostBenefitCalculator(projectBaseDir, codebaseGraphDTO.getClassToSourceFilePathMapping())) {
                this.packageRelationshipDisharmonies = costBenefitCalculator.calculateRelationshipCostBenefitValues(
                        packageGraph,
                        this.packageEdgeCycleCounts,
                        codebaseGraphDTO,
                        this.packagesToRemove,
                        this.packageCycles,
                        List.of());
                this.classRelationshipDisharmonies = costBenefitCalculator.calculateRelationshipCostBenefitValues(
                        classGraph,
                        this.classEdgeCycleCounts,
                        codebaseGraphDTO,
                        this.classesToRemove,
                        this.packageCycles,
                        this.packageRelationshipDisharmonies);

                for (SimpleHtmlReport.DisharmonySpec spec : SimpleHtmlReport.DISHARMONY_SPECS) {
                    List<DisharmonyInstance> instances = spec.methodLevel()
                            ? costBenefitCalculator.getMethodDisharmonies(codebaseGraphDTO, spec.type())
                            : costBenefitCalculator.getClassDisharmonies(codebaseGraphDTO, spec.type());
                    if (!instances.isEmpty()) {
                        rankedDisharmoniesByAnchor.put(
                                spec.anchorId(), costBenefitCalculator.calculateDisharmonyCostBenefitValues(instances));
                    }
                }
            }

            boolean hasAnyDisharmony = !classRelationshipsToRemove.isEmpty()
                    || !packageRelationshipsToRemove.isEmpty()
                    || !rankedClassCycles.isEmpty()
                    || !rankedDisharmoniesByAnchor.isEmpty();

            String repoUrl = SimpleHtmlReport.getRepoUrl(projectBaseDir);

            ProjectMetadataDTO projectMetadata = ProjectMetadataDTO.builder()
                    .name(projectName)
                    .version(projectVersion)
                    .repoUrl(repoUrl)
                    .baseDir(projectBaseDir)
                    .scanTimestamp(scanTimestamp)
                    .hasAnyDisharmony(hasAnyDisharmony)
                    .build();

            // 1. Class Map
            int classCount = classGraph.vertexSet().size();
            int classRelationshipCount = classGraph.edgeSet().size();
            HierarchyDTO classGraphHierarchy = HierarchyBuilder.buildClassHierarchy(
                    classGraph, codebaseGraphDTO, classesToRemove, packagesToRemove, classRelationshipsToRemove);
            GraphVisualDTO classMapDTO = GraphVisualDTO.builder()
                    .graphId("classGraph")
                    .classCount(classCount)
                    .relationshipCount(classRelationshipCount)
                    .dotThreshold(dotGraphThreshold)
                    .dotThresholdExceeded(classCount + classRelationshipCount >= dotGraphThreshold)
                    .hierarchy(classGraphHierarchy)
                    .build();

            // 2. Class Relationships To Remove
            List<ClassRelationshipDTO> classRelList = new ArrayList<>();
            for (RankedDisharmony edgeInfo : classRelationshipDisharmonies) {
                String[] vertexes = SimpleHtmlReport.extractVertexes(edgeInfo.getEdge());
                String startVertex = vertexes[0].trim();
                String endVertex = vertexes[1].trim();

                classRelList.add(ClassRelationshipDTO.builder()
                        .sourceClass(startVertex)
                        .targetClass(endVertex)
                        .sourceClassPath(classSourcePath(startVertex, codebaseGraphDTO))
                        .targetClassPath(classSourcePath(endVertex, codebaseGraphDTO))
                        .simpleSourceClassName(getClassName(startVertex))
                        .simpleTargetClassName(getClassName(endVertex))
                        .sourceMarked(classesToRemove.contains(VertexInfo.forName(startVertex)))
                        .targetMarked(classesToRemove.contains(VertexInfo.forName(endVertex)))
                        .weight((int) classGraph.getEdgeWeight(edgeInfo.getEdge()))
                        .priority(edgeInfo.getPriority())
                        .cycleCount(edgeInfo.getCycleCount())
                        .effortRank(edgeInfo.getEffortRank())
                        .alsoRemovesPackageRelationship(edgeInfo.isPackageRelationshipShouldBeRemoved())
                        .packageCycleCount(edgeInfo.getPackageCycleCount())
                        .build());
            }

            ClassRelationshipsToRemoveDTO classRelationshipsToRemoveDTO = ClassRelationshipsToRemoveDTO.builder()
                    .cycleCount(classCycles.size())
                    .relationshipsToRemoveCount(classRelationshipsToRemove.size())
                    .hasRelationships(!classRelationshipsToRemove.isEmpty())
                    .relationships(classRelList)
                    .build();

            // 3. Package Map
            int packageCount = packageGraph.vertexSet().size();
            int packageRelationshipCount = packageGraph.edgeSet().size();
            boolean hasPackageEdges = !packageGraph.edgeSet().isEmpty();
            HierarchyDTO packageGraphHierarchy = HierarchyBuilder.buildPackageHierarchy(
                    packageGraph, codebaseGraphDTO, packagesToRemove, packageRelationshipsToRemove);
            GraphVisualDTO packageMapDTO = GraphVisualDTO.builder()
                    .hasEdges(hasPackageEdges)
                    .graphId("packageGraph")
                    .classCount(packageCount)
                    .relationshipCount(packageRelationshipCount)
                    .dotThreshold(dotGraphThreshold)
                    .dotThresholdExceeded(packageCount + packageRelationshipCount >= dotGraphThreshold)
                    .hierarchy(packageGraphHierarchy)
                    .build();

            // 4. Package Relationships To Remove
            List<PackageRelationshipDTO> packageRelList = new ArrayList<>();
            for (RankedDisharmony edgeInfo : packageRelationshipDisharmonies) {
                String[] vertexes = SimpleHtmlReport.extractVertexes(edgeInfo.getEdge());
                String startVertex = vertexes[0].trim();
                String endVertex = vertexes[1].trim();

                // Extract package paths from class relationships in the package edge
                String sourcePackagePath = "";
                String targetPackagePath = "";
                Set<DefaultWeightedEdge> classRelationshipsInPackageRelationship = codebaseGraphDTO
                        .getClassRelationshipsInPackageRelationship()
                        .get(edgeInfo.getEdge());
                if (classRelationshipsInPackageRelationship != null) {
                    for (DefaultWeightedEdge classEdge : classRelationshipsInPackageRelationship) {
                        String[] classVertexes = SimpleHtmlReport.extractVertexes(classEdge);
                        String classStart = classVertexes[0].trim();
                        String classEnd = classVertexes[1].trim();

                        // Get source file path for each class vertex
                        String classStartPath = codebaseGraphDTO
                                .getClassToSourceFilePathMapping()
                                .get(classStart);
                        String classEndPath = codebaseGraphDTO
                                .getClassToSourceFilePathMapping()
                                .get(classEnd);

                        // Derive package directory from source file path
                        if (classStartPath != null && !classStartPath.isBlank()) {
                            int lastSlash = classStartPath.lastIndexOf('/');
                            if (lastSlash != -1) {
                                String packageDir = classStartPath.substring(0, lastSlash + 1);
                                // Check if this class belongs to the source package
                                if (isClassInPackage(classStart, startVertex, codebaseGraphDTO)) {
                                    sourcePackagePath = packageDir;
                                }
                                // Check if this class belongs to the target package
                                if (isClassInPackage(classStart, endVertex, codebaseGraphDTO)) {
                                    targetPackagePath = packageDir;
                                }
                            }
                        }

                        if (classEndPath != null && !classEndPath.isBlank()) {
                            int lastSlash = classEndPath.lastIndexOf('/');
                            if (lastSlash != -1) {
                                String packageDir = classEndPath.substring(0, lastSlash + 1);
                                // Check if this class belongs to the source package
                                if (isClassInPackage(classEnd, startVertex, codebaseGraphDTO)) {
                                    sourcePackagePath = packageDir;
                                }
                                // Check if this class belongs to the target package
                                if (isClassInPackage(classEnd, endVertex, codebaseGraphDTO)) {
                                    targetPackagePath = packageDir;
                                }
                            }
                        }
                    }
                }

                // Fallback to package name conversion if no class edges found
                if (sourcePackagePath.isBlank()) {
                    sourcePackagePath = startVertex.replace('.', '/');
                }
                if (targetPackagePath.isBlank()) {
                    targetPackagePath = endVertex.replace('.', '/');
                }

                List<ClassRelationshipDTO> breakClassRels = buildClassRelationshipsToBreakPackage(
                        classRelationshipsInPackageRelationship, codebaseGraphDTO);

                packageRelList.add(PackageRelationshipDTO.builder()
                        .sourcePackage(startVertex)
                        .targetPackage(endVertex)
                        .sourcePackagePath(sourcePackagePath)
                        .targetPackagePath(targetPackagePath)
                        .sourceMarked(packagesToRemove.contains(VertexInfo.forName(startVertex)))
                        .targetMarked(packagesToRemove.contains(VertexInfo.forName(endVertex)))
                        .weight((int) packageGraph.getEdgeWeight(edgeInfo.getEdge()))
                        .priority(edgeInfo.getPriority())
                        .cycleCount(edgeInfo.getCycleCount())
                        .effortRank(edgeInfo.getEffortRank())
                        .classRelationshipsToBreakPackage(breakClassRels)
                        .build());
            }

            PackageRelationshipsToRemoveDTO packageRelationshipsToRemoveDTO = PackageRelationshipsToRemoveDTO.builder()
                    .cycleCount(packageCycles.size())
                    .relationshipsToRemoveCount(packageRelationshipsToRemove.size())
                    .hasRelationships(!packageRelationshipsToRemove.isEmpty())
                    .relationships(packageRelList)
                    .build();

            // 5. Disharmonies
            List<DisharmonySectionDTO> disharmonySections = new ArrayList<>();
            for (SimpleHtmlReport.DisharmonySpec spec : SimpleHtmlReport.DISHARMONY_SPECS) {
                List<RankedDisharmony> ranked = rankedDisharmoniesByAnchor.get(spec.anchorId());
                if (ranked != null && !ranked.isEmpty()) {
                    disharmonySections.add(buildDisharmonySection(spec, showDetails, ranked));
                }
            }

            // 6. Cycles
            List<CycleSummaryDTO> cycleSummaries = new ArrayList<>();
            for (RankedCycle cycle : rankedClassCycles) {
                String[] summaryData = SimpleHtmlReport.getRankedCycleSummaryData(cycle);
                cycleSummaries.add(CycleSummaryDTO.builder()
                        .cycleName(summaryData[0])
                        .priority(Integer.parseInt(summaryData[1]))
                        .classCount(Integer.parseInt(summaryData[2]))
                        .relationshipCount(Integer.parseInt(summaryData[3]))
                        .build());
            }

            LargestCycleDTO largestCycleDTO = null;
            if (!rankedClassCycles.isEmpty()) {
                RankedCycle largestCycle = rankedClassCycles.get(0);
                String cycleName = getClassName(largestCycle.getCycleName());
                String cycleIdentifier = graphIdentifier(cycleName);
                int cCount = largestCycle.getCycleNodes().size();
                int rCount = largestCycle.getEdgeSet().size();
                HierarchyDTO cycleHierarchy = HierarchyBuilder.buildCycleHierarchy(
                        classGraph,
                        codebaseGraphDTO,
                        classesToRemove,
                        packagesToRemove,
                        classRelationshipsToRemove,
                        largestCycle);

                List<CycleBreakdownRowDTO> breakdown = new ArrayList<>();
                for (String vertex : largestCycle.getVertexSet()) {
                    boolean marked = classesToRemove.contains(VertexInfo.forName(vertex));

                    StringBuilder edges = new StringBuilder();
                    for (DefaultWeightedEdge edge : largestCycle.getEdgeSet()) {
                        if (edge.toString().startsWith("(" + vertex + " :")) {
                            if (classRelationshipsToRemove.contains(edge)) {
                                edges.append("<strong>");
                                edges.append(renderClassEdge(edge) + "*");
                                edges.append("</strong>");
                            } else {
                                edges.append(renderClassEdge(edge));
                            }
                            edges.append("<br/>\n");
                        }
                    }
                    // The row carries the simple class name, the source path relative to the
                    // project root and the removal marker; viewers build the URL from
                    // project.repoUrl
                    breakdown.add(CycleBreakdownRowDTO.builder()
                            .className(getClassName(vertex))
                            .classPath(classSourcePath(vertex, codebaseGraphDTO))
                            .marked(marked)
                            .edgesHtml(edges.toString())
                            .build());
                }

                largestCycleDTO = LargestCycleDTO.builder()
                        .hasCycleMap(true)
                        .cycleName(cycleName)
                        .cycleIdentifier(cycleIdentifier)
                        .classCount(cCount)
                        .relationshipCount(rCount)
                        .dotThresholdExceeded(cCount + rCount >= dotGraphThreshold)
                        .hierarchy(cycleHierarchy)
                        .breakdown(breakdown)
                        .build();
            }

            ClassCyclesDTO classCyclesDTO = ClassCyclesDTO.builder()
                    .hasCycles(!rankedClassCycles.isEmpty())
                    .summary(cycleSummaries)
                    .largestCycle(largestCycleDTO)
                    .build();

            return RefactorFirstReportDTO.builder()
                    .project(projectMetadata)
                    .classMap(classMapDTO)
                    .classRelationshipsToRemove(classRelationshipsToRemoveDTO)
                    .packageMap(packageMapDTO)
                    .packageRelationshipsToRemove(packageRelationshipsToRemoveDTO)
                    .hasDisharmonies(!disharmonySections.isEmpty())
                    .disharmonies(disharmonySections)
                    .classCycles(classCyclesDTO)
                    .build();
        } catch (Exception e) {
            log.warn("Analysis failed or git history unavailable", e);
            return RefactorFirstReportDTO.builder()
                    .project(ProjectMetadataDTO.builder()
                            .name(projectName)
                            .version(projectVersion)
                            .baseDir(projectBaseDir)
                            .repoUrl("")
                            .scanTimestamp(scanTimestamp)
                            .hasAnyDisharmony(false)
                            .analysisFailed(true)
                            .build())
                    .build();
        }
    }

    /**
     * Converts ranked instances of one disharmony type into chart and table data. Table cells
     * carry only plain text and paths relative to the project root; viewers combine the paths
     * with the repository URL from the project metadata.
     */
    public DisharmonySectionDTO buildDisharmonySection(
            SimpleHtmlReport.DisharmonySpec spec, boolean showDetails, List<RankedDisharmony> ranked) {

        int maxPriority = ranked.get(ranked.size() - 1).getPriority();

        // Chart
        List<ChartJsBubbleDTO> bubbles = new ArrayList<>();
        for (RankedDisharmony rd : ranked) {
            String label = rd.getFileName() != null
                    ? rd.getFileName()
                    : rd.getRawPriority().toString();
            bubbles.add(createBubble(
                    rd.getFileName(),
                    label,
                    rd.getEffortRank(),
                    rd.getChangePronenessRank(),
                    rd.getPriority(),
                    maxPriority,
                    rd.getPath()));
        }

        DisharmonyChartDTO chartDTO = DisharmonyChartDTO.builder()
                .canvasId("chart_" + spec.anchorId())
                .xAxisLabel("Effort to refactor")
                .yAxisLabel("Relative churn (impact)")
                .bubbles(bubbles)
                .build();

        // Table
        List<String> headers = new ArrayList<>();
        headers.add("Class");
        if (spec.methodLevel()) {
            headers.add("Method");
        }
        headers.add("Priority");
        if (showDetails) {
            headers.add("Raw Priority");
            headers.add("Description");
        }
        headers.add("Change Proneness Rank");
        headers.add("Effort Rank");
        if (showDetails && !ranked.isEmpty()) {
            for (DisharmonyMetric m : ranked.get(0).getRankedMetrics()) {
                headers.add(m.getName());
                headers.add(m.getName() + " Rank");
            }
        }
        boolean showPartners = !ranked.isEmpty() && ranked.get(0).getDuplicationPartners() != null;
        if (showPartners) {
            headers.add("Duplicate Partners");
        }
        headers.add("Most Recent Commit Date");
        headers.add("Commit Count");
        if (showDetails) {
            headers.add("Date of First Commit");
            headers.add("Full Path");
        }

        List<DisharmonyTableRowDTO> rows = new ArrayList<>();
        for (RankedDisharmony rd : ranked) {
            List<DisharmonyTableCellDTO> cells = new ArrayList<>();

            // Class link: the cell carries the plain file name and the source path
            // relative to the project root; viewers build the URL from project.repoUrl
            cells.add(DisharmonyTableCellDTO.builder()
                    .content(SimpleHtmlReport.escapeHtmlLabel(rd.getFileName()))
                    .path(rd.getPath())
                    .align("left")
                    .build());

            // Method
            if (spec.methodLevel()) {
                String sig = rd.getMethodSignature();
                if (!showDetails && sig != null) {
                    sig = SimpleHtmlReport.getSimpleMethodSignature(sig);
                }
                cells.add(DisharmonyTableCellDTO.builder()
                        .content(SimpleHtmlReport.escapeHtmlLabel(sig))
                        .align("left")
                        .build());
            }

            // Priority
            cells.add(DisharmonyTableCellDTO.builder()
                    .content(rd.getPriority().toString())
                    .align("right")
                    .build());

            if (showDetails) {
                cells.add(DisharmonyTableCellDTO.builder()
                        .content(rd.getRawPriority().toString())
                        .align("right")
                        .build());
                cells.add(DisharmonyTableCellDTO.builder()
                        .content(SimpleHtmlReport.escapeHtmlLabel(rd.getDescription()))
                        .align("left")
                        .build());
            }

            // Change Proneness & Effort
            cells.add(DisharmonyTableCellDTO.builder()
                    .content(rd.getChangePronenessRank().toString())
                    .align("right")
                    .build());
            cells.add(DisharmonyTableCellDTO.builder()
                    .content(rd.getEffortRank().toString())
                    .align("right")
                    .build());

            if (showDetails) {
                for (DisharmonyMetric m : rd.getRankedMetrics()) {
                    double v = m.getValue();
                    String formatted = v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
                    cells.add(DisharmonyTableCellDTO.builder()
                            .content(formatted)
                            .align("right")
                            .build());
                    cells.add(DisharmonyTableCellDTO.builder()
                            .content(m.getRank() != null ? m.getRank().toString() : "")
                            .align("right")
                            .build());
                }
            }

            if (showPartners) {
                String duplicationPartners = rd.getDuplicationPartners();
                if (!showDetails && duplicationPartners != null) {
                    duplicationPartners = SimpleHtmlReport.simplifyDuplicatePartners(duplicationPartners);
                }
                cells.add(DisharmonyTableCellDTO.builder()
                        .content(
                                duplicationPartners != null
                                        ? "<span class=\"rf-duplicate-partner\">"
                                                + Arrays.stream(duplicationPartners.split(";", -1))
                                                        .map(String::trim)
                                                        .map(SimpleHtmlReport::escapeHtmlLabel)
                                                        .collect(Collectors.joining("<br>"))
                                                + "</span>"
                                        : "")
                        .align("left")
                        .build());
            }

            cells.add(DisharmonyTableCellDTO.builder()
                    .content(formatter.format(rd.getMostRecentCommitTime()))
                    .align("right")
                    .build());
            cells.add(DisharmonyTableCellDTO.builder()
                    .content(rd.getCommitCount().toString())
                    .align("right")
                    .build());

            if (showDetails) {
                cells.add(DisharmonyTableCellDTO.builder()
                        .content(formatter.format(rd.getFirstCommitTime()))
                        .align("right")
                        .build());
                cells.add(DisharmonyTableCellDTO.builder()
                        .content(SimpleHtmlReport.escapeHtmlLabel(rd.getPath()))
                        .align("left")
                        .build());
            }

            rows.add(DisharmonyTableRowDTO.builder().cells(cells).build());
        }

        DisharmonyTableDTO tableDTO =
                DisharmonyTableDTO.builder().headers(headers).rows(rows).build();

        return DisharmonySectionDTO.builder()
                .type(spec.type())
                .anchorId(spec.anchorId())
                .title(spec.title())
                .methodLevel(spec.methodLevel())
                .problem(spec.problem())
                .solution(spec.solution())
                .maxPriority(maxPriority)
                .chart(chartDTO)
                .table(tableDTO)
                .build();
    }

    /**
     * Creates a chart bubble whose size and color reflect the finding priority. The bubble
     * carries only the source file path relative to the project root; viewers combine it with
     * the repository URL from the project metadata.
     *
     * <p>Static so the bubble interpolation is the single source for the P4 chart configuration.
     */
    public static ChartJsBubbleDTO createBubble(
            String id,
            String label,
            int effortRank,
            int changePronenessRank,
            int priority,
            int maxPriority,
            String path) {

        int minRadius = 6;
        int maxRadius = 24;
        int radius;
        if (maxPriority <= 1) {
            radius = maxRadius;
        } else {
            double fraction = (double) (maxPriority - priority) / (maxPriority - 1);
            radius = (int) Math.round(minRadius + fraction * (maxRadius - minRadius));
        }

        String color;
        String borderColor;
        if (maxPriority <= 1 || priority == 1) {
            color = "rgba(235, 64, 52, 0.75)";
            borderColor = "rgb(235, 64, 52)";
        } else if (priority == maxPriority) {
            color = "rgba(39, 174, 96, 0.75)";
            borderColor = "rgb(39, 174, 96)";
        } else {
            double t = (double) (priority - 1) / (maxPriority - 1);
            int red = (int) Math.round(235 * (1 - t) + 39 * t);
            int green = (int) Math.round(64 * (1 - t) + 174 * t);
            int blue = (int) Math.round(52 * (1 - t) + 96 * t);
            color = String.format("rgba(%d, %d, %d, 0.75)", red, green, blue);
            borderColor = String.format("rgb(%d, %d, %d)", red, green, blue);
        }

        return ChartJsBubbleDTO.builder()
                .id(id)
                .label(label)
                .x(effortRank)
                .y(changePronenessRank)
                .r(radius)
                .priority(priority)
                .effortRank(effortRank)
                .changePronenessRank(changePronenessRank)
                .color(color)
                .borderColor(borderColor)
                .path(path)
                .build();
    }

    /**
     * Converts the class edges behind a package edge into structured DTOs.
     * Cycle membership is calculated for every class edge from {@code classCycles} before the
     * DTOs are constructed: {@code classEdgeCycleCounts} only covers edges of the class
     * feedback arc set, so relying on it reported the nested edges — which are generally not
     * feedback-arc-set members — as members of zero class cycles.
     */
    List<ClassRelationshipDTO> buildClassRelationshipsToBreakPackage(
            Set<DefaultWeightedEdge> classEdges, CodebaseGraphDTO codebaseGraphDTO) {
        if (classEdges == null || classEdges.isEmpty()) {
            return List.of();
        }

        List<DefaultWeightedEdge> orderedClassEdges = sortedClassEdgesInPackageRelationship(classEdges);
        Map<DefaultWeightedEdge, Integer> cycleCounts = new java.util.LinkedHashMap<>();
        for (DefaultWeightedEdge classEdge : orderedClassEdges) {
            cycleCounts.put(classEdge, countClassCyclesContaining(classEdge));
        }

        List<ClassRelationshipDTO> relationships = new ArrayList<>();
        for (DefaultWeightedEdge classEdge : orderedClassEdges) {
            String[] vertexes = SimpleHtmlReport.extractVertexes(classEdge);
            String startVertex = vertexes[0].trim();
            String endVertex = vertexes[1].trim();
            int weight = (int) classGraph.getEdgeWeight(classEdge);

            relationships.add(ClassRelationshipDTO.builder()
                    .sourceClass(startVertex)
                    .targetClass(endVertex)
                    .sourceClassPath(classSourcePath(startVertex, codebaseGraphDTO))
                    .targetClassPath(classSourcePath(endVertex, codebaseGraphDTO))
                    .simpleSourceClassName(getClassName(startVertex))
                    .simpleTargetClassName(getClassName(endVertex))
                    .sourceMarked(classesToRemove.contains(VertexInfo.forName(startVertex)))
                    .targetMarked(classesToRemove.contains(VertexInfo.forName(endVertex)))
                    .weight(weight)
                    .cycleCount(cycleCounts.get(classEdge))
                    .build());
        }
        return relationships;
    }

    /** Returns the class's source file path relative to the project root, or an empty string. */
    private String classSourcePath(String className, CodebaseGraphDTO codebaseGraphDTO) {
        String path = codebaseGraphDTO.getClassToSourceFilePathMapping().get(className);
        return path != null ? path : "";
    }

    /**
     * Counts how many of the detected class cycles contain the given class edge — the same
     * counting {@code CycleRemovalComputer} applies to the edges it marks for removal. Returns
     * {@code 0} when no class cycles are available.
     */
    private int countClassCyclesContaining(DefaultWeightedEdge classEdge) {
        if (classCycles == null || classCycles.isEmpty()) {
            return 0;
        }
        int cycleCount = 0;
        for (AsSubgraph<VertexInfo, DefaultWeightedEdge> cycle : classCycles.values()) {
            if (cycle.containsEdge(classEdge)) {
                cycleCount++;
            }
        }
        return cycleCount;
    }

    /** Creates a stable HTML-safe graph identifier from a display value. */
    private static String graphIdentifier(String value) {
        String original = value == null ? "" : value;
        String sanitized = original.replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitized.isEmpty()) {
            sanitized = "cycle";
        }
        return "graph_" + sanitized + "_" + Integer.toUnsignedString(original.hashCode(), 36);
    }

    /**
     * Checks if a class belongs to a given package, deriving the class's package with the same
     * rule the graphs use ({@link HierarchyBuilder#packageOf}, mirroring
     * {@code GraphDependencyCollector.getPackageFromFqn}): a Kotlin anonymous class belongs to
     * its enclosing class's package ({@code dev.Owner.<anonymous> -> dev}), and packageless
     * classes match no package.
     */
    boolean isClassInPackage(String className, String packageName, CodebaseGraphDTO codebaseGraphDTO) {
        if (className == null || packageName == null) {
            return false;
        }
        String classPackage = HierarchyBuilder.packageOf(className);
        return classPackage != null && classPackage.equals(packageName);
    }

    /** Returns the edges of one package relationship, ordered by source then target class FQN. */
    List<DefaultWeightedEdge> sortedClassEdgesInPackageRelationship(Set<DefaultWeightedEdge> classEdges) {
        if (classEdges == null || classEdges.isEmpty()) {
            return List.of();
        }
        return classEdges.stream()
                .sorted(java.util.Comparator.comparing((DefaultWeightedEdge classEdge) ->
                                classGraph.getEdgeSource(classEdge).getName())
                        .thenComparing(
                                classEdge -> classGraph.getEdgeTarget(classEdge).getName()))
                .toList();
    }

    /** Renders a class edge without repository links for serialized report data. */
    String renderClassEdge(DefaultWeightedEdge edge) {
        StringBuilder edgesToCut = new StringBuilder();
        String[] vertexes = SimpleHtmlReport.extractVertexes(edge);
        String startVertex = vertexes[0].trim();
        String endVertex = vertexes[1].trim();

        // &#8594; is HTML "Right Arrow" code
        return edgesToCut
                .append(SimpleHtmlReport.escapeHtmlLabel(getClassName(startVertex)) + " &#8594; "
                        + SimpleHtmlReport.escapeHtmlLabel(getClassName(endVertex)) + " : "
                        + (int) classGraph.getEdgeWeight(edge))
                .toString();
    }

    /** Returns the simple class name from a fully qualified class name. */
    static String getClassName(String fqn) {
        if (fqn == null) {
            return "";
        }
        if (!fqn.contains(".")) {
            return fqn;
        }
        int lastIndex = fqn.lastIndexOf(".");
        return fqn.substring(lastIndex + 1);
    }
}
