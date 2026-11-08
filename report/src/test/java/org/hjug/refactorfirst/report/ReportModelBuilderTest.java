package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase P1/T1.1 red test for the {@link ReportModelBuilder} extraction.
 *
 * <p>Asserts that the builder-produced {@link RefactorFirstReportDTO} is identical to the DTO
 * {@link JsonGenerator} produces today for the same fixture project, serialized to JSON and
 * compared — the byte-compatible golden the plan's exit criteria demand ("zero output changes").
 */
class ReportModelBuilderTest {

    private Path tempDir;
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("reportModelBuilderTest");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    void builtDtoEqualsGeneratorDtoForSameFixtureProject() throws Exception {
        // Given: a fixed in-memory fixture project with a two-class cycle (mirrors the
        // JsonGeneratorTest circular-dependency fixture).
        File repoDir = tempDir.toFile();
        File gitDir = new File(repoDir, ".git");
        gitDir.mkdirs();
        File srcDir = new File(repoDir, "src/main/java/com/example");
        srcDir.mkdirs();

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

        Clock clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneId.of("UTC"));

        // When: the builder builds the report model...
        RefactorFirstReportDTO built = new ReportModelBuilder()
                .build(
                        false, // showDetails
                        50, // edgeAnalysisCount
                        true, // analyzeCycles
                        true, // excludeTests
                        "src/test",
                        "ParityProject",
                        "1.0.0",
                        repoDir,
                        clock);

        // Then: it serializes identically to what the generator produces today.
        RefactorFirstReportDTO generated = new JsonGenerator()
                .withClock(clock)
                .generateReportData(false, 50, true, true, "src/test", "ParityProject", "1.0.0", repoDir);

        String builtJson = objectMapper.writeValueAsString(built);
        String generatedJson = objectMapper.writeValueAsString(generated);

        assertNotNull(builtJson);
        assertNotNull(generatedJson);
        assertEquals(generatedJson, builtJson);
    }

    /**
     * A multi-module layout (module directory != Git repo root) must build a full report —
     * the model builder passes both paths to CycleRanker for URL canonicalization instead of
     * returning early. Migrated from SimpleHtmlReportTest.generateReport_multiModuleProject_
     * doesNotReturnEarly when the string-building renderer was deleted (P4/§10.3).
     */
    @Test
    void buildsMultiModuleProjectWithoutEarlyReturn() throws Exception {
        // Given: a parent repo and a submodule with a Kotlin source file
        File tempDir = File.createTempFile("multiModuleTest", "");
        tempDir.delete();
        tempDir.mkdirs();
        File submoduleDir = new File(tempDir, "submodule");
        submoduleDir.mkdirs();

        File srcDir = new File(submoduleDir, "src/main/kotlin/com/example");
        srcDir.mkdirs();
        File kotlinFile = new File(srcDir, "TestClass.kt");
        Files.writeString(
                kotlinFile.toPath(),
                """
            package com.example

            class TestClass {
                fun doSomething() = "test"
            }
            """);

        ProcessBuilder pb = new ProcessBuilder("git", "init");
        pb.directory(tempDir);
        pb.start().waitFor();
        pb = new ProcessBuilder("git", "config", "user.email", "test@test.com");
        pb.directory(tempDir);
        pb.start().waitFor();
        pb = new ProcessBuilder("git", "config", "user.name", "Test");
        pb.directory(tempDir);
        pb.start().waitFor();
        pb = new ProcessBuilder("git", "add", ".");
        pb.directory(tempDir);
        pb.start().waitFor();
        pb = new ProcessBuilder("git", "commit", "-m", "initial");
        pb.directory(tempDir);
        pb.start().waitFor();

        // When: the builder builds the model with baseDir = submodule, git root = parent
        RefactorFirstReportDTO reportDTO =
                new ReportModelBuilder().build(false, 50, true, false, "src/test", "TestProject", "1.0", submoduleDir);

        // Then: the analysis did not fail and carries real findings structure
        assertNotNull(reportDTO.getProject(), "the multi-module build must produce a project");
        assertFalse(reportDTO.getProject().isAnalysisFailed(), "must not return early for multi-module projects");

        deleteDir(tempDir);
    }

    private void deleteDir(File dir) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                deleteDir(file);
            } else {
                file.delete();
            }
        }
        dir.delete();
    }
}
