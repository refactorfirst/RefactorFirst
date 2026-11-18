package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.hjug.refactorfirst.report.json.JsonReportExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs each report type on a repository that holds only C#, through the real .NET extractor.
 * Runs only when REFACTORFIRST_CSHARP_EXTRACTOR points at a built CSharpFactExtractor.dll,
 * so the normal build needs no .NET.
 */
class CSharpOnlyReportTest {

    private static final String EXTRACTOR_ENV = "REFACTORFIRST_CSHARP_EXTRACTOR";
    private static final List<String> FIXTURE_FILES =
            List.of("TinyGreeter.sln", "TinyGreeter.csproj", "Program.cs", "Greeter.cs", "MessageStore.cs");

    private Path repo;

    @BeforeEach
    void makeCSharpOnlyRepository() throws Exception {
        String extractor = System.getenv(EXTRACTOR_ENV);
        assumeTrue(extractor != null && Files.exists(Path.of(extractor)), "extractor not configured");
        Path fixture = Path.of("..", "csharp-fact-extractor", "fixtures", "TinyGreeter")
                .toAbsolutePath()
                .normalize();
        assumeTrue(Files.isDirectory(fixture), "fixture not found");

        // Real path: the report writer refuses any symlink in the output path, and macOS temp folders sit under one.
        repo = Files.createTempDirectory(
                Path.of(System.getProperty("java.io.tmpdir")).toRealPath(), "rf-csharp-only");
        for (String file : FIXTURE_FILES) {
            Files.copy(fixture.resolve(file), repo.resolve(file));
        }
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("first").setSign(false).call();
            Thread.sleep(1000);
            Files.writeString(
                    repo.resolve("Greeter.cs"), Files.readString(repo.resolve("Greeter.cs")) + "\n// touched\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("second").setSign(false).call();
        }
    }

    @AfterEach
    void deleteRepository() throws IOException {
        if (repo == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(repo)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void givenCSharpOnlyRepository_whenSimpleHtmlReportRuns_thenTheCycleAndItsClassesAreReported() throws Exception {
        Path out = Files.createDirectories(repo.resolve("out"));

        new SimpleHtmlReport()
                .execute(50, true, false, false, true, "src/test", "tiny", "1.0", repo.toFile(), out.toString());

        String html = Files.readString(out.resolve("refactor-first-report.html"));
        assertTrue(html.contains("Class Cycles"));
        assertTrue(html.contains("Current Class Cycle Count: 1"));
        assertTrue(html.contains("Greeter"));
        assertTrue(html.contains("MessageStore"));
    }

    @Test
    void givenCSharpOnlyRepository_whenHtmlReportRuns_thenItIsWritten() throws Exception {
        Path out = Files.createDirectories(repo.resolve("out"));

        new HtmlReport()
                .execute(50, true, false, false, true, "src/test", "tiny", "1.0", repo.toFile(), out.toString());

        assertTrue(Files.readString(out.resolve("refactor-first-report.html")).contains("MessageStore"));
    }

    @Test
    void givenCSharpOnlyRepository_whenCsvAndJsonReportsRun_thenTheJavaOnlyCouplingStepDoesNotFail() throws Exception {
        Path out = Files.createDirectories(repo.resolve("out"));

        assertDoesNotThrow(() -> new CsvReport().execute(false, "tiny", "1.0", out.toString(), repo.toFile()));
        assertDoesNotThrow(() -> new JsonReportExecutor().execute(repo.toFile(), out.toString()));

        try (Stream<Path> written = Files.list(out)) {
            assertEquals(2, written.count(), "one CSV and one JSON file");
        }
    }
}
