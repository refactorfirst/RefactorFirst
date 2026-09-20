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
import org.hjug.refactorfirst.report.json.JsonReportExecutor;

@CacheableTask
public abstract class JsonReportTask extends DefaultTask {
    /** Returns the project information used while generating the report. */
    @Internal
    public abstract Property<GradleProjectAdapter> getProjectAdapter();

    /** Returns the optional report output directory. */
    @Input
    @Optional
    public abstract Property<String> getOutputDirectory();

    /** Returns the file produced by this task. */
    @OutputFile
    public abstract RegularFileProperty getReportFile();

    /** Generates the JSON report using the configured task inputs. */
    @TaskAction
    public void generate() {
        System.out.println("Starting RefactorFirst JSON report generation...");

        GradleProjectAdapter adapter = getProjectAdapter().get();
        File baseDir = adapter.getProjectBaseDir();
        File buildDir = new File(baseDir, "build");
        File outputDir = getOutputDirectory().isPresent()
                ? new File(getOutputDirectory().get())
                : new File(buildDir, "reports/refactor-first");

        System.out.println("Base directory: " + baseDir.getAbsolutePath());
        System.out.println("Output directory: " + outputDir.getAbsolutePath());

        System.out.println("Creating JsonReportExecutor instance...");
        JsonReportExecutor jsonReportExecutor = new JsonReportExecutor();

        System.out.println("Executing JSON report generation...");
        jsonReportExecutor.execute(baseDir, outputDir.getAbsolutePath());

        System.out.println("JSON report generation completed.");
    }
}
