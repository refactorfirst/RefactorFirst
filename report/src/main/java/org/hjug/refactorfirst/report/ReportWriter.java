package org.hjug.refactorfirst.report;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ReportWriter {

    /**
     * Resolves a configured report output directory against the project base directory
     * and refuses any value that escapes it. The value originates from the *analyzed*
     * project's POM (<reporting><outputDirectory>), which is untrusted input whenever
     * the tool runs against a third-party repository: absolute paths, ".." segments
     * and empty values must never redirect report writes outside the analyzed project.
     */
    public static String containReportDirectory(final File baseDir, final String configuredDir) {
        final Path base = (baseDir != null ? baseDir.toPath() : Path.of(""))
                .toAbsolutePath()
                .normalize();
        final String configured =
                configuredDir == null || configuredDir.isBlank() ? "target" + File.separator + "site" : configuredDir;
        final Path resolved = base.resolve(configured).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException(
                    "Report output directory escapes the project base directory: " + configuredDir);
        }
        return resolved.toString();
    }

    public static void writeReportToDisk(
            final String reportOutputDirectory, final String filename, final String string) {
        final File reportOutputDir = new File(reportOutputDirectory);

        // CWE-59: never let a pre-existing symbolic link relocate the report write.
        // Files.isSymbolicLink() examines the link itself (lstat semantics), so it also
        // covers dangling links (the creation-through-link variant).
        if (Files.isSymbolicLink(reportOutputDir.toPath())) {
            log.error("Refusing to write report: output directory is a symbolic link: {}", reportOutputDirectory);
            return;
        }

        if (!reportOutputDir.exists()) {
            reportOutputDir.mkdirs();
        }

        final String pathname = reportOutputDirectory + File.separator + filename;

        final File reportFile = new File(pathname);
        if (Files.isSymbolicLink(reportFile.toPath())) {
            log.error("Refusing to write report: output path is a symbolic link: {}", pathname);
            return;
        }

        try {
            reportFile.createNewFile();
        } catch (IOException e) {
            log.error("Failure creating chart script file", e);
        }

        try (BufferedWriter writer =
                Files.newBufferedWriter(reportFile.toPath(), Charset.defaultCharset(), LinkOption.NOFOLLOW_LINKS)) {
            writer.write(string);
        } catch (IOException e) {
            log.error("Error writing chart script file", e);
        }

        log.info("Done! View the report at target/site/{}", filename);
    }

    private ReportWriter() {}
}
