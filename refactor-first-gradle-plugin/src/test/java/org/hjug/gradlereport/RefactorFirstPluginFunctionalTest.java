package org.hjug.gradlereport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Functional tests for the RefactorFirst Gradle plugin, using Gradle TestKit against a sample project.
 * These tests pin the output-location contract and the JsonGenerator wiring.
 */
class RefactorFirstPluginFunctionalTest {

    @TempDir
    File projectDir;

    @BeforeEach
    void setUp() throws Exception {
        newSampleProject(projectDir);
    }

    /** T4: the full HTML report lands in build/reports/refactorfirst. */
    @Test
    void htmlReportWritesToBuildReportsRefactorFirst() {
        BuildResult result = runGradle(projectDir, "refactorFirstHtmlReport");
        assertNotNull(result.task(":refactorFirstHtmlReport"));
        File report = new File(projectDir, "build/reports/refactorfirst/refactor-first-report.html");
        assertTrue(report.isFile(), "Expected HTML report at " + report);
        assertTrue(report.length() > 0, "HTML report must not be empty");
    }

    /** T5: the simple HTML report lands in build/reports/refactorfirst. */
    @Test
    void simpleHtmlReportWritesToBuildReportsRefactorFirst() {
        BuildResult result = runGradle(projectDir, "refactorFirstSimpleHtmlReport");
        assertNotNull(result.task(":refactorFirstSimpleHtmlReport"));
        File report = new File(projectDir, "build/reports/refactorfirst/refactor-first-report.html");
        assertTrue(report.isFile(), "Expected simple HTML report at " + report);
        assertTrue(report.length() > 0, "Simple HTML report must not be empty");
    }

    /** T6: the CSV report lands in build/reports/refactorfirst with the contract file name. */
    @Test
    void csvReportWritesToBuildReportsRefactorFirst() throws IOException {
        BuildResult result = runGradle(projectDir, "refactorFirstCsvReport");
        assertNotNull(result.task(":refactorFirstCsvReport"));
        File reportsDir = new File(projectDir, "build/reports/refactorfirst");
        assertTrue(reportsDir.isDirectory(), "Expected reports directory at " + reportsDir);
        File[] csvFiles = reportsDir.listFiles((dir, name) -> name.matches("RefFirst_P.*_PV.*_PD.*\\.csv"));
        assertNotNull(csvFiles);
        assertTrue(
                csvFiles.length == 1 && csvFiles[0].length() > 0,
                "Expected exactly one non-empty RefFirst_P*_PV*_PD*.csv in " + reportsDir);
    }

    /**
     * T7: the JSON report runs through org.hjug.refactorfirst.report.JsonGenerator —
     * it produces {@code .refactorfirst/refactor-first.json} plus the bundled viewer resources
     * and never the legacy {@code refactor-first-data.json} of JsonReportExecutor.
     */
    @Test
    void jsonReportProducesJsonGeneratorOutput() throws IOException {
        BuildResult result = runGradle(projectDir, "refactorFirstJsonReport");
        assertNotNull(result.task(":refactorFirstJsonReport"));

        File reportDir = new File(projectDir, ".refactorfirst");
        File json = new File(reportDir, "refactor-first.json");
        assertTrue(json.isFile(), "Expected JSON report at " + json);
        assertTrue(json.length() > 0, "JSON report must not be empty");

        // Viewer resources only copied by JsonGenerator
        assertTrue(new File(reportDir, "index.html").isFile(), "Expected bundled viewer index.html");
        assertTrue(
                new File(reportDir, "refactor-first-report.mustache").isFile(),
                "Expected bundled refactor-first-report.mustache");

        // Parses as the JsonGenerator DTO (not the legacy JsonReport DTO)
        RefactorFirstReportDTO dto = new ObjectMapper().readValue(json, RefactorFirstReportDTO.class);
        assertNotNull(dto.getProject(), "Expected RefactorFirstReportDTO with project metadata");

        // refactor-first-data.json is only produced by the former JsonReportExecutor wiring
        try (Stream<Path> paths = Files.walk(projectDir.toPath())) {
            List<Path> legacy = paths.filter(p -> p.getFileName().toString().equals("refactor-first-data.json"))
                    .toList();
            assertTrue(legacy.isEmpty(), "refactor-first-data.json must not be created anywhere: " + legacy);
        }
    }

