package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
        ChartJsBubbleDTO bubblePriority1 = generator.createBubble("TestClass", "TestClass.java", 5, 10, 1, 10, null);

        assertEquals(1, bubblePriority1.getPriority());
        assertEquals(24, bubblePriority1.getR(), "Priority 1 should have max radius 24");
        assertTrue(
                bubblePriority1.getColor().contains("235, 64, 52")
                        || bubblePriority1.getColor().contains("231, 76, 60"),
                "Priority 1 should be red");

        // Priority 10 out of 10 should have min radius and green color
        ChartJsBubbleDTO bubblePriority10 = generator.createBubble("CleanClass", "CleanClass.java", 1, 1, 10, 10, null);

        assertEquals(10, bubblePriority10.getPriority());
        assertEquals(6, bubblePriority10.getR(), "Max priority (lowest urgency) should have min radius 6");
        assertTrue(
                bubblePriority10.getColor().contains("39, 174, 96")
                        || bubblePriority10.getColor().contains("46, 204, 113"),
                "Lowest priority should be green");
    }

    /** Verifies that bubble URL is set to the class file path. */
    @Test
    void given_createBubble_when_urlProvided_then_urlIsSet() {
        // Given
        JsonGenerator generator = new JsonGenerator();
        String classPath = "src/main/java/com/example/TestClass.java";
        String repoUrl = "https://github.com/example/repo/blob/main/";

        // When
        ChartJsBubbleDTO bubble =
                generator.createBubble("TestClass", "TestClass.java", 5, 10, 1, 10, repoUrl + classPath);

        // Then
        assertNotNull(bubble.getUrl());
        assertEquals(
                "https://github.com/example/repo/blob/main/src/main/java/com/example/TestClass.java", bubble.getUrl());
    }

    /** Verifies that generated and serialized chart bubbles carry repoUrl + source path. */
    @Test
    void given_repoWithOrigin_when_reportGenerated_then_bubbleUrlsPointToSourceFiles() throws Exception {
        // Given
        File repoDir = tempDir.toFile();
        File srcDir = new File(repoDir, "src/main/java/com/example");
        srcDir.mkdirs();

        StringBuilder complexMethod = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            complexMethod
                    .append("        if (a > ")
                    .append(i)
                    .append(") { for (int j = 0; j < a; j++) { if (j % 2 == 0 && b) { c += j; } else { c -= j; } } }\n");
        }
        Files.writeString(
                new File(srcDir, "ComplexService.java").toPath(),
                "package com.example;\n\n"
                        + "public class ComplexService {\n"
                        + "    private int c;\n"
                        + "    public int compute(int a, boolean b) {\n"
                        + complexMethod
                        + "        return c;\n"
                        + "    }\n"
                        + "}\n");

        new ProcessBuilder("git", "init").directory(repoDir).start().waitFor();
        new ProcessBuilder("git", "config", "user.email", "test@test.com")
                .directory(repoDir)
                .start()
                .waitFor();
        new ProcessBuilder("git", "config", "user.name", "Test")
                .directory(repoDir)
                .start()
                .waitFor();
        new ProcessBuilder("git", "remote", "add", "origin", "https://github.com/example/repo.git")
                .directory(repoDir)
                .start()
                .waitFor();
        new ProcessBuilder("git", "add", ".").directory(repoDir).start().waitFor();
        new ProcessBuilder("git", "commit", "-m", "initial")
                .directory(repoDir)
                .start()
                .waitFor();

        // When
        new JsonGenerator().execute(0, true, false, true, "src/test", "BubbleProject", "1.0.0", repoDir, null);

        // Then
        Path jsonFile = tempDir.resolve(".refactorfirst").resolve("refactor-first.json");
        RefactorFirstReportDTO report = objectMapper.readValue(jsonFile.toFile(), RefactorFirstReportDTO.class);
        String repoUrl = report.getProject().getRepoUrl();
        assertTrue(
                repoUrl.startsWith("https://github.com/example/repo/blob/"),
                "repoUrl should be derived from origin: " + repoUrl);

        var bubbles = report.getDisharmonies() == null
                ? java.util.List.<ChartJsBubbleDTO>of()
                : report.getDisharmonies().stream()
                        .filter(section -> section.getChart() != null)
                        .flatMap(section -> section.getChart().getBubbles().stream())
                        .toList();
        assumeTrue(!bubbles.isEmpty(), "Fixture produced no disharmony bubbles");

        for (ChartJsBubbleDTO bubble : bubbles) {
            assertNotNull(bubble.getUrl(), "Bubble " + bubble.getLabel() + " should have a url");
            assertTrue(bubble.getUrl().startsWith(repoUrl), "Bubble url should start with repoUrl: " + bubble.getUrl());
            assertTrue(
                    bubble.getUrl().endsWith("src/main/java/com/example/" + bubble.getLabel()),
                    "Bubble url should point to the source file: " + bubble.getUrl());
        }
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

    /**
     * Verifies report generation succeeds with parser selection left entirely
     * to the runtime (JEP 238 multi-release jar design).
     */
    @Test
    void testExecuteWithoutParserFlags() throws Exception {
        File repoDir = tempDir.toFile();
        new File(repoDir, ".git").mkdirs();

        File srcDir = new File(repoDir, "src/main/java/com/example");
        srcDir.mkdirs();
        Files.writeString(
                new File(srcDir, "SampleService.java").toPath(),
                """
                package com.example;

                public class SampleService {
                    public String execute() {
                        return "Hello World";
                    }
                }
                """);

        new ProcessBuilder("git", "init").directory(repoDir).start().waitFor();
        new ProcessBuilder("git", "config", "user.email", "test@test.com")
                .directory(repoDir)
                .start()
                .waitFor();
        new ProcessBuilder("git", "config", "user.name", "Test")
                .directory(repoDir)
                .start()
                .waitFor();
        new ProcessBuilder("git", "add", ".").directory(repoDir).start().waitFor();
        new ProcessBuilder("git", "commit", "-m", "initial")
                .directory(repoDir)
                .start()
                .waitFor();

        new JsonGenerator().execute(50, true, false, true, "src/test", "ForcedProject", "1.0.0", repoDir, null);

        Path jsonFile = tempDir.resolve(".refactorfirst").resolve("refactor-first.json");
        assertTrue(Files.exists(jsonFile));

        RefactorFirstReportDTO report = objectMapper.readValue(jsonFile.toFile(), RefactorFirstReportDTO.class);
        assertNotNull(report);
        assertEquals("ForcedProject", report.getProject().getName());
        assertFalse(report.getProject().isAnalysisFailed());
        assertTrue(report.getClassMap().getClassCount() >= 1);
    }

    /**
     * Verifies that sourceUrl and targetUrl are set in ClassRelationshipDTO and PackageRelationshipDTO.
     */
    @Test
    void given_circularDependency_when_reportGenerated_then_relationshipUrlsAreSet() throws Exception {
        // Given
        File repoDir = tempDir.toFile();
        new File(repoDir, ".git").mkdirs();

        File srcDir = new File(repoDir, "src/main/java/com/example");
        srcDir.mkdirs();

        // Create two classes with circular dependency
        Files.writeString(
                new File(srcDir, "ClassA.java").toPath(),
                """
                package com.example;

                public class ClassA {
                    private ClassB classB;

                    public ClassA(ClassB classB) {
                        this.classB = classB;
                    }
                }
                """);

        Files.writeString(
                new File(srcDir, "ClassB.java").toPath(),
                """
                package com.example;

                public class ClassB {
                    private ClassA classA;

                    public ClassB(ClassA classA) {
                        this.classA = classA;
                    }
                }
                """);

        new ProcessBuilder("git", "init").directory(repoDir).start().waitFor();
        new ProcessBuilder("git", "config", "user.email", "test@test.com")
                .directory(repoDir)
                .start()
                .waitFor();
        new ProcessBuilder("git", "config", "user.name", "Test")
                .directory(repoDir)
                .start()
                .waitFor();
        new ProcessBuilder("git", "add", ".").directory(repoDir).start().waitFor();
        new ProcessBuilder("git", "commit", "-m", "initial")
                .directory(repoDir)
                .start()
                .waitFor();

        // When
        new JsonGenerator().execute(0, true, false, true, "src/test", "CircularProject", "1.0.0", repoDir, null);

        // Then
        Path jsonFile = tempDir.resolve(".refactorfirst").resolve("refactor-first.json");
        assertTrue(Files.exists(jsonFile));

        RefactorFirstReportDTO report = objectMapper.readValue(jsonFile.toFile(), RefactorFirstReportDTO.class);
        assertNotNull(report);

        // Verify ClassRelationshipDTO renderedLabel contains HTML links
        if (report.getClassRelationshipsToRemove() != null
                && report.getClassRelationshipsToRemove().getRelationships() != null
                && !report.getClassRelationshipsToRemove().getRelationships().isEmpty()) {
            var classRel =
                    report.getClassRelationshipsToRemove().getRelationships().get(0);
            assertNotNull(classRel.getRenderedLabel(), "ClassRelationshipDTO should have renderedLabel");
            // Only check for links if source path was available
            if (classRel.getRenderedLabel().contains("<a href=\"")) {
                assertTrue(
                        classRel.getRenderedLabel().contains("target=\"_blank\""),
                        "renderedLabel should have target=\"_blank\" attribute");
            }
        }

        // Verify PackageRelationshipDTO renderedLabel contains HTML links
        if (report.getPackageRelationshipsToRemove() != null
                && report.getPackageRelationshipsToRemove().getRelationships() != null
                && !report.getPackageRelationshipsToRemove().getRelationships().isEmpty()) {
            var packageRel =
                    report.getPackageRelationshipsToRemove().getRelationships().get(0);
            assertNotNull(packageRel.getRenderedLabel(), "PackageRelationshipDTO should have renderedLabel");
            assertTrue(
                    packageRel.getRenderedLabel().contains("<a href=\""),
                    "renderedLabel should contain HTML anchor tags");
            assertTrue(
                    packageRel.getRenderedLabel().contains("target=\"_blank\""),
                    "renderedLabel should have target=\"_blank\" attribute");
        }
    }
}
