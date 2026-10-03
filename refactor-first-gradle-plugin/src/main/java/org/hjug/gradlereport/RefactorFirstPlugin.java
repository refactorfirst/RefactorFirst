package org.hjug.gradlereport;

import java.io.File;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;

/**
 * The gradle refactor first plugin
 */
public class RefactorFirstPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Create extension to configure the plugin
        RefactorFirstExtension extension =
                project.getExtensions().create("refactorFirst", RefactorFirstExtension.class);

        // Capture configuration-cache-safe values at registration time:
        // no getProject() calls are allowed inside @TaskAction methods.
        File projectDir = project.getLayout().getProjectDirectory().getAsFile();
        Provider<Directory> defaultReportsDir =
                project.getLayout().getBuildDirectory().dir("reports/refactorfirst");
        String defaultProjectName = project.getName();

        // Register tasks
        // The project version is read lazily inside each configuration action: apply() runs while the
        // plugins {} block is being evaluated, so getVersion() would still return "unspecified" there.
        project.getTasks().register("refactorFirstHtmlReport", HtmlReportTask.class, task -> {
            task.setGroup("RefactorFirst");
            task.setDescription("Generates the RefactorFirst full HTML report (with graphs)");
            task.configureFrom(
                    extension, projectDir, defaultReportsDir, defaultProjectName, String.valueOf(project.getVersion()));
        });

        project.getTasks().register("refactorFirstSimpleHtmlReport", SimpleHtmlReportTask.class, task -> {
            task.setGroup("RefactorFirst");
            task.setDescription("Generates the RefactorFirst simplified HTML report (no heavy graphs)");
            task.configureFrom(
                    extension, projectDir, defaultReportsDir, defaultProjectName, String.valueOf(project.getVersion()));
        });

        project.getTasks().register("refactorFirstJsonReport", JsonReportTask.class, task -> {
            task.setGroup("RefactorFirst");
            task.setDescription("Generates the RefactorFirst JSON data report");
            task.configureFrom(extension, projectDir, defaultProjectName, String.valueOf(project.getVersion()));
        });

        project.getTasks().register("refactorFirstCsvReport", CsvReportTask.class, task -> {
            task.setGroup("RefactorFirst");
            task.setDescription("Generates the RefactorFirst CSV report");
            task.configureFrom(
                    extension, projectDir, defaultReportsDir, defaultProjectName, String.valueOf(project.getVersion()));
        });
    }
}
