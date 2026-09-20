package org.hjug.gradlereport;

import java.io.File;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class RefactorFirstPlugin implements Plugin<Project> {
    /** Registers the RefactorFirst extension and report tasks. */
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java");

        RefactorFirstExtension extension =
                project.getExtensions().create("refactorFirst", RefactorFirstExtension.class);
        GradleProjectAdapter projectAdapter = new GradleProjectAdapter(project, extension);

        project.getTasks().register("refactorFirstHtmlReport", HtmlReportTask.class, task -> {
            task.setGroup("RefactorFirst");
            task.setDescription("Generates the RefactorFirst HTML report");
            task.getProjectAdapter().set(projectAdapter);
            task.getProjectName().set(project.getName());
            task.getProjectVersion().set(project.getVersion().toString());
            task.getBackEdgeAnalysisCount().set(extension.getBackEdgeAnalysisCount());
            task.getAnalyzeCycles().set(extension.getAnalyzeCycles());
            task.getShowDetails().set(extension.getShowDetails());
            task.getMinifyHtml().set(extension.getMinifyHtml());
            task.getExcludeTests().set(extension.getExcludeTests());
            if (extension.getTestSourceDirectory().isPresent()) {
                task.getTestSourceDirectory()
                        .set(extension.getTestSourceDirectory().get().getAbsolutePath());
            }
            if (extension.getOutputDirectory().isPresent()) {
                task.getOutputDirectory()
                        .set(extension.getOutputDirectory().get().getAbsolutePath());
            }
            task.getReportFile()
                    .set(project.getLayout()
                            .getBuildDirectory()
                            .file("reports/refactor-first/refactor-first-report.html"));
        });

        project.getTasks().register("refactorFirstCsvReport", CsvReportTask.class, task -> {
            task.setGroup("RefactorFirst");
            task.setDescription("Generates the RefactorFirst CSV report");
            task.getProjectAdapter().set(projectAdapter);
            task.getProjectName().set(project.getName());
            task.getProjectVersion().set(project.getVersion().toString());
            task.getShowDetails().set(extension.getShowDetails());
            if (extension.getOutputDirectory().isPresent()) {
                task.getOutputDirectory()
                        .set(extension.getOutputDirectory().get().getAbsolutePath());
            }
            task.getReportFile()
                    .set(project.getLayout()
                            .getBuildDirectory()
                            .file("reports/refactor-first/refactor-first-report.csv"));
        });

        project.getTasks().register("refactorFirstJsonReport", JsonReportTask.class, task -> {
            task.setGroup("RefactorFirst");
            task.setDescription("Generates the RefactorFirst JSON report");
            task.getProjectAdapter().set(projectAdapter);
            if (extension.getOutputDirectory().isPresent()) {
                task.getOutputDirectory()
                        .set(extension.getOutputDirectory().get().getAbsolutePath());
            }
            task.getReportFile()
                    .set(project.getLayout()
                            .getBuildDirectory()
                            .file("reports/refactor-first/refactor-first-data.json"));
        });

        project.getTasks().register("refactorFirstSimpleHtmlReport", SimpleHtmlReportTask.class, task -> {
            task.setGroup("RefactorFirst");
            task.setDescription("Generates the RefactorFirst simplified HTML report");
            task.getProjectAdapter().set(projectAdapter);
            task.getProjectName().set(project.getName());
            task.getProjectVersion().set(project.getVersion().toString());
            task.getBackEdgeAnalysisCount().set(extension.getBackEdgeAnalysisCount());
            task.getAnalyzeCycles().set(extension.getAnalyzeCycles());
            task.getShowDetails().set(extension.getShowDetails());
            task.getMinifyHtml().set(extension.getMinifyHtml());
            task.getExcludeTests().set(extension.getExcludeTests());
            if (extension.getTestSourceDirectory().isPresent()) {
                task.getTestSourceDirectory()
                        .set(extension.getTestSourceDirectory().get().getAbsolutePath());
            }
            if (extension.getOutputDirectory().isPresent()) {
                task.getOutputDirectory()
                        .set(extension.getOutputDirectory().get().getAbsolutePath());
            }
            task.getReportFile()
                    .set(project.getLayout().getBuildDirectory().file("reports/refactor-first/simple-report.html"));
        });
    }

    /** Returns a project-relative output path, or the legacy default for external paths. */
    public static String relativizeToProject(File baseDir, File outputDir) {
        String basePath = baseDir.getAbsolutePath();
        String outPath = outputDir.getAbsolutePath();
        if (outPath.startsWith(basePath)) {
            String rel = outPath.substring(basePath.length());
            if (rel.startsWith(File.separator)) {
                rel = rel.substring(1);
            }
            return rel;
        }
        return "target/site";
    }
}
