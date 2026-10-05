package org.hjug.refactorfirst.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.CostBenefitCalculator;
import org.hjug.cbc.CycleRanker;
import org.hjug.cbc.RankedCycle;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.feedback.CycleRemovalComputer;
import org.hjug.feedback.CycleRemovalResult;
import org.hjug.git.GitLogReader;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.metrics.DisharmonyMetric;
import org.hjug.metrics.DisharmonyInstance;
import org.hjug.refactorfirst.report.model.*;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Slf4j
public class JsonGenerator extends HtmlReport {

    public static final String DIRECTORY_NAME = ".refactorfirst";
    public static final String FILE_NAME = "refactor-first.json";

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Generates report data and writes it with the bundled viewer resources. */
    @SneakyThrows
    public void execute(
            int edgeAnalysisCount,
            boolean analyzeCycles,
            boolean showDetails,
            boolean excludeTests,
            String testSourceDirectory,
            String projectName,
            String projectVersion,
            File baseDir,
            File outputDir) {

        Path projectPath = baseDir != null
                ? baseDir.toPath().toAbsolutePath().normalize()
                : Path.of("").toAbsolutePath().normalize();

        Path reportRoot =
                outputDir != null ? outputDir.toPath().toAbsolutePath().normalize() : projectPath;
        Path dotRefactorFirstDir = reportRoot.resolve(DIRECTORY_NAME);
        if (!Files.exists(dotRefactorFirstDir)) {
            Files.createDirectories(dotRefactorFirstDir);
        }

        RefactorFirstReportDTO reportDTO = generateReportData(
                showDetails,
                edgeAnalysisCount,
                analyzeCycles,
                excludeTests,
                testSourceDirectory,
                projectName,
                projectVersion,
                projectPath.toFile());

        String json = objectMapper.writeValueAsString(reportDTO);

        Path targetFile = dotRefactorFirstDir.resolve(FILE_NAME);
        Path tempFile = dotRefactorFirstDir.resolve(FILE_NAME + ".tmp");
        Files.writeString(tempFile, json, StandardCharsets.UTF_8);
        Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        log.info("RefactorFirst JSON successfully generated at {}", targetFile.toAbsolutePath());

        // Copy Mustache template and viewer to .refactorfirst directory
        // copyViewerResources(dotRefactorFirstDir);
    }

    /**
     * Intentionally excluding hyperlinks in DOT to reduce JSON file size
     *
     * @param fqClassName The fully qualified class name
     * @param repoUrl The repo URL
     * @param codebaseGraphDTO The DTO containing the codebase graph
     */
    String hyperlinkClassForDot(String fqClassName, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        return "";
    }

