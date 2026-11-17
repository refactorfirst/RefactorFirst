package org.hjug.refactorfirst.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.VertexInfo;
import org.hjug.refactorfirst.report.model.*;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Slf4j
public class JsonGenerator {

    public static final String DIRECTORY_NAME = ".refactorfirst";
    public static final String FILE_NAME = "refactor-first.json";

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private Clock clock = Clock.systemDefaultZone();

    // Analysis state backing the delegation paths below. The authoritative copies live in
    // {@link ReportModelBuilder}; these mirror the last analysis into the builder for the
    // relationship-to-remove and cycle-membership computations used by the frozen tests.
    Graph<VertexInfo, DefaultWeightedEdge> classGraph;
    Graph<VertexInfo, DefaultWeightedEdge> packageGraph;
    Map<VertexInfo, AsSubgraph<VertexInfo, DefaultWeightedEdge>> classCycles;
    Set<VertexInfo> classesToRemove = Set.of(); // initialize for unit tests

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

    /** Returns a builder configured with the given clock (used by deterministic tests). */
    public JsonGenerator withClock(Clock clock) {
        this.clock = clock;
        return this;
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
        return new ReportModelBuilder()
                .build(
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
     * Creates a chart bubble whose size and color reflect the finding priority. The bubble
     * carries only the source file path relative to the project root; viewers combine it with
     * the repository URL from the project metadata.
     */
    public ChartJsBubbleDTO createBubble(
            String id,
            String label,
            int effortRank,
            int changePronenessRank,
            int priority,
            int maxPriority,
            String path) {
        return ReportModelBuilder.createBubble(id, label, effortRank, changePronenessRank, priority, maxPriority, path);
    }

    /**
     * Checks if a class belongs to a given package, deriving the class's package with the same
     * rule the graphs use ({@link HierarchyBuilder#packageOf}, mirroring
     * {@code GraphDependencyCollector.getPackageFromFqn}): a Kotlin anonymous class belongs to
     * its enclosing class's package ({@code dev.Owner.<anonymous> -> dev}), and packageless
     * classes match no package.
     */
    boolean isClassInPackage(String className, String packageName, CodebaseGraphDTO codebaseGraphDTO) {
        return new ReportModelBuilder().isClassInPackage(className, packageName, codebaseGraphDTO);
    }

    /**
     * Converts ranked instances of one disharmony type into chart and table data. Table cells
     * carry only plain text and paths relative to the project root; viewers combine the paths
     * with the repository URL from the project metadata.
     */
    DisharmonySectionDTO buildDisharmonySection(
            SimpleHtmlReport.DisharmonySpec spec, boolean showDetails, List<RankedDisharmony> ranked) {
        return new ReportModelBuilder().buildDisharmonySection(spec, showDetails, ranked);
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

        ReportModelBuilder modelBuilder = new ReportModelBuilder();
        modelBuilder.classGraph = classGraph;
        modelBuilder.classCycles = classCycles;
        modelBuilder.classesToRemove = classesToRemove;
        return modelBuilder.buildClassRelationshipsToBreakPackage(classEdges, codebaseGraphDTO);
    }
}
