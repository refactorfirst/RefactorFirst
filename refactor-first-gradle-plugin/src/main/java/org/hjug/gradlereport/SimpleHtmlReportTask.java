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
import org.hjug.refactorfirst.report.SimpleHtmlReport;

public abstract class SimpleHtmlReportTask extends DefaultTask {
    /** Returns the project information used while generating the report. */
    @Internal
    public abstract Property<GradleProjectAdapter> getProjectAdapter();

    /** Returns the project name included in the report. */
    @Input
    public abstract Property<String> getProjectName();

    /** Returns the project version included in the report. */
    @Input
    public abstract Property<String> getProjectVersion();

    /** Returns the maximum number of cycle back edges to analyze. */
    @Input
    public abstract Property<Integer> getBackEdgeAnalysisCount();

    /** Returns whether cycle analysis is enabled. */
    @Input
    public abstract Property<Boolean> getAnalyzeCycles();

    /** Returns whether detailed findings should be included. */
    @Input
    public abstract Property<Boolean> getShowDetails();

    /** Returns whether the generated HTML should be minified. */
    @Input
    public abstract Property<Boolean> getMinifyHtml();

    /** Returns whether test sources should be excluded from analysis. */
    @Input
    public abstract Property<Boolean> getExcludeTests();

    /** Returns the optional test source directory. */
    @Input
    @Optional
    public abstract Property<String> getTestSourceDirectory();

    /** Returns the optional report output directory. */
    @Input
    @Optional
    public abstract Property<String> getOutputDirectory();

    /** Returns the file produced by this task. */
    @OutputFile
    public abstract RegularFileProperty getReportFile();

    /** Generates the simplified HTML report using the configured task inputs. */
    @TaskAction
    public void generate() {
        System.out.println("Starting RefactorFirst Simple HTML report generation...");

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

        System.out.println("Creating SimpleHtmlReport instance...");
        SimpleHtmlReport htmlReport = new SimpleHtmlReport();

        System.out.println("Executing simple report generation with backEdgeAnalysisCount=" + backEdgeAnalysisCount);
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

        System.out.println("Simple report generation completed.");
    }
}