    /** Copies the Mustache template and browser viewer into the report directory. */
    private void copyViewerResources(Path targetDir) {
        try {
            // Copy Mustache template
            try (InputStream templateStream =
                    getClass().getResourceAsStream("/templates/refactor-first-report.mustache")) {
                if (templateStream != null) {
                    Path templateTarget = targetDir.resolve("refactor-first-report.mustache");
                    Files.copy(templateStream, templateTarget, StandardCopyOption.REPLACE_EXISTING);
                    log.debug("Copied Mustache template to {}", templateTarget);
                } else {
                    log.warn("Mustache template not found in resources");
                }
            }

            // Copy index.html viewer
            try (InputStream viewerStream = getClass().getResourceAsStream("/viewer/index.html")) {
                if (viewerStream != null) {
                    Path viewerTarget = targetDir.resolve("index.html");
                    Files.copy(viewerStream, viewerTarget, StandardCopyOption.REPLACE_EXISTING);
                    log.debug("Copied viewer index.html to {}", viewerTarget);
                } else {
                    log.warn("Viewer index.html not found in resources");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to copy viewer resources: {}", e.getMessage());
        }
    }

    /** Analyzes a project and converts its findings into serializable report data. */
    public RefactorFirstReportDTO generateReportData(
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
            projectBaseDir = Path.of("").toAbsolutePath().toString();
            optionalGitDir = Optional.ofNullable(GitLogReader.getGitDir(new File(projectBaseDir)));
        }

        String scanTimestamp = formatter.format(Instant.now());

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
            List<RankedCycle> rankedClassCycles = List.of();
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
            Map<DefaultWeightedEdge, Integer> classEdgeCycleCounts = classCycleRemovalResult.getEdgeCycleCounts();
            classRelationshipsToRemove = classCycleRemovalResult.getEdgesToRemove();
            classesToRemove = classCycleRemovalResult.getVertexesToRemove();
            classCycles = classCycleRemovalResult.getCycles();

            packageGraph = codebaseGraphDTO.getPackageReferencesGraph();
            CycleRemovalResult packageCycleRemovalResult =
                    cycleRemovalComputer.computeCycleRemovalInformation(packageGraph);
            Map<DefaultWeightedEdge, Integer> packageEdgeCycleCounts = packageCycleRemovalResult.getEdgeCycleCounts();
            packageRelationshipsToRemove = packageCycleRemovalResult.getEdgesToRemove();
            packagesToRemove = packageCycleRemovalResult.getVertexesToRemove();
            packageCycles = packageCycleRemovalResult.getCycles();

            Map<String, List<RankedDisharmony>> rankedDisharmoniesByAnchor = new LinkedHashMap<>();
            List<RankedDisharmony> classRelationshipDisharmonies = List.of();
            List<RankedDisharmony> packageRelationshipDisharmonies = List.of();

            try (CostBenefitCalculator costBenefitCalculator =
                    new CostBenefitCalculator(projectBaseDir, codebaseGraphDTO.getClassToSourceFilePathMapping())) {
                packageRelationshipDisharmonies = costBenefitCalculator.calculateRelationshipCostBenefitValues(
                        packageGraph,
                        packageEdgeCycleCounts,
                        codebaseGraphDTO,
                        packagesToRemove,
                        packageCycles,
                        List.of());
                classRelationshipDisharmonies = costBenefitCalculator.calculateRelationshipCostBenefitValues(
                        classGraph,
                        classEdgeCycleCounts,
                        codebaseGraphDTO,
                        classesToRemove,
                        packageCycles,
                        packageRelationshipDisharmonies);

                for (DisharmonySpec spec : DISHARMONY_SPECS) {
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

            String repoUrl = getRepoUrl(projectBaseDir);

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
            String classGraphDot = buildRawClassGraphDot(classGraph, repoUrl, codebaseGraphDTO);
            GraphVisualDTO classMapDTO = GraphVisualDTO.builder()
                    .graphId("classGraph")
                    .classCount(classCount)
                    .relationshipCount(classRelationshipCount)
                    .dotThreshold(dotGraphThreshold)
                    .dotThresholdExceeded(classCount + classRelationshipCount >= dotGraphThreshold)
                    .dot(classGraphDot)
                    .build();

            // 2. Class Relationships To Remove
            List<ClassRelationshipDTO> classRelList = new ArrayList<>();
            for (RankedDisharmony edgeInfo : classRelationshipDisharmonies) {
                String[] vertexes = extractVertexes(edgeInfo.getEdge());
                String startVertex = vertexes[0].trim();
                String endVertex = vertexes[1].trim();

                classRelList.add(ClassRelationshipDTO.builder()
                        .sourceClass(startVertex)
                        .targetClass(endVertex)
                        .sourceClassPath(classSourcePath(startVertex, codebaseGraphDTO))
                        .targetClassPath(classSourcePath(endVertex, codebaseGraphDTO))
                        .simpleSourceClassName(getClassName(startVertex))
                        .simpleTargetClassName(getClassName(endVertex))
                        .sourceMarked(classesToRemove.contains(startVertex))
                        .targetMarked(classesToRemove.contains(endVertex))
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
            String packageGraphDot =
                    hasPackageEdges ? buildRawPackageGraphDot(packageGraph, repoUrl, codebaseGraphDTO) : "";
            GraphVisualDTO packageMapDTO = GraphVisualDTO.builder()
                    .hasEdges(hasPackageEdges)
                    .graphId("packageGraph")
                    .classCount(packageCount)
                    .relationshipCount(packageRelationshipCount)
                    .dotThreshold(dotGraphThreshold)
                    .dotThresholdExceeded(packageCount + packageRelationshipCount >= dotGraphThreshold)
                    .dot(packageGraphDot)
                    .build();

            // 4. Package Relationships To Remove
            List<PackageRelationshipDTO> packageRelList = new ArrayList<>();
            for (RankedDisharmony edgeInfo : packageRelationshipDisharmonies) {
                String[] cells = getPackageRelationshipDisharmony(edgeInfo, repoUrl, codebaseGraphDTO);
                String[] vertexes = extractVertexes(edgeInfo.getEdge());
                String startVertex = vertexes[0].trim();
                String endVertex = vertexes[1].trim();

                // Extract package URLs from class relationships in the package edge
                String sourceUrl = "";
                String targetUrl = "";
                Set<DefaultWeightedEdge> classRelationshipsInPackageRelationship = codebaseGraphDTO
                        .getClassRelationshipsInPackageRelationship()
                        .get(edgeInfo.getEdge());
                if (classRelationshipsInPackageRelationship != null) {
                    for (DefaultWeightedEdge classEdge : classRelationshipsInPackageRelationship) {
                        String[] classVertexes = extractVertexes(classEdge);
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
                                    sourceUrl = repoUrl + packageDir;
                                }
                                // Check if this class belongs to the target package
                                if (isClassInPackage(classStart, endVertex, codebaseGraphDTO)) {
                                    targetUrl = repoUrl + packageDir;
                                }
                            }
                        }

                        if (classEndPath != null && !classEndPath.isBlank()) {
                            int lastSlash = classEndPath.lastIndexOf('/');
                            if (lastSlash != -1) {
                                String packageDir = classEndPath.substring(0, lastSlash + 1);
                                // Check if this class belongs to the source package
                                if (isClassInPackage(classEnd, startVertex, codebaseGraphDTO)) {
                                    sourceUrl = repoUrl + packageDir;
                                }
                                // Check if this class belongs to the target package
                                if (isClassInPackage(classEnd, endVertex, codebaseGraphDTO)) {
                                    targetUrl = repoUrl + packageDir;
                                }
                            }
                        }
                    }
                }

                // Fallback to package name conversion if no class edges found
                if (sourceUrl.isBlank()) {
                    sourceUrl = repoUrl + startVertex.replace('.', '/');
                }
                if (targetUrl.isBlank()) {
                    targetUrl = repoUrl + endVertex.replace('.', '/');
                }

                List<ClassRelationshipDTO> breakClassRels = buildClassRelationshipsToBreakPackage(
                        classRelationshipsInPackageRelationship, codebaseGraphDTO);

                // Build rendered label with links to package directories
                String renderedLabel =
                        buildPackageLinkLabel(cells[0], startVertex, endVertex, sourceUrl, targetUrl, packagesToRemove);

                packageRelList.add(PackageRelationshipDTO.builder()
                        .sourcePackage(startVertex)
                        .targetPackage(endVertex)
                        .sourceMarked(packagesToRemove.contains(startVertex))
                        .targetMarked(packagesToRemove.contains(endVertex))
                        .weight((int) packageGraph.getEdgeWeight(edgeInfo.getEdge()))
                        .renderedLabel(renderedLabel)
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
            for (DisharmonySpec spec : DISHARMONY_SPECS) {
                List<RankedDisharmony> ranked = rankedDisharmoniesByAnchor.get(spec.anchorId());
                if (ranked != null && !ranked.isEmpty()) {
                    disharmonySections.add(buildDisharmonySection(spec, showDetails, ranked, repoUrl));
                }
            }

            // 6. Cycles
            List<CycleSummaryDTO> cycleSummaries = new ArrayList<>();
            for (RankedCycle cycle : rankedClassCycles) {
                String[] summaryData = getRankedCycleSummaryData(cycle);
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
                String cycleDot = buildRawClassCycleDot(classGraph, largestCycle, repoUrl, codebaseGraphDTO);

                List<CycleBreakdownRowDTO> breakdown = new ArrayList<>();
                for (String vertex : largestCycle.getVertexSet()) {
                    String className;
                    if (classesToRemove.contains(vertex)) {
                        className = hyperlinkClass(vertex, repoUrl, codebaseGraphDTO) + "*";
                    } else {
                        className = hyperlinkClass(vertex, repoUrl, codebaseGraphDTO);
                    }

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
                    breakdown.add(CycleBreakdownRowDTO.builder()
                            .className(className)
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
                        .dot(cycleDot)
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

    /** Converts ranked instances of one disharmony type into chart and table data. */
    private DisharmonySectionDTO buildDisharmonySection(
            DisharmonySpec spec, boolean showDetails, List<RankedDisharmony> ranked, String repoUrl) {

        int maxPriority = ranked.get(ranked.size() - 1).getPriority();

        // Chart
        List<ChartJsBubbleDTO> bubbles = new ArrayList<>();
        for (RankedDisharmony rd : ranked) {
            String label = rd.getFileName() != null
                    ? rd.getFileName()
                    : rd.getRawPriority().toString();
            String url = repoUrl + rd.getPath();
            bubbles.add(createBubble(
                    rd.getFileName(),
                    label,
                    rd.getEffortRank(),
                    rd.getChangePronenessRank(),
                    rd.getPriority(),
                    maxPriority,
                    url));
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

            // Class link
            cells.add(DisharmonyTableCellDTO.builder()
                    .content("<a href=\"" + escapeHtmlAttribute(repoUrl + rd.getPath()) + "\" target=\"_blank\">"
                            + escapeHtmlLabel(rd.getFileName()) + "</a>")
                    .align("left")
                    .build());

            // Method
            if (spec.methodLevel()) {
                String sig = rd.getMethodSignature();
                if (!showDetails && sig != null) {
                    sig = getSimpleMethodSignature(sig);
                }
                cells.add(DisharmonyTableCellDTO.builder()
                        .content(escapeHtmlLabel(sig))
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
                        .content(escapeHtmlLabel(rd.getDescription()))
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
                    duplicationPartners = simplifyDuplicatePartners(duplicationPartners);
                }
                cells.add(DisharmonyTableCellDTO.builder()
                        .content(
                                duplicationPartners != null
                                        ? escapeHtmlLabel(duplicationPartners).replace(";", "<br>")
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
                        .content(escapeHtmlLabel(rd.getPath()))
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

    /** Creates a chart bubble whose size and color reflect the finding priority. */
    public ChartJsBubbleDTO createBubble(
            String id,
            String label,
            int effortRank,
            int changePronenessRank,
            int priority,
            int maxPriority,
            String url) {

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
                .url(url)
                .build();
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

    /** Checks if a class belongs to a given package by checking the fully qualified class name. */
    private boolean isClassInPackage(String className, String packageName, CodebaseGraphDTO codebaseGraphDTO) {
        if (className == null || packageName == null) {
            return false;
        }
        // Check if the class name starts with the package name followed by a dot
        int lastDot = className.lastIndexOf('.');
        return lastDot > 0 && className.substring(0, lastDot).equals(packageName);
    }

    /**
     * Builds a rendered label with HTML links to package directories.
     * Transforms "org.hjug.graphbuilder.metrics &#8594; org.hjug.graphbuilder<strong>*</strong> : 1"
     * into "<a href="...">org.hjug.graphbuilder.metrics</a> &#8594; <a href="...">org.hjug.graphbuilder</a>* : 1"
     */
    private String buildPackageLinkLabel(
            String originalLabel,
            String startVertex,
            String endVertex,
            String sourceUrl,
            String targetUrl,
            Set<String> packagesToRemove) {
        if (originalLabel == null || originalLabel.isBlank()) {
            return originalLabel;
        }

        // The original label format is: "startPackage &#8594; endPackage : weight"
        // We need to wrap the package names with links
        String arrow = " &#8594; ";
        String weightSeparator = " : ";

        // Split the label into parts
        String[] parts = originalLabel.split(weightSeparator);
        if (parts.length < 2) {
            return originalLabel;
        }

        String edgePart = parts[0];
        String weight = parts[1];

        // Split the edge part by arrow
        String[] packages = edgePart.split(arrow, 2);
        if (packages.length < 2) {
            return originalLabel;
        }

        String startPackage = packages[0].trim();
        String endPackage = packages[1].trim();

        // Check if start package has the removal marker (escaped or unescaped)
        boolean startMarked =
                startPackage.contains("<strong>*</strong>") || startPackage.contains("&lt;strong&gt;*&lt;/strong&gt;");
        if (startMarked) {
            startPackage = startPackage.replace("<strong>*</strong>", "").replace("&lt;strong&gt;*&lt;/strong&gt;", "");
        }

        // Check if end package has the removal marker (escaped or unescaped)
        boolean endMarked =
                endPackage.contains("<strong>*</strong>") || endPackage.contains("&lt;strong&gt;*&lt;/strong&gt;");
        if (endMarked) {
            endPackage = endPackage.replace("<strong>*</strong>", "").replace("&lt;strong&gt;*&lt;/strong&gt;", "");
        }

        // Build the new label with links
        StringBuilder newLabel = new StringBuilder();
        newLabel.append("<a href=\"")
                .append(escapeHtmlAttribute(sourceUrl))
                .append("\" target=\"_blank\">")
                .append(escapeHtmlLabel(startPackage))
                .append("</a>");
        if (startMarked) {
            newLabel.append("*");
        }
        newLabel.append(arrow);
        newLabel.append("<a href=\"")
                .append(escapeHtmlAttribute(targetUrl))
                .append("\" target=\"_blank\">")
                .append(escapeHtmlLabel(endPackage))
                .append("</a>");
        if (endMarked) {
            newLabel.append("*");
        }
        newLabel.append(weightSeparator).append(weight);

        return newLabel.toString();
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
        Map<DefaultWeightedEdge, Integer> cycleCounts = new LinkedHashMap<>();
        for (DefaultWeightedEdge classEdge : orderedClassEdges) {
            cycleCounts.put(classEdge, countClassCyclesContaining(classEdge));
        }

        List<ClassRelationshipDTO> relationships = new ArrayList<>();
        for (DefaultWeightedEdge classEdge : orderedClassEdges) {
            String[] vertexes = extractVertexes(classEdge);
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
                    .sourceMarked(classesToRemove.contains(startVertex))
                    .targetMarked(classesToRemove.contains(endVertex))
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
        for (AsSubgraph<String, DefaultWeightedEdge> cycle : classCycles.values()) {
            if (cycle.containsEdge(classEdge)) {
                cycleCount++;
            }
        }
        return cycleCount;
    }
}
