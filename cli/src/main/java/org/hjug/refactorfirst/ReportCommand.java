package org.hjug.refactorfirst;

import static picocli.CommandLine.Option;

import java.io.File;
import java.io.FileReader;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.hjug.refactorfirst.report.CsvReport;
import org.hjug.refactorfirst.report.HtmlReport;
import org.hjug.refactorfirst.report.SimpleHtmlReport;
import org.hjug.refactorfirst.report.json.JsonReportExecutor;
import picocli.CommandLine.Command;

@Command(mixinStandardHelpOptions = true, description = "Generate a report")
@Slf4j
public class ReportCommand implements Callable<Integer> {

    @Option(
            names = {"-d", "--details"},
            description = "Show detailed report")
    private boolean showDetails;

    @Option(
            names = {"-p", "--project"},
            description = "Project name")
    private String projectName;

    @Option(
            names = {"-v", "--version"},
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

        // TODO: add support for inferring arguments from gradle properties
        inferArgumentsFromMavenProject();
        populateDefaultArguments();
        switch (reportType) {
            case SIMPLE_HTML:
                SimpleHtmlReport simpleHtmlReport = new SimpleHtmlReport();
                simpleHtmlReport.execute(showDetails, projectName, projectVersion, outputDirectory, baseDir);
                return 0;
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

    private void populateDefaultArguments() {
        if (projectName == null || projectName.isEmpty()) {
            projectName = "my-project";
        }
        if (projectVersion == null || projectVersion.isEmpty()) {
            projectVersion = "0.0.0";
        }
    }

    private void inferArgumentsFromMavenProject() {
        if (baseDir.isDirectory()) {
            File[] potentialPomFiles = baseDir.listFiles(f -> f.getName().equals("pom.xml"));
            File pomFile = null;
            if (potentialPomFiles != null && potentialPomFiles.length > 0) {
                pomFile = potentialPomFiles[0];
            }
            if (pomFile != null) {
                Model model;
                FileReader reader;
                MavenXpp3Reader mavenreader = new MavenXpp3Reader();
                try {
                    reader = new FileReader(pomFile);
                    model = mavenreader.read(reader);
                    model.setPomFile(pomFile);
                } catch (Exception ex) {
                    log.info("Unable to infer arguments from pom file");
                    return;
                }
                MavenProject project = new MavenProject(model);

                // only override project name and version if they are not set
                if (projectName == null || projectName.isEmpty()) {
                    projectName = project.getName();
                }
                if (projectVersion == null || projectVersion.isEmpty()) {
                    projectVersion = project.getVersion();
                }
            }
        }
    }
}
