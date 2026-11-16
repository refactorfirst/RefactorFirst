package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.stream.Stream;
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
     * A reused {@link ReportModelBuilder} must start every build from a clean slate: findings
     * (and their source paths) from a prior analysis must not leak into the next report build.
     */
    @Test
    void reusedBuilderDoesNotCarryFindingsFromPriorBuildIntoNextBuild() throws Exception {
        // Given: a builder reused across two builds — the first over a repo that yields
        // disharmony findings, the second over a clean repo
        File dirtyDir = gitRepoWithKotlinDisharmonies(tempDir);
        File cleanDir = gitRepoWithSimpleSource(tempDir);

        ReportModelBuilder builder = new ReportModelBuilder();
        RefactorFirstReportDTO first = builder.build(
                false, // showDetails
                50, // edgeAnalysisCount
                true, // analyzeCycles
                true, // excludeTests
                "src/test",
                "DirtyProject",
                "1.0.0",
                dirtyDir);
        assertTrue(first.getProject().isHasAnyDisharmony());
        assertFalse(builder.getRankedDisharmoniesByAnchor().isEmpty());

        RefactorFirstReportDTO second = builder.build(
                false, // showDetails
                50, // edgeAnalysisCount
                true, // analyzeCycles
                true, // excludeTests
                "src/test",
                "CleanProject",
                "1.0.0",
                cleanDir);

        // Then: the second build does not carry the first build's findings into its result
        assertTrue(builder.getRankedDisharmoniesByAnchor().isEmpty());
        assertFalse(second.getProject().isHasAnyDisharmony());
    }

    @Test
    void reusedBuilderDoesNotCarryRankedCyclesWhenNextBuildDisablesCycleAnalysis() throws Exception {
        // Given: a builder that first analyzes a cross-package cycle
        File cyclicDir = gitRepoWithTwoPackageCycle(tempDir);
        File cleanDir = gitRepoWithSimpleSource(tempDir);
        ReportModelBuilder builder = new ReportModelBuilder();

        RefactorFirstReportDTO first = builder.build(
                false, // showDetails
                50, // edgeAnalysisCount
                true, // analyzeCycles
                true, // excludeTests
                "src/test",
                "CyclicProject",
                "1.0.0",
                cyclicDir);

        assertTrue(first.getClassCycles().isHasCycles());
        assertFalse(builder.getClassEdgeCycleCounts().isEmpty());
        assertFalse(builder.getPackageEdgeCycleCounts().isEmpty());
        assertNotNull(builder.getCodebaseGraphDTO());

        // When: the same builder analyzes an acyclic project with cycle ranking disabled
        RefactorFirstReportDTO second = builder.build(
                false, // showDetails
                50, // edgeAnalysisCount
                false, // analyzeCycles
                true, // excludeTests
                "src/test",
                "CleanProject",
                "1.0.0",
                cleanDir);

        // Then: the report and getters reflect only the current graph
        assertFalse(second.getClassCycles().isHasCycles());
        assertFalse(second.getProject().isHasAnyDisharmony());
        assertTrue(builder.getRankedClassCycles().isEmpty());
        assertNotNull(builder.getCodebaseGraphDTO());
        assertTrue(builder.getClassEdgeCycleCounts().isEmpty());
        assertTrue(builder.getPackageEdgeCycleCounts().isEmpty());
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

    private File gitRepoWithKotlinDisharmonies(Path parent) throws Exception {
        File projectDir = new File(parent.toFile(), "kotlin-disharmony-project");
        File sourceDir = new File(projectDir, "src/main/kotlin");
        Files.createDirectories(sourceDir.toPath());
        copyFixtureSources(
                new File("../codebase-graph-builder/src/test/resources/kotlinDisharmonySrcDirectory"), sourceDir);
        return gitInit(projectDir);
    }

    private File gitRepoWithSimpleSource(Path parent) throws Exception {
        File projectDir = new File(parent.toFile(), "clean-project");
        File sourceDir = new File(projectDir, "src/main/java/com/example");
        Files.createDirectories(sourceDir.toPath());
        Files.writeString(
                new File(sourceDir, "SimpleService.java").toPath(),
                """
                package com.example;

                public class SimpleService {
                    public String greet(String name) {
                        return "Hello " + name;
                    }
                }
                """);
        return gitInit(projectDir);
    }

    private File gitRepoWithTwoPackageCycle(Path parent) throws Exception {
        File projectDir = new File(parent.toFile(), "cross-package-cycle-project");
        File firstPackage = new File(projectDir, "src/main/java/com/example/first");
        File secondPackage = new File(projectDir, "src/main/java/com/example/second");
        Files.createDirectories(firstPackage.toPath());
        Files.createDirectories(secondPackage.toPath());
        Files.writeString(
                new File(firstPackage, "ClassA.java").toPath(),
                """
                package com.example.first;

                import com.example.second.ClassB;

                public class ClassA {
                    private ClassB classB;
                }
                """);
        Files.writeString(
                new File(secondPackage, "ClassB.java").toPath(),
                """
                package com.example.second;

                import com.example.first.ClassA;

                public class ClassB {
                    private ClassA classA;
                }
                """);
        return gitInit(projectDir);
    }

    private File gitInit(File dir) throws Exception {
        new ProcessBuilder("git", "init").directory(dir).start().waitFor();
        new ProcessBuilder("git", "config", "user.email", "test@test.com")
                .directory(dir)
                .start()
                .waitFor();
        new ProcessBuilder("git", "config", "user.name", "Test")
                .directory(dir)
                .start()
                .waitFor();
        new ProcessBuilder("git", "add", ".").directory(dir).start().waitFor();
        new ProcessBuilder("git", "commit", "-m", "initial")
                .directory(dir)
                .start()
                .waitFor();
        return dir;
    }

    private void copyFixtureSources(File source, File target) throws Exception {
        try (Stream<Path> stream = Files.walk(source.toPath())) {
            stream.filter(Files::isRegularFile).forEach(sourcePath -> {
                try {
                    Path relative = source.toPath().relativize(sourcePath);
                    Path targetPath = target.toPath().resolve(relative);
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourcePath, targetPath);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
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
