package org.hjug.refactorfirst;

import static picocli.CommandLine.Option;

import java.io.File;
import java.util.concurrent.Callable;
import org.hjug.refactorfirst.report.CsvReport;
import org.hjug.refactorfirst.report.HtmlReport;
import org.hjug.refactorfirst.report.json.JsonReportExecutor;
import picocli.CommandLine.Command;

@Command(name = "report", mixinStandardHelpOptions = true, description = "Generate a report")
public class ReportCommand implements Callable<Integer> {

    @Option(
            names = {"-d", "--details"},
            description = "Show detailed report")
    private boolean showDetails;

    @Option(
            names = {"-p", "--project"},
            defaultValue = "my-project",
            description = "Project name")
    private String projectName;

    @Option(
            names = {"-v", "--version"},
            defaultValue = "1.0.0",
            description = "Project version")
    private String projectVersion;

    @Option(
            names = {"-o", "--output"},
            defaultValue = ".",
            description = "Output directory")
    private String outputDirectory;

    @Option(
            names = {"-b", "--base-dir"},
            defaultValue = ".",
            description = "Base directory of the project")
    private File baseDir;

    @Option(
            names = {"-t", "--type"},
            description = "Report type: ${COMPLETION-CANDIDATES}",
            defaultValue = "HTML")
    private ReportType reportType;

    @Override
    public Integer call() {
        switch (reportType) {
            case HTML:
                HtmlReport htmlReport = new HtmlReport();
                htmlReport.execute(showDetails, projectName, projectVersion, outputDirectory, baseDir);
                return 0;
            case JSON:
                JsonReportExecutor jsonReportExecutor = new JsonReportExecutor();
                jsonReportExecutor.execute(baseDir, outputDirectory);
                return 0;
            case CSV:
                CsvReport csvReport = new CsvReport();
                csvReport.execute(showDetails, projectName, projectVersion, outputDirectory, baseDir);
                return 0;
        }

        return 0;
    }
}
