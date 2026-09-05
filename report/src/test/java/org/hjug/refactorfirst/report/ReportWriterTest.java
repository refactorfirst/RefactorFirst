package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportWriterTest {

    private static boolean supportsSymlinks() {
        Path probeDirectory = null;
        Path target = null;
        Path link = null;
        try {
            probeDirectory = Files.createTempDirectory("symlink-test");
            target = Files.createFile(probeDirectory.resolve("target"));
            link = probeDirectory.resolve("link");
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        } finally {
            try {
                if (link != null) {
                    Files.deleteIfExists(link);
                }
                if (target != null) {
                    Files.deleteIfExists(target);
                }
                if (probeDirectory != null) {
                    Files.deleteIfExists(probeDirectory);
                }
            } catch (IOException ignored) {
                // The probe result remains valid even if best-effort cleanup fails.
            }
        }
    }

    @Test
    void writeReportToDisk_throwsWhenOutputDirIsSymlink(@TempDir Path tempDir) throws IOException {
        assumeTrue(supportsSymlinks(), "Symbolic links not supported on this platform");

        // Create a target directory outside tempDir
        Path outsideDir = tempDir.resolve("outside_target");
        Files.createDirectories(outsideDir);

        // Create a symlink in tempDir pointing to outsideDir
        Path symlinkDir = tempDir.resolve("symlink_target");
        Files.createSymbolicLink(symlinkDir, outsideDir);

        // Verify symlink exists
        assertTrue(Files.isSymbolicLink(symlinkDir));

        // Should refuse to write and log error
        assertThrows(
                ReportWriter.ReportWriteException.class,
                () -> ReportWriter.writeReportToDisk(symlinkDir.toString(), "test.html", "<html>test</html>"));

        // The file should NOT be created in the linked directory
        Path linkedFile = outsideDir.resolve("test.html");
        assertFalse(Files.exists(linkedFile), "Should not write through symlink");
    }

    @Test
    void writeReportToDisk_throwsWhenOutputFileIsSymlink(@TempDir Path tempDir) throws IOException {
        assumeTrue(supportsSymlinks(), "Symbolic links not supported on this platform");

        // Create a target file outside tempDir
        Path outsideFile = tempDir.resolve("outside_file.html");
        Files.writeString(outsideFile, "original content");

        // Create a symlink in tempDir pointing to outsideFile
        Path symlinkFile = tempDir.resolve("report.html");
        Files.createSymbolicLink(symlinkFile, outsideFile);

        // Verify symlink exists
        assertTrue(Files.isSymbolicLink(symlinkFile));

        // Should refuse to write and log error
        assertThrows(
                ReportWriter.ReportWriteException.class,
                () -> ReportWriter.writeReportToDisk(tempDir.toString(), "report.html", "<html>new content</html>"));

        // The original file should NOT be overwritten
        String content = Files.readString(outsideFile);
        assertEquals("original content", content, "Should not overwrite through symlink");
    }

    @Test
    void writeReportToDisk_throwsWhenOutputDirIsDanglingSymlink(@TempDir Path tempDir) throws IOException {
        assumeTrue(supportsSymlinks(), "Symbolic links not supported on this platform");

        // Create a dangling symlink (target doesn't exist)
        Path danglingTarget = tempDir.resolve("nonexistent_dir");
        Path symlinkDir = tempDir.resolve("dangling_link");
        Files.createSymbolicLink(symlinkDir, danglingTarget);

        // Verify it's a symlink but target doesn't exist
        assertTrue(Files.isSymbolicLink(symlinkDir));
        assertFalse(Files.exists(danglingTarget));

        // Should refuse to write and log error
        assertThrows(
                ReportWriter.ReportWriteException.class,
                () -> ReportWriter.writeReportToDisk(symlinkDir.toString(), "test.html", "<html>test</html>"));

        // The target directory should not be created
        assertFalse(Files.exists(danglingTarget), "Should not create target of dangling symlink");
    }

    @Test
    void writeReportToDisk_throwsWhenOutputFileIsDanglingSymlink(@TempDir Path tempDir) throws IOException {
        assumeTrue(supportsSymlinks(), "Symbolic links not supported on this platform");

        // Create a dangling symlink for the file
        Path danglingTarget = tempDir.resolve("nonexistent_file.html");
        Path symlinkFile = tempDir.resolve("report.html");
        Files.createSymbolicLink(symlinkFile, danglingTarget);

        // Verify it's a symlink but target doesn't exist
        assertTrue(Files.isSymbolicLink(symlinkFile));
        assertFalse(Files.exists(danglingTarget));

        // Should refuse to write and log error
        assertThrows(
                ReportWriter.ReportWriteException.class,
                () -> ReportWriter.writeReportToDisk(tempDir.toString(), "report.html", "<html>test</html>"));

        // The target file should not be created
        assertFalse(Files.exists(danglingTarget), "Should not create target of dangling symlink");
    }

    @Test
    void writeReportToDisk_writesNormallyWhenNoSymlinks(@TempDir Path tempDir) throws IOException {
        // Normal case - no symlinks
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        ReportWriter.writeReportToDisk(outputDir.toString(), "test.html", "<html>test content</html>");

        // File should be created with correct content
        Path outputFile = outputDir.resolve("test.html");
        assertTrue(Files.exists(outputFile));
        String content = Files.readString(outputFile);
        assertEquals("<html>test content</html>", content);
    }

    @Test
    void writeReportToDisk_atomicallyReplacesExistingReport(@TempDir Path tempDir) throws IOException {
        ReportWriter.writeReportToDisk(tempDir.toString(), "report.html", "old");

        ReportWriter.writeReportToDisk(tempDir.toString(), "report.html", "new");

        assertEquals("new", Files.readString(tempDir.resolve("report.html")));
    }

    @Test
    void writeReportToDisk_createsParentDirectories(@TempDir Path tempDir) throws IOException {
        // Nested directory that doesn't exist yet
        Path outputDir = tempDir.resolve("nested").resolve("output");

        ReportWriter.writeReportToDisk(outputDir.toString(), "test.html", "<html>test</html>");

        // Parent directories should be created
        Path outputFile = outputDir.resolve("test.html");
        assertTrue(Files.exists(outputFile));
        String content = Files.readString(outputFile);
        assertEquals("<html>test</html>", content);
    }

    @Test
    void writeReportToDisk_rejectsNestedSymlinkedAncestor(@TempDir Path tempDir) throws IOException {
        assumeTrue(supportsSymlinks(), "Symbolic links not supported on this platform");
        Path target = Files.createDirectory(tempDir.resolve("target"));
        Path nested = Files.createDirectories(tempDir.resolve("safe/nested"));
        Path linkedAncestor = nested.resolve("linked");
        Files.createSymbolicLink(linkedAncestor, target);

        assertThrows(
                ReportWriter.ReportWriteException.class,
                () -> ReportWriter.writeReportToDisk(
                        linkedAncestor.resolve("nested").toString(), "report.html", "blocked"));
        assertFalse(Files.exists(target.resolve("nested/report.html")));
    }

    @Test
    void writeReportToDisk_doesNotFollowPathComponentReplacedDuringWrite(@TempDir Path tempDir) throws Exception {
        assumeTrue(supportsSymlinks(), "Symbolic links not supported on this platform");
        Path output = Files.createDirectory(tempDir.resolve("output"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path originalOutput = tempDir.resolve("original-output");
        String content = "x".repeat(32 * 1024 * 1024);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (WatchService watcher = output.getFileSystem().newWatchService()) {
            output.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
            Future<?> write =
                    executor.submit(() -> ReportWriter.writeReportToDisk(output.toString(), "report.html", content));

            WatchKey key = watcher.poll(10, TimeUnit.SECONDS);
            assertNotNull(key, "Expected the secure temporary report to be created");
            assertTrue(
                    key.pollEvents().stream()
                            .anyMatch(event -> event.context().toString().endsWith(".tmp")),
                    "Expected a temporary report-file creation event");

            Files.move(output, originalOutput);
            Files.createSymbolicLink(output, outside);
            write.get(10, TimeUnit.SECONDS);

            assertFalse(Files.exists(outside.resolve("report.html")), "Should not follow the replacement link");
            assertEquals(content.length(), Files.size(originalOutput.resolve("report.html")));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void containReportDirectory_allowsNormalTargetSite(@TempDir Path tempDir) throws IOException {
        File baseDir = tempDir.toFile();
        String result = ReportWriter.containReportDirectory(baseDir, "target/site");
        assertEquals(tempDir.resolve("target/site").normalize().toString(), result);
    }

    @Test
    void containReportDirectory_rejectsAbsolutePath(@TempDir Path tempDir) throws IOException {
        File baseDir = tempDir.toFile();
        String absolutePath = "/absolute/path/outside";
        assertThrows(IllegalArgumentException.class, () -> ReportWriter.containReportDirectory(baseDir, absolutePath));
    }

    @Test
    void containReportDirectory_rejectsTraversalPath(@TempDir Path tempDir) throws IOException {
        File baseDir = tempDir.toFile();
        String traversalPath = "../../../etc";
        assertThrows(IllegalArgumentException.class, () -> ReportWriter.containReportDirectory(baseDir, traversalPath));
    }

    @Test
    void containReportDirectory_rejectsEmptyValue_usesDefault(@TempDir Path tempDir) throws IOException {
        File baseDir = tempDir.toFile();
        String result = ReportWriter.containReportDirectory(baseDir, "");
        assertEquals(tempDir.resolve("target/site").normalize().toString(), result);
    }

    @Test
    void containReportDirectory_rejectsNullValue_usesDefault(@TempDir Path tempDir) throws IOException {
        File baseDir = tempDir.toFile();
        String result = ReportWriter.containReportDirectory(baseDir, null);
        assertEquals(tempDir.resolve("target/site").normalize().toString(), result);
    }

    @Test
    void containReportDirectory_usesBaseDirAsRoot(@TempDir Path tempDir) throws IOException {
        File baseDir = tempDir.resolve("project").toFile();
        String result = ReportWriter.containReportDirectory(baseDir, "target/site");
        assertEquals(baseDir.toPath().resolve("target/site").normalize().toString(), result);
    }

    @Test
    void containReportDirectory_rejectsSymlinkedAncestor(@TempDir Path tempDir) throws IOException {
        assumeTrue(supportsSymlinks(), "Symbolic links not supported on this platform");
        Files.createDirectory(tempDir.resolve("outside"));
        Files.createSymbolicLink(tempDir.resolve("linked"), tempDir.resolve("outside"));

        assertThrows(
                IllegalArgumentException.class,
                () -> ReportWriter.containReportDirectory(tempDir.toFile(), "linked/site"));
    }

    @Test
    void writeReportToDisk_emitsUtf8ForNonAsciiContent(@TempDir Path tempDir) throws IOException {
        String nonAsciiContent = "café 漢字 😀";
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        ReportWriter.writeReportToDisk(outputDir.toString(), "test.html", nonAsciiContent);

        Path outputFile = outputDir.resolve("test.html");
        assertTrue(Files.exists(outputFile));

        byte[] writtenBytes = Files.readAllBytes(outputFile);
        byte[] expectedBytes = nonAsciiContent.getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expectedBytes, writtenBytes, "ReportWriter must emit UTF-8 bytes");
    }
}