    /** T8: none of the legacy output locations are created by any task. */
    @Test
    void noReportsAtLegacyLocations() throws IOException {
        runGradle(
                projectDir,
                "refactorFirstHtmlReport",
                "refactorFirstSimpleHtmlReport",
                "refactorFirstCsvReport",
                "refactorFirstJsonReport");

        try (Stream<Path> paths = Files.walk(projectDir.toPath())) {
            List<Path> legacy = paths.filter(p -> {
                        Path rel = projectDir.toPath().relativize(p);
                        String name = p.getFileName().toString();
                        return rel.startsWith(Path.of("target", "site"))
                                || name.equals("refactor-first-data.json")
                                || (rel.getNameCount() == 1 && name.equals("reports"));
                    })
                    .toList();
            assertTrue(legacy.isEmpty(), "No reports at legacy locations, found: " + legacy);
        }
    }

    /** T9: every task reports SUCCESS on repeated execution — never UP-TO-DATE or FROM-CACHE. */
    @Test
    void tasksRunEveryTime() {
        String[] tasks = {
            "refactorFirstHtmlReport",
            "refactorFirstSimpleHtmlReport",
            "refactorFirstCsvReport",
            "refactorFirstJsonReport"
        };
        BuildResult first = runGradle(projectDir, tasks);
        BuildResult second = runGradle(projectDir, tasks);
        for (String taskName : tasks) {
            assertEquals(
                    TaskOutcome.SUCCESS,
                    first.task(":" + taskName).getOutcome(),
                    "First run of " + taskName + " must succeed");
            assertEquals(
                    TaskOutcome.SUCCESS,
                    second.task(":" + taskName).getOutcome(),
                    "Second run of " + taskName + " must rerun, not be UP_TO_DATE/FROM_CACHE");
        }
    }

    /** T10: repeated JSON runs regenerate output — guards against accidental caching. */
    @Test
    void outputsChangeWhenSourceChanges() throws Exception {
        runGradle(projectDir, "refactorFirstJsonReport");
        File json = new File(projectDir, ".refactorfirst/refactor-first.json");
        assertTrue(json.isFile(), "Expected JSON report after first run");
        String firstContent = Files.readString(json.toPath());

        // Add a class so the class graph (and thus the JSON) provably changes
        Path added = projectDir.toPath().resolve(Path.of("src", "main", "java", "com", "example", "Another.java"));
        Files.writeString(
                added,
                """
                package com.example;

                public class Another {
                    public String echo(String value) {
                        return value;
                    }
                }
                """);
        commitAll(projectDir, "Second commit");
        runGradle(projectDir, "refactorFirstJsonReport");
        String secondContent = Files.readString(json.toPath());

        assertFalse(firstContent.equals(secondContent), "JSON report must be regenerated when sources change");
    }

