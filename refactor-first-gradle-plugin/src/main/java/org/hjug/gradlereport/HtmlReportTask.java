package org.hjug.gradlereport;

import java.io.File;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.hjug.refactorfirst.report.HtmlReport;

public abstract class HtmlReportTask extends DefaultTask {
    @Internal
    public abstract Property<GradleProjectAdapter> getProjectAdapter();

    @Input
    public abstract Property<String> getProjectName();

    @Input
    public abstract Property<String> getProjectVersion();

    @Input
    public abstract Property<Integer> getBackEdgeAnalysisCount();

    @Input
    public abstract Property<Boolean> getAnalyzeCycles();

    @Input
    public abstract Property<Boolean> getShowDetails();

    @Input
    public abstract Property<Boolean> getMinifyHtml();

    @Input
    public abstract Property<Boolean> getExcludeTests();

    @Input
    @Optional
    public abstract Property<String> getTestSourceDirectory();

    @Input
    @Optional
    public abstract Property<String> getOutputDirectory();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void generate() {
        System.out.println("Starting RefactorFirst HTML report generation...");

        GradleProjectAdapter adapter = getProjectAdapter().get();
        File baseDir = adapter.getProjectBaseDir();
        File buildDir = new File(baseDir, "build");
        File outputDir = getOutputDirectory().isPresent()
                ? new File(getOutputDirectory().get())
                : new File(buildDir, "reports/refactor-first");

        System.out.println("Base directory: " + baseDir.getAbsolutePath());
        System.out.println("Output directory: " + outputDir.getAbsolutePath());

        String projectName = getProjectName().getOrElse(adapter.getProjectName());
        String projectVersion = getProjectVersion().getOrElse(adapter.getProjectVersion());
        String testSourceDirectory =
                getTestSourceDirectory().isPresent() ? getTestSourceDirectory().get() : null;

        System.out.println("Project name: " + projectName);
        System.out.println("Project version: " + projectVersion);

        int backEdgeAnalysisCount = getBackEdgeAnalysisCount().get();
        if (backEdgeAnalysisCount < 0) {
            throw new RefactorFirstPluginException("backEdgeAnalysisCount must be >= 0, got: " + backEdgeAnalysisCount);
        }

        System.out.println("Creating HtmlReport instance...");
        HtmlReport htmlReport = new HtmlReport();

        System.out.println("Executing report generation with backEdgeAnalysisCount=" + backEdgeAnalysisCount);
        htmlReport.execute(
                backEdgeAnalysisCount,
                getAnalyzeCycles().get(),
                getShowDetails().get(),
                getMinifyHtml().get(),
                getExcludeTests().get(),
                testSourceDirectory,
                projectName,
                projectVersion,
                baseDir,
                outputDir.getAbsolutePath());

        System.out.println("Report generation completed.");
    }
}
