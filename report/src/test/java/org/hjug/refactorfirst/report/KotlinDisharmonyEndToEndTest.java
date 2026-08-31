package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test for the three new Kotlin disharmony types in the HTML report.
 * Uses the existing Kotlin disharmony test fixtures which contain:
 * - ExtensionHost.kt (EXCESSIVE_EXTENSIONS)
 * - Shape.kt (LARGE_SEALED_HIERARCHY)
 * - Money.kt (DATA_CLASS_WITH_LOGIC)
 * - PureData.kt (control - should NOT trigger DATA_CLASS_WITH_LOGIC)
 */
@DisplayName("Kotlin Disharmony End-to-End Report Test")
class KotlinDisharmonyEndToEndTest {

    @TempDir
    File tempDir;

    @Test
    @DisplayName("SimpleHtmlReport generates sections for all three Kotlin disharmony types")
    void reportContainsAllThreeKotlinDisharmonies() throws Exception {
        // Copy test fixtures to temp directory
        File testSourceDir =
                new File("../codebase-graph-builder/src/test/resources/kotlinDisharmonySrcDirectory").getAbsoluteFile();
        File projectDir = new File(tempDir, "kotlin-test-project");
        Files.createDirectories(projectDir.toPath());

        // Copy Kotlin sources
        copyDirectory(testSourceDir, new File(projectDir, "src/main/kotlin"));

        // Initialize git repo (required for report)
        initGitRepo(projectDir);

        // Run SimpleHtmlReport
        SimpleHtmlReport report = new SimpleHtmlReport();
        File outputDir = new File(tempDir, "output");
        outputDir.mkdirs();

        report.execute(
                50, // backEdgeAnalysisCount
                true, // analyzeCycles
                false, // showDetails
                false, // minifyHtml
                true, // excludeTests
                "src/test", // testSourceDirectory
                "KotlinTest", // projectName
                "1.0.0", // projectVersion
                projectDir, // baseDir
                outputDir.getPath() // outputDirectory
                );

        // Read generated HTML
        File htmlFile = new File(outputDir, "refactor-first-report.html");
        assertTrue(htmlFile.exists(), "Report HTML file should be generated");

        String html = Files.readString(htmlFile.toPath());

        // Verify all three disharmony types appear in the report
        assertTrue(html.contains("Excessive Extensions"), "Report should contain Excessive Extensions section");
        assertTrue(html.contains("id=\"EXCESSIVE_EXTENSIONS\""), "Report should have Excessive Extensions anchor");
        assertTrue(html.contains("ExtensionHost"), "Report should reference ExtensionHost class");

        assertTrue(html.contains("Large Sealed Hierarchy"), "Report should contain Large Sealed Hierarchy section");
        assertTrue(html.contains("id=\"LARGE_SEALED_HIERARCHY\""), "Report should have Large Sealed Hierarchy anchor");
        assertTrue(html.contains("Shape"), "Report should reference Shape class");

        assertTrue(html.contains("Data Class with Logic"), "Report should contain Data Class with Logic section");
        assertTrue(html.contains("id=\"DATA_CLASS_WITH_LOGIC\""), "Report should have Data Class with Logic anchor");
        assertTrue(html.contains("Money"), "Report should reference Money class");

        // Verify control class (PureData) does NOT appear in Data Class with Logic section
        // Extract the DATA_CLASS_WITH_LOGIC section to avoid false positives from
        // class map DOT, source hyperlinks, or relationship tables mentioning PureData
        int sectionStart = html.indexOf("id=\"DATA_CLASS_WITH_LOGIC\"");
        assertTrue(sectionStart >= 0, "DATA_CLASS_WITH_LOGIC section should exist");
        int sectionEnd = html.indexOf("id=\"", sectionStart + 1);
        String dataClassSection =
                (sectionEnd >= 0) ? html.substring(sectionStart, sectionEnd) : html.substring(sectionStart);
        assertFalse(dataClassSection.contains("PureData"), "PureData should not be flagged as Data Class with Logic");

        // Verify menu contains all three
        assertTrue(html.contains("<a href=\"#EXCESSIVE_EXTENSIONS\">Excessive Extensions</a>"));
        assertTrue(html.contains("<a href=\"#LARGE_SEALED_HIERARCHY\">Large Sealed Hierarchy</a>"));
        assertTrue(html.contains("<a href=\"#DATA_CLASS_WITH_LOGIC\">Data Class with Logic</a>"));
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
