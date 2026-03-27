package org.hjug.gradlereport;

import java.io.File;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.hjug.refactorfirst.report.HtmlReport;

public class HtmlReportTask extends DefaultTask {

    @TaskAction
    public void generate() {
        RefactorFirstExtension ext = getProject().getExtensions().findByType(RefactorFirstExtension.class);
        if (ext == null) {
            ext = new RefactorFirstExtension();
        }

        final String projectName = ext.getProjectName() != null ? ext.getProjectName() : getProject().getName();
        final String projectVersion = ext.getProjectVersion() != null ? ext.getProjectVersion() : String.valueOf(getProject().getVersion());
        final File baseDir = getProject().getProjectDir();
        final File outputDir = ext.resolveOutputDir(baseDir);

        HtmlReport htmlReport = new HtmlReport();
        htmlReport.execute(
                ext.getBackEdgeAnalysisCount(),
                ext.isAnalyzeCycles(),
                ext.isShowDetails(),
                ext.isMinifyHtml(),
                ext.isExcludeTests(),
                ext.getTestSourceDirectory(),
                projectName,
                projectVersion,
                baseDir,
                RefactorFirstPlugin.relativizeToProject(baseDir, outputDir)
        );
    }
}
