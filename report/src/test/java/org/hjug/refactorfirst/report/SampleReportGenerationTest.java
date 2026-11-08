package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase P0/T0.3 fixture generator: renders the small in-memory fixture codebase
 * (parity sources under {@code codebase-graph-builder/src/test/resources/parity})
 * into {@code target/e2e/} for the Playwright E2E suite.
 */
@DisplayName("Sample Report Generation Test")
class SampleReportGenerationTest {

    private static final String FIXTURE_SOURCE = "../codebase-graph-builder/src/test/resources/parity";
    private static final String E2E_OUTPUT_DIR = "target/e2e";

    @TempDir
    File tempDir;

    @Test
    @DisplayName("generates unminified and minified INTERACTIVE fixture reports for the E2E suite")
    void generatesFixtureReportsForE2e() throws Exception {
        File projectDir = new File(tempDir, "parity-project");
        Files.createDirectories(projectDir.toPath());
        copyDirectory(new File(FIXTURE_SOURCE), projectDir);
        initGitRepo(projectDir);

        File e2eDir = new File(E2E_OUTPUT_DIR);
        e2eDir.mkdirs();

        // Leg 1: interactive report, minifyHtml=false -> target/e2e/refactor-first-report.html
        // (T4.8 + the file:// canary open this leg)
        File unminifiedOutput = new File(e2eDir, "refactor-first-report.html");
        HtmlReport htmlReport = new HtmlReport();
        htmlReport.execute(
                50,
                true,
                false,
                false, // minifyHtml
                true,
                "src/test",
                "ParityProject",
                "1.0.0",
                projectDir,
                unminifiedOutput.getParentFile().getAbsolutePath());
        assertTrue(
                unminifiedOutput.exists(),
                "Fixture report should be generated at " + unminifiedOutput.getAbsolutePath());
        String html = Files.readString(unminifiedOutput.toPath());
        assertTrue(html.contains("<html"), "Fixture report should be a complete HTML document");

        // Leg 2: interactive report, minifyHtml=true -> target/e2e/refactor-first-report.minified.html
        // (the report filename is fixed at refactor-first-report.html, so the
        // minified leg lands in a scratch output dir and is copied into target/e2e)
        File minifiedScratch = new File(tempDir, "output-minified");
        minifiedScratch.mkdirs();
        File minifiedSource = new File(minifiedScratch, "refactor-first-report.html");
        HtmlReport minifiedReport = new HtmlReport();
        minifiedReport.execute(
                50,
                true,
                false,
                true, // minifyHtml
                true,
                "src/test",
                "ParityProject",
                "1.0.0",
                projectDir,
                minifiedScratch.getAbsolutePath());
        File minified = new File(e2eDir, "refactor-first-report.minified.html");
        assertTrue(minifiedSource.exists(), "Minified fixture report should be generated");
        Files.copy(minifiedSource.toPath(), minified.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Phase P3/T3.6 leg: renders the parity fixture through the strictly-HTML simple report
     * (empty state) into {@code target/e2e/} so {@code simple-report.spec.js} can assert the
     * file:// gate and axe-core scan on the lightweight page.
     */
    @Test
    @DisplayName("generates the simple fixture report for the simple-report E2E leg")
    void generatesSimpleReportForE2e() throws Exception {
        File projectDir = new File(tempDir, "parity-simple-project");
        Files.createDirectories(projectDir.toPath());
        copyDirectory(new File(FIXTURE_SOURCE), projectDir);
        initGitRepo(projectDir);

        File e2eDir = new File(E2E_OUTPUT_DIR);
        e2eDir.mkdirs();

        // The report filename is fixed at refactor-first-report.html, so the simple leg
        // lands in a scratch output dir and is copied into target/e2e
        File scratch = new File(tempDir, "output-simple");
        scratch.mkdirs();
        SimpleHtmlReport simpleReport = new SimpleHtmlReport();
        simpleReport.execute(
                50,
                true,
                false,
                false,
                true,
                "src/test",
                "ParityProject",
                "1.0.0",
                projectDir,
                scratch.getAbsolutePath());

        File generated = new File(scratch, "refactor-first-report.html");
        assertTrue(generated.exists(), "Simple fixture report should be generated");
        File report = new File(e2eDir, "refactor-first-report.simple.html");
        Files.copy(generated.toPath(), report.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String html = Files.readString(report.toPath());
        assertTrue(html.contains("Congratulations!"), "the parity fixture project has no findings");
    }

    /**
     * Phase P3/T3.6 leg: renders the Kotlin disharmony fixture (guaranteed disharmony
     * sections, relationship tables and cycles) into {@code target/e2e/} so the
     * {@code simple-report.spec.js} E2E suite can assert sticky headers and scan
     * accessibility on a fully-populated page.
     */
    @Test
    @DisplayName("generates a Kotlin disharmony simple report for the populated E2E leg")
    void generatesKotlinDisharmonyReportForE2e() throws Exception {
        File testSourceDir =
                new File("../codebase-graph-builder/src/test/resources/kotlinDisharmonySrcDirectory").getAbsoluteFile();
        File projectDir = new File(tempDir, "kotlin-e2e-project");
        Files.createDirectories(projectDir.toPath());
        copyDirectory(testSourceDir, new File(projectDir, "src/main/kotlin"));
        initGitRepo(projectDir);

        File e2eDir = new File(E2E_OUTPUT_DIR);
        e2eDir.mkdirs();

        // The report filename is fixed at refactor-first-report.html, so the populated
        // legs land in a scratch output dir and are copied into target/e2e (both
        // minifyHtml legs, so T4.8's chart assertions run on both).
        File unminifiedScratch = new File(tempDir, "output-kotlin");
        unminifiedScratch.mkdirs();
        SimpleHtmlReport simpleReport = new HtmlReport();
        simpleReport.execute(
                50,
                true,
                false,
                false,
                true,
                "src/test",
                "KotlinE2EProject",
                "1.0.0",
                projectDir,
                unminifiedScratch.getAbsolutePath());

        File generated = new File(unminifiedScratch, "refactor-first-report.html");
        assertTrue(generated.exists(), "Kotlin disharmony report should be generated");
        File report = new File(e2eDir, "refactor-first-report.kt.html");
        Files.copy(generated.toPath(), report.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String html = Files.readString(report.toPath());
        assertTrue(html.contains("id=\"DATA_CLASS_WITH_LOGIC\""), "Populated report must carry disharmony sections");
        assertTrue(html.contains("<thead"), "Populated report must carry tables with sticky-capable headers");
        assertTrue(html.contains("chart_DATA_CLASS_WITH_LOGIC"), "Populated report must carry chart canvases");

        // Minified leg
        File minifiedScratch = new File(tempDir, "output-kotlin-minified");
        minifiedScratch.mkdirs();
        SimpleHtmlReport minifiedReport = new HtmlReport();
        minifiedReport.execute(
                50,
                true,
                false,
                true,
                true,
                "src/test",
                "KotlinE2EProject",
                "1.0.0",
                projectDir,
                minifiedScratch.getAbsolutePath());
        File minifiedGenerated = new File(minifiedScratch, "refactor-first-report.html");
        assertTrue(minifiedGenerated.exists(), "Minified Kotlin disharmony report should be generated");
        File minifiedReportFile = new File(e2eDir, "refactor-first-report.kt.minified.html");
        Files.copy(
                minifiedGenerated.toPath(),
                minifiedReportFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Simple-template leg (the strictly-HTML page with populated sections) for
        // simple-report.spec.js's sticky-header and axe assertions
        File simpleScratch = new File(tempDir, "output-kotlin-simple");
        simpleScratch.mkdirs();
        SimpleHtmlReport simpleTemplateReport = new SimpleHtmlReport();
        simpleTemplateReport.execute(
                50,
                true,
                false,
                false,
                true,
                "src/test",
                "KotlinE2EProject",
                "1.0.0",
                projectDir,
                simpleScratch.getAbsolutePath());
        File simpleGenerated = new File(simpleScratch, "refactor-first-report.html");
        assertTrue(simpleGenerated.exists(), "Simple Kotlin disharmony report should be generated");
        File simpleReportFile = new File(e2eDir, "refactor-first-report.kt.simple.html");
        Files.copy(
                simpleGenerated.toPath(), simpleReportFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Phase P6/T6.7 leg: renders the page-repo junit4 fixture (rich tables — 47 class
     * relationships, 18 package relationships, 25 cycle summaries, disharmony findings with
     * up to 14 rows) into {@code target/e2e/} so {@code report-features.spec.js} can exercise
     * filtering, sorting, sticky headers, CSV export and copy on fully-populated tables.
     */
    @Test
    @DisplayName("generates a junit4-fixture interactive report for the table-features E2E leg")
    void generatesJunit4FixtureReportForE2e() throws Exception {
        File e2eDir = new File(E2E_OUTPUT_DIR);
        e2eDir.mkdirs();

        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        RefactorFirstReportDTO fixtureDto;
        try (java.io.InputStream in = getClass().getResourceAsStream("/parity/junit4-report.json")) {
            assertNotNull(in, "parity fixture must be on the classpath");
            fixtureDto = mapper.readValue(in, RefactorFirstReportDTO.class);
        }

        String html = new HtmlReport().renderInteractiveReport(fixtureDto, new ReportModelBuilder());
        File report = new File(e2eDir, "refactor-first-report.junit4.html");
        Files.writeString(report.toPath(), html);
        assertTrue(html.contains("data-rf-table=\"class-relationships\""), "the fixture table must render");
        assertTrue(html.contains("data-rf-export=\"class-relationships\""), "the export control must render");
    }

    private void copyDirectory(File source, File target) throws IOException {
        try (Stream<Path> stream = Files.walk(source.toPath())) {
            stream.filter(Files::isRegularFile).forEach(sourcePath -> {
                try {
                    Path relative = source.toPath().relativize(sourcePath);
                    Path targetPath = target.toPath().resolve(relative);
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourcePath, targetPath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private void initGitRepo(File dir) throws Exception {
        runCommand(dir, "git", "init");
        runCommand(dir, "git", "config", "user.email", "test@test.com");
        runCommand(dir, "git", "config", "user.name", "Test User");
        runCommand(dir, "git", "add", ".");
        runCommand(dir, "git", "commit", "-m", "Initial commit");
    }

    private void runCommand(File dir, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(command);
        pb.directory(dir);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", command) + " - " + output);
        }
    }
}
