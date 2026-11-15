package org.hjug.refactorfirst.report;

import static org.hjug.refactorfirst.report.ReportWriter.writeReportToDisk;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.RankedCycle;
import org.hjug.git.GitLogReader;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.VertexInfo;
import org.hjug.refactorfirst.report.model.HierarchyDTO;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 * The single-file interactive report page (Phase P4). Renders from
 * {@code templates/refactor-first-report.mustache} with all data embedded as the rf-data JSON
 * payload, all classpath assets inlined by {@link AssetInliner}, Chart.js bubble charts and
 * pinned CDN widgets. Google Charts is gone.
 */
@Slf4j
public class HtmlReport extends SimpleHtmlReport {

    /**
     * Generates the interactive HTML report and writes it to {@code outputDirectory}.
     *
     * <p>Phase P4/T4.6: the page renders from the mustache template through the shared
     * {@link ReportModelBuilder} + {@link ViewModelFactory} pipeline, embeds the rf-data JSON
     * payload and inlines the classpath assets. A missing Git repository writes the strictly-HTML
     * message page; an analysis failure throws as before.
     */
    @Override
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

        File effectiveBaseDir = baseDir != null
                ? baseDir
                : new File(Path.of("").toAbsolutePath().toString());
        String reportHtml;
        if (GitLogReader.getGitDir(effectiveBaseDir) == null) {
            log.info(
                    "Done! No Git repository found!  Please initialize a Git repository and perform an initial commit.");
            reportHtml = new TemplateEngine()
                    .render(
                            "templates/refactor-first-simple-report.mustache",
                            noGitViewModel(projectName, projectVersion, effectiveBaseDir));
        } else {
            reportHtml = renderInteractivePage(
                    showDetails,
                    edgeAnalysisCount,
                    analyzeCycles,
                    excludeTests,
                    testSourceDirectory,
                    projectName,
                    projectVersion,
                    baseDir);
        }

