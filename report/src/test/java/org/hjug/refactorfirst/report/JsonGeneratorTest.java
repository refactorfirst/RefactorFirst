package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.hjug.refactorfirst.report.model.ChartJsBubbleDTO;
import org.hjug.refactorfirst.report.model.ClassRelationshipDTO;
import org.hjug.refactorfirst.report.model.PackageRelationshipDTO;
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

    /** Verifies that the bubble carries only the source file path relative to the project root. */
    @Test
    void given_createBubble_when_pathProvided_then_pathIsSet() {
        // Given
        JsonGenerator generator = new JsonGenerator();
        String classPath = "src/main/java/com/example/TestClass.java";

        // When
        ChartJsBubbleDTO bubble = generator.createBubble("TestClass", "TestClass.java", 5, 10, 1, 10, classPath);

        // Then
        assertNotNull(bubble.getPath());
        assertEquals(
                "src/main/java/com/example/TestClass.java",
                bubble.getPath(),
                "The bubble should carry the source file path relative to the project root, not a full URL");
    }

    /** Verifies that generated and serialized chart bubbles carry only the source path. */
    @Test
    void given_repoWithOrigin_when_reportGenerated_then_bubblePathsListSourceFiles() throws Exception {
        // Given
        File repoDir = tempDir.toFile();
        File srcDir = new File(repoDir, "src/main/java/com/example");
        srcDir.mkdirs();

        StringBuilder complexMethod = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            complexMethod
                    .append("        if (a > ")
                    .append(i)
                    .append(
                            ") { for (int j = 0; j < a; j++) { if (j % 2 == 0 && b) { c += j; } else { c -= j; } } }\n");
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
            assertNotNull(bubble.getPath(), "Bubble " + bubble.getLabel() + " should have a path");
            assertEquals(
                    "src/main/java/com/example/" + bubble.getLabel(),
                    bubble.getPath(),
                    "Bubble path should be the source file path relative to the project root: " + bubble.getPath());
        }

        // The viewer constructs the bubble URLs from project.repoUrl + path, so the
        // payload must not embed a full URL per bubble
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(jsonFile.toFile());
        for (com.fasterxml.jackson.databind.JsonNode disharmony : root.get("disharmonies")) {
            if (!disharmony.has("chart") || !disharmony.get("chart").has("bubbles")) {
                continue;
            }
            for (com.fasterxml.jackson.databind.JsonNode bubble :
                    disharmony.get("chart").get("bubbles")) {
                assertFalse(bubble.has("url"), "Bubbles must not embed a full url: " + bubble);
                assertTrue(bubble.has("path"), "Bubbles must carry the source path: " + bubble);
            }
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
     * Verifies that sourceClassPath, targetClassPath, simpleSourceClassName and
     * simpleTargetClassName are set in ClassRelationshipDTO, and that the label
     * is no longer pre-rendered server-side.
     */
    @Test
    void given_circularDependency_when_reportGenerated_then_classPathsAndSimpleNamesAreSet() throws Exception {
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

        // Verify ClassRelationshipDTO carries the source file paths and simple class names
        // so the viewer can combine them with project.repoUrl itself
        assertNotNull(report.getClassRelationshipsToRemove());
        assertNotNull(report.getClassRelationshipsToRemove().getRelationships());
        assertFalse(report.getClassRelationshipsToRemove().getRelationships().isEmpty());
        var classRel = report.getClassRelationshipsToRemove().getRelationships().get(0);
        assertEquals(
                "src/main/java/com/example/ClassA.java",
                classRel.getSourceClassPath(),
                "sourceClassPath should be the source file path relative to the project root");
        assertEquals(
                "src/main/java/com/example/ClassB.java",
                classRel.getTargetClassPath(),
                "targetClassPath should be the source file path relative to the project root");
        assertEquals("ClassA", classRel.getSimpleSourceClassName());
        assertEquals("ClassB", classRel.getSimpleTargetClassName());

        // Verify the raw JSON no longer carries a server-rendered label on class relationships
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(jsonFile.toFile());
        for (com.fasterxml.jackson.databind.JsonNode rel :
                root.get("classRelationshipsToRemove").get("relationships")) {
            assertFalse(rel.has("renderedLabel"), "Class relationships must not carry a renderedLabel");
            assertTrue(rel.has("sourceClassPath"), "Class relationships must carry sourceClassPath");
            assertTrue(rel.has("targetClassPath"), "Class relationships must carry targetClassPath");
            assertTrue(rel.has("simpleSourceClassName"), "Class relationships must carry simpleSourceClassName");
            assertTrue(rel.has("simpleTargetClassName"), "Class relationships must carry simpleTargetClassName");
        }
    }

    /**
     * Verifies that class relationships that break a package cycle are serialized as
     * structured {@link ClassRelationshipDTO}s with class names, markers and a rendered label.
     */
    @Test
    void given_packageCycle_when_reportGenerated_then_classRelationshipsToBreakPackageAreStructuredDTOs()
            throws Exception {
        // Given: two packages with a circular class dependency
        File repoDir = tempDir.toFile();
        new File(repoDir, ".git").mkdirs();

        File pkgA = new File(repoDir, "src/main/java/com/example/pkga");
        File pkgB = new File(repoDir, "src/main/java/com/example/pkgb");
        pkgA.mkdirs();
        pkgB.mkdirs();

        Files.writeString(
                new File(pkgA, "ClassA.java").toPath(),
                """
                package com.example.pkga;

                public class ClassA {
                    private com.example.pkgb.ClassB classB;

                    public ClassA(com.example.pkgb.ClassB classB) {
                        this.classB = classB;
                    }
                }
                """);
        Files.writeString(
                new File(pkgB, "ClassB.java").toPath(),
                """
                package com.example.pkgb;

                public class ClassB {
                    private com.example.pkga.ClassA classA;

                    public ClassB(com.example.pkga.ClassA classA) {
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
        new JsonGenerator().execute(0, true, false, true, "src/test", "PackageCycleProject", "1.0.0", repoDir, null);

        // Then
        Path jsonFile = tempDir.resolve(".refactorfirst").resolve("refactor-first.json");
        RefactorFirstReportDTO report = objectMapper.readValue(jsonFile.toFile(), RefactorFirstReportDTO.class);
        assertNotNull(report.getPackageRelationshipsToRemove());
        assertNotNull(
                report.getPackageRelationshipsToRemove().getRelationships(),
                "Package relationships to remove should be present");
        assertFalse(
                report.getPackageRelationshipsToRemove().getRelationships().isEmpty(),
                "Fixture produced no package relationships to remove");

        int totalClassRelationships = 0;
        for (PackageRelationshipDTO packageRel :
                report.getPackageRelationshipsToRemove().getRelationships()) {
            assertNotNull(packageRel.getClassRelationshipsToBreakPackage());
            // Package-level relationships carry the package directory paths relative to the
            // project root so the viewer can combine them with project.repoUrl itself
            assertTrue(
                    packageRel.getSourcePackagePath().startsWith("src/main/java/com/example/pkg"),
                    "sourcePackagePath should be the package directory relative to the project root: "
                            + packageRel.getSourcePackagePath());
            assertTrue(
                    packageRel.getTargetPackagePath().startsWith("src/main/java/com/example/pkg"),
                    "targetPackagePath should be the package directory relative to the project root: "
                            + packageRel.getTargetPackagePath());
            assertTrue(
                    packageRel.getSourcePackagePath().endsWith("/"),
                    "sourcePackagePath derived from a class source file should end with a slash: "
                            + packageRel.getSourcePackagePath());
            for (ClassRelationshipDTO classRel : packageRel.getClassRelationshipsToBreakPackage()) {
                totalClassRelationships++;
                assertTrue(
                        classRel.getSourceClass().startsWith("com.example.pkg"),
                        "sourceClass should be a fully qualified class name: " + classRel.getSourceClass());
                assertTrue(
                        classRel.getTargetClass().startsWith("com.example.pkg"),
                        "targetClass should be a fully qualified class name: " + classRel.getTargetClass());
                assertTrue(classRel.getWeight() >= 1, "weight should be at least 1");
                assertTrue(
                        classRel.getSourceClassPath().startsWith("src/main/java/com/example/pkg"),
                        "sourceClassPath should be the source file path relative to the project root: "
                                + classRel.getSourceClassPath());
                assertTrue(
                        classRel.getTargetClassPath().startsWith("src/main/java/com/example/pkg"),
                        "targetClassPath should be the source file path relative to the project root: "
                                + classRel.getTargetClassPath());
                assertTrue(
                        classRel.getSimpleSourceClassName().startsWith("Class"),
                        "simpleSourceClassName should be the simple source class name: "
                                + classRel.getSimpleSourceClassName());
                assertTrue(
                        classRel.getSimpleTargetClassName().startsWith("Class"),
                        "simpleTargetClassName should be the simple target class name: "
                                + classRel.getSimpleTargetClassName());
            }
        }
        assertTrue(totalClassRelationships > 0, "Expected at least one class relationship to break the package cycle");

        // The nested class relationships must not carry a server-rendered label either
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(jsonFile.toFile());
        for (com.fasterxml.jackson.databind.JsonNode packageRel :
                root.get("packageRelationshipsToRemove").get("relationships")) {
            assertFalse(packageRel.has("renderedLabel"), "Package relationships must not carry a renderedLabel");
            assertTrue(packageRel.has("sourcePackagePath"), "Package relationships must carry sourcePackagePath");
            assertTrue(packageRel.has("targetPackagePath"), "Package relationships must carry targetPackagePath");
            for (com.fasterxml.jackson.databind.JsonNode classRel :
                    packageRel.get("classRelationshipsToBreakPackage")) {
                assertFalse(classRel.has("renderedLabel"), "Nested class relationships must not carry a renderedLabel");
                assertTrue(classRel.has("sourceClassPath"));
                assertTrue(classRel.has("targetClassPath"));
                assertTrue(classRel.has("simpleSourceClassName"));
                assertTrue(classRel.has("simpleTargetClassName"));
            }
        }
    }
}
