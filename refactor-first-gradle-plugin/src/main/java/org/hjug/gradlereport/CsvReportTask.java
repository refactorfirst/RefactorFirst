package org.hjug.gradlereport;

import java.io.File;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.hjug.refactorfirst.report.CsvReport;

@CacheableTask
public abstract class CsvReportTask extends DefaultTask {
    @Internal
    public abstract Property<GradleProjectAdapter> getProjectAdapter();

    @Input
    public abstract Property<String> getProjectName();

    @Input
    public abstract Property<String> getProjectVersion();

    @Input
    public abstract Property<Boolean> getShowDetails();

    @Input
    @Optional
    public abstract Property<String> getOutputDirectory();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    public void generate() {
        System.out.println("Starting RefactorFirst CSV report generation...");

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

        System.out.println("Project name: " + projectName);
        System.out.println("Project version: " + projectVersion);

        System.out.println("Creating CsvReport instance...");
        CsvReport csvReport = new CsvReport();

        System.out.println("Executing CSV report generation...");
        csvReport.execute(getShowDetails().get(), projectName, projectVersion, outputDir.getAbsolutePath(), baseDir);

        System.out.println("CSV report generation completed.");
    }
}
