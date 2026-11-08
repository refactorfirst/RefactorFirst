package org.hjug.refactorfirst.report;

import static org.hjug.refactorfirst.report.ReportWriter.writeReportToDisk;

import in.wilsonl.minifyhtml.Configuration;
import in.wilsonl.minifyhtml.MinifyHtml;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.RankedCycle;
import org.hjug.git.GitLogReader;
import org.hjug.graphbuilder.VertexInfo;
import org.hjug.graphbuilder.metrics.DisharmonyTypes;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 * The strictly-HTML report page. Renders from
 * {@code templates/refactor-first-simple-report.mustache} through the shared
 * {@link ReportModelBuilder} + {@link ViewModelFactory} pipeline (Phase P3), and carries the
 * disharmony metadata + rendering helpers shared by the model builder, the JSON report and
 * the interactive {@link HtmlReport} page.
 */
@Slf4j
public class SimpleHtmlReport {

    public static final List<DisharmonySpec> DISHARMONY_SPECS = List.of(
            new DisharmonySpec(
                    DisharmonyTypes.GOD_CLASS,
                    "GOD",
                    "God Classes",
                    false,
                    "God Classes take on too much responsibility,",
                    "Extract related islands of functionality into separate classes.  Leave God classes that don't change often alone."),
            new DisharmonySpec(
                    DisharmonyTypes.DATA_CLASS,
                    "DATA_CLASS",
                    "Data Classes",
                    false,
                    "Data Classes are dumb data holders that other classes rely on.",
                    "Move the data/variable(s) to the same class as the operation."),
            new DisharmonySpec(
                    DisharmonyTypes.BRAIN_CLASS,
                    "BRAIN_CLASS",
                    "Brain Classes",
                    false,
                    "Brain Classes are complex, lack cohesion, and have at least one Brain Method.",
                    "Decompose Brain Methods into smaller methods."),
            new DisharmonySpec(
                    DisharmonyTypes.REFUSED_PARENT_BEQUEST,
                    "RPB",
                    "Refused Parent Bequest",
                    false,
                    "Child class is large and often complex, but doesn't override or use many of the parent class's methods",
                    "Do one or more of the following:<br>"
                            + "- Extract the child class into a separate class.  Move the methods that are used from the parent class into the child class.<br>"
                            + "- Make unused protected members private in the parent class.<br>"
                            + "- If a parent class has multiple children, move methods not used by all descendants to another class."),
            new DisharmonySpec(
                    DisharmonyTypes.TRADITION_BREAKER,
                    "TB",
                    "Tradition Breakers",
                    false,
                    "Child class adds many new public methods, but doesn't override or use many of the parent class's methods",
                    "Do one or more of the following:<br>"
                            + "- Make public child methods unused outside of the class non-public.<br>"
                            + "- Pull duplicated methods in child classes into the parent class.<br>"
                            + "- Move methods in the child class that are unrelated to the parent class to another class.<br>"
                            + "- Remove the child class from the hierarchy."),
            new DisharmonySpec(
                    DisharmonyTypes.SIGNIFICANT_DUPLICATION,
                    "SIG_DUP",
                    "Significant Duplication",
                    false,
                    "Nearly identical code is found in multiple classes, leading to increased maintenance costs.",
                    "- Move duplicated code in the same class into a new method.<br>"
                            + "- Move duplicated code into a separate or parent class.<br>"
                            + "- Move duplicated code in two child classes or in parent/child classes into the parent class."),
            new DisharmonySpec(
                    DisharmonyTypes.BRAIN_METHOD,
                    "BRAIN_METHOD",
                    "Brain Methods",
                    true,
                    "Method is long, complicated, and uses many variables.",
                    "- Decompose the method into two or more smaller methods.<br>"
                            + "- If part of the method relies heavily on an outside class, extract that functionality out of the calling method and move it to the called class."),
            new DisharmonySpec(
                    DisharmonyTypes.FEATURE_ENVY,
                    "FEATURE_ENVY",
                    "Feature Envy",
                    true,
                    "Method is more interested in data in other classes than its own class.",
                    "Move the method (or part of the method) to the class where it uses the most data."),
            new DisharmonySpec(
                    DisharmonyTypes.INTENSIVE_COUPLING,
                    "INTENSIVE_COUPLING",
                    "Intensive Coupling",
                    true,
                    "Method calls too many methods from a few unrelated classes (often in a separate package).",
                    "Move the calling method to a class more closely related to the other classes that the original method can call."),
            new DisharmonySpec(
                    DisharmonyTypes.DISPERSED_COUPLING,
                    "DISPERSED_COUPLING",
                    "Dispersed Coupling",
                    true,
                    "Method calls a few methods in many classes",
                    "Reduce the size of the calling method.  Extract methods from the calling method into the target classes."),
            new DisharmonySpec(
                    DisharmonyTypes.SHOTGUN_SURGERY,
                    "SHOTGUN_SURGERY",
                    "Shotgun Surgery",
                    true,
                    "Method is called by many methods in many classes",
                    "- Move the method closer to the calling classes (move the behavior closer to the data) if it is small.<br>"
                            + "- If it is a large method, treat it as a Brain Method and decompose it into two or more smaller methods."),
            new DisharmonySpec(
                    DisharmonyTypes.EXCESSIVE_EXTENSIONS,
                    "EXCESSIVE_EXTENSIONS",
                    "Excessive Extensions",
                    false,
                    "Class declares many extension functions across many receiver types, indicating it's trying to extend too many unrelated types.",
                    "Consider moving extension functions closer to the types they extend. Group related extensions into separate files or classes."),
            new DisharmonySpec(
                    DisharmonyTypes.LARGE_SEALED_HIERARCHY,
                    "LARGE_SEALED_HIERARCHY",
                    "Large Sealed Hierarchy",
                    false,
                    "Sealed class has many permitted subtypes, making the hierarchy hard to maintain and exhaustive when expressions become unwieldy.",
                    "Re-evaluate the domain model. Consider grouping subtypes into intermediate sealed classes or using a different pattern."),
            new DisharmonySpec(
                    DisharmonyTypes.DATA_CLASS_WITH_LOGIC,
                    "DATA_CLASS_WITH_LOGIC",
                    "Data Class with Logic",
                    false,
                    "Data class contains non-accessor methods with business logic, violating the data carrier principle.",
                    "Move business logic to separate service classes. Keep data classes as pure data holders with only accessor methods."));