        writeReportToDisk(outputDirectory, filename, maybeMinifyHtml(reportHtml, minifyHtml));
        log.info("Done! View the report at target/site/{}", filename);
    }

    /**
     * Renders the complete interactive report page: the model builder analyzes the project, the
     * view model factory translates the DTO, the Java-built graph DOT strings join the rf-data
     * JSON payload (the P4 parity bridge), the template engine renders the page and the asset
     * inliner embeds the classpath CSS/JS. Used by {@link #execute} and the Doxia site report.
     *
     * <p>A missing Git repository renders the strictly-HTML message page instead of surfacing the
     * analysis-failed state: the no-Git result must reach this renderer as the message page rather
     * than tripping the analysis-failed guard below.
     */
    public String renderInteractivePage(
            boolean showDetails,
            int edgeAnalysisCount,
            boolean analyzeCycles,
            boolean excludeTests,
            String testSourceDirectory,
            String projectName,
            String projectVersion,
            File baseDir)
            throws Exception {

        File effectiveBaseDir = baseDir != null
                ? baseDir
                : new File(Path.of("").toAbsolutePath().toString());
        if (GitLogReader.getGitDir(effectiveBaseDir) == null) {
            log.info(
                    "Done! No Git repository found!  Please initialize a Git repository and perform an initial commit.");
            return new TemplateEngine()
                    .render(
                            "templates/refactor-first-simple-report.mustache",
                            noGitViewModel(projectName, projectVersion, effectiveBaseDir));
        }

        ReportModelBuilder modelBuilder = new ReportModelBuilder();
        RefactorFirstReportDTO reportDTO = modelBuilder.build(
                showDetails,
                edgeAnalysisCount,
                analyzeCycles,
                excludeTests,
                testSourceDirectory,
                projectName,
                projectVersion,
                baseDir);

        if (reportDTO.getProject() != null && reportDTO.getProject().isAnalysisFailed()) {
            throw new RuntimeException("Report analysis failed");
        }

        return renderInteractiveReport(reportDTO, modelBuilder);
    }

    /**
     * Renders the complete interactive report page from an already-built report DTO: the view
     * model factory translates the DTO, the template engine renders the page (the rf-data
     * payload drives every widget — graphs synthesize client-side from the inline hierarchy
     * payloads since P7) and the asset inliner embeds the classpath CSS/JS.
     */
    public String renderInteractiveReport(RefactorFirstReportDTO reportDTO, ReportModelBuilder modelBuilder)
            throws Exception {

        // modelBuilder carries no render-time state since P7: the graphs
        // synthesize client-side from the DTO's inline hierarchy payloads.
        if (reportDTO.getProject() != null && reportDTO.getProject().isAnalysisFailed()) {
            throw new RuntimeException("Report analysis failed");
        }

        Map<String, Object> view = new ViewModelFactory().createViewModel(reportDTO);
        view.put("rfDataBlock", new RfDataBlock().build(reportDTO));
        return new TemplateEngine().renderWithAssets("templates/refactor-first-report.mustache", view);
    }

    public String getName(Locale locale) {
        return "Refactor First Report";
    }

    public String getDescription(Locale locale) {
        return "Ranks the disharmonies in a codebase.  The classes that should be refactored first "
                + " have the highest priority values.";
    }

    /**
     * Builds raw DOT source for the complete class relationship graph as nested package
     * clusters containing the classes that participate in at least one relationship.
     */
    String buildRawClassGraphDot(
            Graph<VertexInfo, DefaultWeightedEdge> classGraph, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        HierarchyDTO hierarchy = HierarchyBuilder.buildClassHierarchy(
                classGraph, codebaseGraphDTO, classesToRemove, packagesToRemove, classRelationshipsToRemove);
        return NestedDotBuilder.buildNestedDot(hierarchy, repoUrl, true);
    }

    /**
     * Encodes a full class name as an injective ASCII DOT identifier. ASCII letters and digits
     * pass through; every other UTF-16 code unit (including underscores) becomes an underscore
     * followed by four hex digits. Escaping the escape marker makes the encoding reversible,
     * including for supplementary characters. The class_ prefix avoids DOT keywords, leading
     * digits, and the package_/cluster_ namespaces. Human-readable names remain in DTO labels.
     */
    static String renderSafeNodeId(String vertex) {
        StringBuilder id = new StringBuilder("class_");
        for (int i = 0; i < vertex.length(); i++) {
            char c = vertex.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                id.append(c);
            } else {
                String hex = Integer.toHexString(c);
                id.append('_').append("0".repeat(4 - hex.length())).append(hex);
            }
        }
        return id.toString();
    }

    /** Source mappings affect labels only; class identity always comes from the full name. */
    String renderSafeNodeId(String vertex, CodebaseGraphDTO codebaseGraphDTO) {
        return renderSafeNodeId(vertex);
    }

    /** Class ids remain stable across full-graph and cycle views. */
    String renderSafeNodeId(String vertex, Graph<VertexInfo, DefaultWeightedEdge> classGraph) {
        return renderSafeNodeId(vertex);
    }

    /** Resolves the same class id regardless of graph membership or source-file mappings. */
    static String renderSafeNodeId(
            String vertex, Graph<VertexInfo, DefaultWeightedEdge> classGraph, CodebaseGraphDTO codebaseGraphDTO) {
        return renderSafeNodeId(vertex);
    }

    /**
     * Derives the enclosing Kotlin/Java class name from the source-file path mapped for an
     * anonymous vertex, e.g. {@code /fxgl-samples/src/main/kotlin/dev/DeveloperWASDControl.kt} ->
     * {@code DeveloperWASDControl}. Returns {@code null} when the vertex has no mapped source
     * path or {@code codebaseGraphDTO == null}.
     */
    private static String enclosingSourceFileBaseName(String vertex, CodebaseGraphDTO codebaseGraphDTO) {
        if (codebaseGraphDTO == null) {
            return null;
        }
        Map<String, String> mapping = codebaseGraphDTO.getClassToSourceFilePathMapping();
        if (mapping == null) {
            return null;
        }
        String path = mapping.get(vertex);
        if (path == null || path.isEmpty()) {
            return null;
        }
        // strip a trailing '/' then take the last path segment
        String name = path;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(0, dot);
        }
        return name.isEmpty() ? null : name;
    }

    /**
     * Builds the DOT label for an anonymous vertex using the enclosing source-file base name as
     * the owner with {@code $} as the enclosing-class separator (escaped as {@code \$} for DOT),
     * e.g. {@code DeveloperWASDControl$anonymous} -> {@code DeveloperWASDControl\$anonymous}.
     * Falls back to the raw class name (e.g. {@code <anonymous>}) when no owner is recoverable.
     */
    static String anonymousOwnerLabel(String vertex, CodebaseGraphDTO codebaseGraphDTO) {
        String owner = enclosingSourceFileBaseName(vertex, codebaseGraphDTO);
        String simple = getClassName(vertex);
        if (owner != null) {
            return owner + "$anonymous";
        }
        return simple;
    }

    /**
     * Returns {@code true} for a Kotlin anonymous-class FQN. OpenRewrite attributes a Kotlin
     * anonymous object / function-literal type with the literal {@code "<anonymous>"} as its
     * trailing simple-name segment, either standalone ({@code "<anonymous>"}) or prefixed by the
     * enclosing class/package (e.g. {@code "dev.DeveloperWASDControl.<anonymous>"}). Such a
     * vertex cannot appear verbatim in a DOT node id (the {@code <}/{@code >} are illegal), so
     * this predicate decides when the source-aware label derivation must kick in.
     */
    static boolean isAnonymousFqn(String vertex) {
        if (vertex == null) {
            return false;
        }
        // trailing simple-name segment (text after the last '.'); matches the behaviour of the
        // package-private getClassName(...) used by the renderer without pulling in the
        // non-static helper.
        int dot = vertex.lastIndexOf('.');
        String simple = dot >= 0 ? vertex.substring(dot + 1) : vertex;
        return simple.startsWith("<");
    }

    /**
     * Render-time noise filter: returns {@code true} for sink-only anonymous/synthetic vertices
     * (those with <em>no</em> outgoing edges), so the Class/Cycle Map DOT graph is not cluttered
     * with leaf {@code Outer$1}/{@code Outer$}/{@code <anonymous>}/lambda nodes that contribute
     * nothing to refactor decisions. Active anonymous/synthetic classes (with at least one
     * outgoing edge) are <em>not</em> suppressed and still render.
     *
     * <p>Covers four shapes under one rule:
     * <ul>
     *   <li>Java anonymous inner classes: simple-name suffix after the last {@code $} is purely
     *       numeric (e.g. {@code Outer$1}, {@code Outer$2}).</li>
     *   <li>Java synthetic nested classes with an empty trailing {@code $} (e.g. {@code Outer$}).</li>
     *   <li>Kotlin {@code <anonymous>}: the vertex itself is the literal string.</li>
     *   <li>Kotlin synthetic classes: surfaced with numeric-suffix {@code $N} names exactly like
     *       Java's; the numeric predicate already covers them.</li>
     * </ul>
     *
     * @param graph the class graph; used to compute {@code outDegreeOf(vertex)}
     * @param vertex the candidate vertex
     * @return {@code true} if the vertex should be suppressed from the DOT graph
     */
    static boolean isSinkAnonymousOrSyntheticVertex(Graph<VertexInfo, DefaultWeightedEdge> graph, String vertex) {
        VertexInfo probe = VertexInfo.forName(vertex);
        if (!graph.containsVertex(probe)) {
            return false;
        }
        if (graph.outDegreeOf(probe) != 0) {
            return false; // keep nodes that reach out
        }
        if (isAnonymousFqn(vertex)) {
            return true; // Kotlin <anonymous> / literal anonymous sink
        }
        String simple = simpleNameAfterLastDollar(vertex); // post-last-`.` then post-last-`$`
        if (simple.isEmpty()) {
            return true; // trailing-$ synthetic
        }
        return simple.matches("\\d+"); // Outer$1, Foo$2, lambda $$
    }

    /**
     * Computes the simple-name suffix after the last {@code $}: take the text after the last
     * {@code .} (the simple class name), then the text after the last {@code $} within it.
     */
    private static String simpleNameAfterLastDollar(String vertex) {
        int dot = vertex.lastIndexOf('.');
        String afterDot = dot >= 0 ? vertex.substring(dot + 1) : vertex;
        int dollar = afterDot.lastIndexOf('$');
        return dollar >= 0 ? afterDot.substring(dollar + 1) : afterDot;
    }

    /**
     * Builds raw DOT source for a ranked class cycle as nested package clusters, consulting the
     * full class graph for the cycle members' node ids.
     */
    String buildRawClassCycleDot(
            Graph<VertexInfo, DefaultWeightedEdge> classGraph,
            RankedCycle cycle,
            String repoUrl,
            CodebaseGraphDTO codebaseGraphDTO) {
        HierarchyDTO hierarchy = HierarchyBuilder.buildCycleHierarchy(
                classGraph, codebaseGraphDTO, classesToRemove, packagesToRemove, classRelationshipsToRemove, cycle);
        return NestedDotBuilder.buildNestedDot(hierarchy, repoUrl, true);
    }

    /**
     * Builds raw DOT source for the package relationship graph: one ellipse node per
     * participating package and the relationship edges between them — the main-branch
     * package map, rendered by vizdom like the class and cycle graphs. The
     * {@code repoUrl} and {@code codebaseGraphDTO} parameters are retained for
     * signature compatibility; the full hierarchy stays in refactor-first.json.
     */
    String buildRawPackageGraphDot(
            Graph<VertexInfo, DefaultWeightedEdge> packageGraph, String repoUrl, CodebaseGraphDTO codebaseGraphDTO) {
        HierarchyDTO hierarchy = HierarchyBuilder.buildPackageHierarchy(
                packageGraph, codebaseGraphDTO, packagesToRemove, packageRelationshipsToRemove);
        return NestedDotBuilder.buildPackageDot(hierarchy, repoUrl);
    }

    static String renderSafePackageNodeId(String packageName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(packageName.getBytes(StandardCharsets.UTF_8));
            return "package_" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    /** Escapes a value for a DOT quoted string (quotes, backslashes, line breaks). */
    static String escapeDotQuoted(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /** Slugifies a cycle name into the graph identifier used by rf-data and popup buttons. */
    static String graphIdentifier(String value) {
        String original = value == null ? "" : value;
        String sanitized = original.replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitized.isEmpty()) {
            sanitized = "cycle";
        }
        return "graph_" + sanitized + "_" + Integer.toUnsignedString(original.hashCode(), 36);
    }
}
