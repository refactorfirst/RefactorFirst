package org.hjug.mavenreport;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.project.MavenProject;

@Slf4j
public class ReportWriter {
    public static void writeReportToDisk(
            final MavenProject project, final String filename, final StringBuilder stringBuilder) {
        final String reportOutputDirectory = project.getModel()
                .getReporting()
                .getOutputDirectory()
                .replace("${project.basedir}" + File.separator, "");
        final File reportOutputDir = new File(reportOutputDirectory);

        if (!reportOutputDir.exists()) {
            reportOutputDir.mkdirs();
        }

        final String pathname = reportOutputDirectory + File.separator + filename;

        final File reportFile = new File(pathname);

        try {
            reportFile.createNewFile();
        } catch (IOException e) {
            log.error("Failure creating chart script file", e);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(reportFile.toPath(), Charset.defaultCharset())) {
            writer.write(stringBuilder.toString());
        } catch (IOException e) {
            log.error("Error writing chart script file", e);
        }

        log.info("Done! View the report at target/site/{}", filename);
    }
}