    // Rendering state shared with the DOT builders and the interactive pipeline; re-pointed
    // from the model builder's results before any rendering happens.
    Graph<VertexInfo, DefaultWeightedEdge> classGraph;
    Graph<VertexInfo, DefaultWeightedEdge> packageGraph;
    Map<VertexInfo, AsSubgraph<VertexInfo, DefaultWeightedEdge>> classCycles;
    Map<VertexInfo, AsSubgraph<VertexInfo, DefaultWeightedEdge>> packageCycles;
    Set<VertexInfo> classesToRemove = Set.of(); // initialize for unit tests
    Set<VertexInfo> packagesToRemove = Set.of(); // initialize for unit tests
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

    /**
     * Generates the HTML report and writes it to {@code outputDirectory}.
     *
     * <p>Phase P3/T3.4: the strictly-HTML page renders from
     * {@code templates/refactor-first-simple-report.mustache} through the shared
     * {@link ReportModelBuilder} + {@link ViewModelFactory} pipeline. A missing Git
     * repository still writes a message report (behavior preserved from the
     * string-building era); an analysis failure throws as before.
     */
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

        String reportHtml = maybeMinifyHtml(
                renderSimpleReport(
                        showDetails,
                        edgeAnalysisCount,
                        analyzeCycles,
                        excludeTests,
                        testSourceDirectory,
                        projectName,
                        projectVersion,
                        baseDir),
                minifyHtml);
        writeReportToDisk(outputDirectory, filename, reportHtml);
        log.info("Done! View the report at target/site/{}", filename);
    }

    /**
     * Renders the strictly-HTML simple report page: the model builder analyzes the project,
     * the view model factory translates the DTO, and the template engine renders the page.
     * A missing Git repository renders the message page instead of failing.
     */
    String renderSimpleReport(
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
        Map<String, Object> viewModel;
        if (GitLogReader.getGitDir(effectiveBaseDir) == null) {
            log.info(
                    "Done! No Git repository found!  Please initialize a Git repository and perform an initial commit.");
            viewModel = noGitViewModel(projectName, projectVersion, effectiveBaseDir);
        } else {
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

            viewModel = new ViewModelFactory().createViewModel(reportDTO);
        }

        return new TemplateEngine().render("templates/refactor-first-simple-report.mustache", viewModel);
    }

    /** Builds the minimal view model for the no-Git-repository message page. */
    protected Map<String, Object> noGitViewModel(String projectName, String projectVersion, File effectiveBaseDir) {
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("name", projectName);
        project.put("version", projectVersion);
        project.put("repoUrl", "");
        project.put("baseDir", effectiveBaseDir.getPath());
        project.put("scanTimestamp", formatter.format(Instant.now()));
        project.put("hasAnyDisharmony", false);
        project.put("analysisFailed", false);
        Map<String, Object> viewModel = new LinkedHashMap<>();
        viewModel.put("project", project);
        viewModel.put("noGitRepository", true);
        return viewModel;
    }

    /** Applies the MinifyHtml pass when requested (shared by both report pages). */
    protected String maybeMinifyHtml(String html, boolean minifyHtml) {
        return minifyHtml ? MinifyHtml.minify(html, htmlMinifierConfig) : html;
    }

    /** Reads the repository's remote URL from the project Git metadata. */
    static String getRepoUrl(String projectBaseDir) throws Exception {
        try (GitLogReader glr = new GitLogReader(new File(projectBaseDir))) {
            return glr.getRepoUrl();
        }
    }

    /**
     * Escapes HTML-significant characters in a class-name label for non-DOT HTML table contexts
     * (cycle/edge relationship tables). Most importantly the Kotlin literal {@code "<anonymous>"}
     * must be HTML-escaped to {@code <anonymous>} so the {@code <}/{@code >} do not break the
     * surrounding anchor/table markup.
     */
    static String escapeHtmlLabel(String label) {
        if (label == null) {
            return "";
        }
        return label.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Escapes repository-derived values for use in a quoted HTML attribute. */
    static String escapeHtmlAttribute(String value) {
        return escapeHtmlLabel(value).replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** Builds the summary-table cells for a ranked class cycle. */
    static String[] getRankedCycleSummaryData(RankedCycle rankedCycle) {
        return new String[] {
            // "Cycle Name", "Priority", "Class Count", "Relationship Count"
            getClassName(rankedCycle.getCycleName()),
            rankedCycle.getPriority().toString(),
            String.valueOf(rankedCycle.getCycleNodes().size()),
            String.valueOf(rankedCycle.getEdgeSet().size())
        };
    }

    /** Removes package qualifiers and verbose generic bounds from a method signature. */
    static String getSimpleMethodSignature(String sig) {
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
            param = param.replaceAll("([\\w]+\\.)+([\\w]+)", "$2");

            rawParams[i] = param;
        }

        return methodName + "(" + String.join(",", rawParams) + ")";
    }

    /**
     * Simplifies fully qualified parameter types in a duplication-partner description.
     * upWaitQueue(com.tonikelope.megabasterd.Transference) becomes
     * upWaitQueue(Transference).
     */
    static String simplifyDuplicatePartners(String duplicationPartners) {
        if (duplicationPartners == null) {
            return null;
        }
        // Split the string on the arrow symbol (↔) to handle each partner separately
        String[] parts = duplicationPartners.split("↔");
        List<String> simplifiedParts = new ArrayList<>();
        // Pattern to capture methodName(params) where params may contain fully‑qualified class names
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\w+)\\(([^)]*)\\)");
        for (String part : parts) {
            String trimmed = part.trim();
            java.util.regex.Matcher matcher = pattern.matcher(trimmed);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String methodName = matcher.group(1);
                String params = matcher.group(2);
                // Replace fully‑qualified class names inside the parentheses with simple names
                String simplifiedParams = params.replaceAll("([\\w]+\\.)+([\\w]+)", "$2");
                matcher.appendReplacement(
                        sb, java.util.regex.Matcher.quoteReplacement(methodName + "(" + simplifiedParams + ")"));
            }
            matcher.appendTail(sb);
            simplifiedParts.add(sb.toString().trim());
        }
        return String.join(" ↔ ", simplifiedParts);
    }

    /** Returns the simple class name from a fully qualified class name. */
    static String getClassName(String fqn) {
        // handle no package
        if (!fqn.contains(".")) {
            return fqn;
        }

        int lastIndex = fqn.lastIndexOf(".");
        return fqn.substring(lastIndex + 1);
    }

    /** Extracts the source and target vertex labels from an edge's string representation. */
    static String[] extractVertexes(DefaultWeightedEdge edge) {
        return edge.toString().replace("(", "").replace(")", "").split(":");
    }

    /** Returns the base filename for the generated report. */
    String getOutputName() {
        // This report will generate refactor-first-report.html when invoked in a project with `mvn site`
        return "refactor-first-report";
    }

    static final class DisharmonySpec {
        final String type;
        final String anchorId;
        final String title;
        final boolean methodLevel;
        final String problem;
        final String solution;

        /** Creates the display metadata for one supported disharmony category. */
        DisharmonySpec(
                String type, String anchorId, String title, boolean methodLevel, String problem, String solution) {
            this.type = type;
            this.anchorId = anchorId;
            this.title = title;
            this.methodLevel = methodLevel;
            this.problem = problem;
            this.solution = solution;
        }

        /** Returns the disharmony type identifier. */
        String type() {
            return type;
        }

        /** Returns the HTML anchor identifier. */
        String anchorId() {
            return anchorId;
        }

        /** Returns the display title. */
        String title() {
            return title;
        }

        /** Reports whether the disharmony describes methods rather than classes. */
        boolean methodLevel() {
            return methodLevel;
        }

        /** Returns the problem description. */
        String problem() {
            return problem;
        }

        /** Returns the recommended remediation. */
        String solution() {
            return solution;
        }
    }
}
