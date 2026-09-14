package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.hjug.refactorfirst.report.model.ChartJsBubbleDTO;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonGeneratorTest {

    private Path tempDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Creates an isolated project directory for each test. */
    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("jsonGeneratorTest");
    }

    /** Removes the isolated project directory after each test. */
    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    /** Verifies that generation creates the report directory and JSON file. */
    @Test
    void testDirectoryAndFileCreatedIfNotExist() throws Exception {
        JsonGenerator generator = new JsonGenerator();
        File baseDir = tempDir.toFile();

        generator.execute(50, true, false, true, "src/test", "TestProject", "1.0.0", baseDir, null);

        Path dotRefactorFirstDir = tempDir.resolve(".refactorfirst");
        Path jsonFile = dotRefactorFirstDir.resolve("refactor-first.json");

        assertTrue(Files.exists(dotRefactorFirstDir), ".refactorfirst directory should be created");
        assertTrue(Files.exists(jsonFile), "refactor-first.json file should be created");

        RefactorFirstReportDTO report = objectMapper.readValue(jsonFile.toFile(), RefactorFirstReportDTO.class);
        assertNotNull(report);
        assertEquals("TestProject", report.getProject().getName());
        assertEquals("1.0.0", report.getProject().getVersion());
        assertTrue(report.getProject().isAnalysisFailed());
    }

    /** Verifies that a configured output directory controls where report artifacts are written. */
    @Test
    void testConfiguredOutputDirectoryIsHonored() throws Exception {
        Path outputDir = tempDir.resolve("custom-output");

        new JsonGenerator()
                .execute(
                        50,
                        true,
                        false,
                        true,
                        "src/test",
                        "TestProject",
                        "1.0.0",
                        tempDir.toFile(),
                        outputDir.toFile());

        assertTrue(Files.exists(outputDir.resolve(".refactorfirst/refactor-first.json")));
        assertFalse(Files.exists(tempDir.resolve(".refactorfirst/refactor-first.json")));

        String viewer = Files.readString(outputDir.resolve(".refactorfirst/index.html"));
        assertTrue(viewer.contains("accept=\".json,.mustache\" multiple"));
        assertFalse(viewer.contains("getFallbackTemplate"));
    }

    /** Verifies HTML encoding used for repository-derived text and attribute values. */
    @Test
    void testRepositoryTextEncoding() {
        assertEquals("&lt;script&gt;&amp;", SimpleHtmlReport.escapeHtmlLabel("<script>&"));
        assertEquals(
                "path&quot; onclick=&quot;alert(1)&#39;",
                SimpleHtmlReport.escapeHtmlAttribute("path\" onclick=\"alert(1)'"));
    }

    /** Verifies that generation atomically replaces an existing report file. */
    @Test
    void testFileReplacedIfAlreadyExists() throws Exception {
        Path dotRefactorFirstDir = tempDir.resolve(".refactorfirst");
        Files.createDirectories(dotRefactorFirstDir);
        Path jsonFile = dotRefactorFirstDir.resolve("refactor-first.json");
        Files.writeString(jsonFile, "{\"stale\": true}");

        JsonGenerator generator = new JsonGenerator();
        File baseDir = tempDir.toFile();

        generator.execute(50, true, false, true, "src/test", "UpdatedProject", "2.0.0", baseDir, null);

        assertTrue(Files.exists(jsonFile));
        String content = Files.readString(jsonFile);
        assertFalse(content.contains("stale"), "Old content must be completely replaced");

        RefactorFirstReportDTO report = objectMapper.readValue(jsonFile.toFile(), RefactorFirstReportDTO.class);
        assertEquals("UpdatedProject", report.getProject().getName());
        assertEquals("2.0.0", report.getProject().getVersion());
    }

    /** Verifies that bubble size and color reflect priority. */
    @Test
    void testCalculateBubbleRadiusAndColor() {
        JsonGenerator generator = new JsonGenerator();

        // Priority 1 out of 10 should have max radius and red color
        ChartJsBubbleDTO bubblePriority1 = generator.createBubble("TestClass", "TestClass.java", 5, 10, 1, 10);

        assertEquals(1, bubblePriority1.getPriority());
        assertEquals(24, bubblePriority1.getR(), "Priority 1 should have max radius 24");
        assertTrue(
                bubblePriority1.getColor().contains("235, 64, 52")
                        || bubblePriority1.getColor().contains("231, 76, 60"),
                "Priority 1 should be red");

        // Priority 10 out of 10 should have min radius and green color
        ChartJsBubbleDTO bubblePriority10 = generator.createBubble("CleanClass", "CleanClass.java", 1, 1, 10, 10);

        assertEquals(10, bubblePriority10.getPriority());
        assertEquals(6, bubblePriority10.getR(), "Max priority (lowest urgency) should have min radius 6");
        assertTrue(
                bubblePriority10.getColor().contains("39, 174, 96")
                        || bubblePriority10.getColor().contains("46, 204, 113"),
                "Lowest priority should be green");
    }

    /** Verifies report generation against a minimal Git repository fixture. */
    @Test
    void testGenerateReportDataWithGitRepoFixture() throws Exception {
        File repoDir = tempDir.toFile();
        File gitDir = new File(repoDir, ".git");
        gitDir.mkdirs();

        File srcDir = new File(repoDir, "src/main/java/com/example");
        srcDir.mkdirs();
        File javaFile = new File(srcDir, "SampleService.java");
        Files.writeString(
                javaFile.toPath(),
                """
                package com.example;

                public class SampleService {
                    public String execute() {
                        return "Hello World";
                    }
                }
                """);

        // Git init and commit
        ProcessBuilder pb = new ProcessBuilder("git", "init");
        pb.directory(repoDir);
        pb.start().waitFor();
        pb = new ProcessBuilder("git", "config", "user.email", "test@test.com");
        pb.directory(repoDir);
        pb.start().waitFor();
        pb = new ProcessBuilder("git", "config", "user.name", "Test");
        pb.directory(repoDir);
        pb.start().waitFor();
        pb = new ProcessBuilder("git", "add", ".");
        pb.directory(repoDir);
        pb.start().waitFor();
        pb = new ProcessBuilder("git", "commit", "-m", "initial");
        pb.directory(repoDir);
        pb.start().waitFor();

        JsonGenerator generator = new JsonGenerator();
        generator.execute(50, true, false, true, "src/test", "SampleProject", "1.0.0", repoDir, null);

        Path jsonFile = tempDir.resolve(".refactorfirst").resolve("refactor-first.json");
        assertTrue(Files.exists(jsonFile));

        RefactorFirstReportDTO report = objectMapper.readValue(jsonFile.toFile(), RefactorFirstReportDTO.class);
        assertNotNull(report);
        assertEquals("SampleProject", report.getProject().getName());
        assertFalse(report.getProject().isAnalysisFailed());
        assertNotNull(report.getClassMap());
        assertTrue(report.getClassMap().getClassCount() >= 1);
        assertNotNull(report.getClassMap().getDot());
        // SampleService has no dependencies, so it has no edges and is not rendered in the DOT
        // (buildRawClassGraphDot only renders vertices with edges)
        assertTrue(report.getClassMap().getDot().contains("digraph G"));
    }
}