    /**
     * Version regression: apply() runs while the plugins {} block is being evaluated, before the build
     * script sets the project version. The default version must therefore be read when the task is
     * realized (after build-script evaluation), and the CSV file name must carry the real version.
     */
    @Test
    void csvReportFileNameUsesProjectVersionSetAfterPluginsBlock() {
        try {
            Files.writeString(
                    projectDir.toPath().resolve("build.gradle.kts"),
                    """
                    plugins { id("org.hjug.refactorfirst") }
                    version = "1.2.3"
                    """);
            commitAll(projectDir, "Set project version");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        runGradle(projectDir, "refactorFirstCsvReport");

        File reportsDir = new File(projectDir, "build/reports/refactorfirst");
        assertTrue(reportsDir.isDirectory(), "Expected reports directory at " + reportsDir);
        File[] csvFiles = reportsDir.listFiles((dir, name) -> name.matches("RefFirst_P.*_PV1\\.2\\.3_PD.*\\.csv"));
        assertNotNull(csvFiles);
        assertTrue(
                csvFiles.length == 1 && csvFiles[0].length() > 0,
                "Expected exactly one non-empty RefFirst_P*_PV1.2.3_PD*.csv in " + reportsDir);
    }

    /** T12: extension overrides are honored by the HTML report task. */
    @Test
    void extensionOverridesAreHonored() throws Exception {
        Files.writeString(
                projectDir.toPath().resolve("build.gradle.kts"),
                """
                plugins { id("org.hjug.refactorfirst") }
                refactorFirst {
                    outputDirectory = "build/rf"
                    projectName = "CustomName"
                }
                """);
        commitAll(projectDir, "Configure extension");

        runGradle(projectDir, "refactorFirstHtmlReport");

        File report = new File(projectDir, "build/rf/refactor-first-report.html");
        assertTrue(report.isFile(), "Expected overridden output dir at " + report);
        String html = Files.readString(report.toPath());
        assertTrue(html.contains("CustomName"), "Expected overridden project name in the report");

        // The default location must not be used when overridden
        assertFalse(
                new File(projectDir, "build/reports/refactorfirst/refactor-first-report.html").exists(),
                "Output must not land at the default location when outputDirectory is overridden");
    }

    /**
     * Configuration-cache hygiene: the tasks must be configuration-cache compatible
     * (no getProject() at execution time), so a stored entry can be reused with no problems.
     */
    @Test
    void configurationCacheRunHasNoProblems() {
        BuildResult first = runGradleWithoutQuiet(projectDir, "refactorFirstJsonReport", "--configuration-cache");
        assertTrue(
                first.getOutput().contains("Configuration cache entry stored"),
                "Expected configuration cache entry to be stored:\n" + first.getOutput());
        BuildResult second = runGradleWithoutQuiet(projectDir, "refactorFirstJsonReport", "--configuration-cache");
        assertTrue(
                second.getOutput().contains("Reusing configuration cache"),
                "Expected configuration cache to be reused with no problems:\n" + second.getOutput());
    }

    /** Runs the given tasks against the sample project (quiet) and expects success. */
    static BuildResult runGradle(File projectDir, String... tasks) {
        List<String> arguments = new ArrayList<>(List.of(tasks));
        arguments.add("--quiet");
        return runGradleRaw(projectDir, arguments);
    }

    /** Runs the given arguments against the sample project and expects success. */
    static BuildResult runGradleWithoutQuiet(File projectDir, String... args) {
        return runGradleRaw(projectDir, List.of(args));
    }

    private static BuildResult runGradleRaw(File projectDir, List<String> arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments(arguments)
                .build();
    }

    static void commitAll(File dir, String message) throws Exception {
        try (Git git = Git.open(dir)) {
            git.add().addFilepattern(".").call();
            git.commit()
                    .setAuthor("RefactorFirst Test", "test@example.com")
                    .setMessage(message)
                    .call();
        }
    }

    /**
     * Creates a minimal Gradle project that applies the plugin and contains one Java class
     * committed to a Git repository (the report layer early-returns without a .git dir).
     */
    static void newSampleProject(File dir) throws Exception {
        Files.writeString(dir.toPath().resolve("settings.gradle.kts"), "rootProject.name = \"sample\"\n");
        Files.writeString(dir.toPath().resolve("build.gradle.kts"), "plugins { id(\"org.hjug.refactorfirst\") }\n");

        Path source = dir.toPath().resolve(Path.of("src", "main", "java", "com", "example", "Sample.java"));
        Files.createDirectories(source.getParent());
        Files.writeString(
                source,
                """
                package com.example;

                public class Sample {
                    public String greet() {
                        return "hello";
                    }
                }
                """);

        try (Git git = Git.init().setDirectory(dir).call()) {
            git.add().addFilepattern(".").call();
            git.commit()
                    .setAuthor("RefactorFirst Test", "test@example.com")
                    .setMessage("Initial commit")
                    .call();
        }
    }
}
