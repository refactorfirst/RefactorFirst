package org.hjug.gradlereport;

import java.io.File;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * The gradle refactor first plugin
 */
public class RefactorFirstPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Create extension to configure the plugin
        project.getExtensions().create("refactorFirst", RefactorFirstExtension.class);

        // Register tasks
        project.getTasks().register("refactorFirstHtmlReport", HtmlReportTask.class, task -> {
            task.setGroup("RefactorFirst");
            task.setDescription("Generates the RefactorFirst full HTML report (with graphs)");
        });

        project.getTasks().register("refactorFirstSimpleHtmlReport", SimpleHtmlReportTask.class, task -> {
            task.setGroup("RefactorFirst");
            task.setDescription("Generates the RefactorFirst simplified HTML report (no heavy graphs)");
        });

        project.getTasks().register("refactorFirstJsonReport", JsonReportTask.class, task -> {
            task.setGroup("RefactorFirst");
            task.setDescription("Generates the RefactorFirst JSON data report");
        });

        project.getTasks().register("refactorFirstCsvReport", CsvReportTask.class, task -> {
            task.setGroup("RefactorFirst");
            task.setDescription("Generates the RefactorFirst CSV report");
        });
    }

    public static String relativizeToProject(File baseDir, File outputDir) {
        // Maven plugin passes a path relative to project base dir
        String basePath = baseDir.getAbsolutePath();
        String outPath = outputDir.getAbsolutePath();
        if (outPath.startsWith(basePath)) {
            String rel = outPath.substring(basePath.length());
            if (rel.startsWith(File.separator)) {
                rel = rel.substring(1);
            }
            return rel;
        }
        // Fallback to default
        return "target/site";
    }
}