package org.hjug.gradlereport;

import java.io.File;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.hjug.refactorfirst.report.json.JsonReportExecutor;

public class JsonReportTask extends DefaultTask {

    @TaskAction
    public void generate() {
        RefactorFirstExtension ext = getProject().getExtensions().findByType(RefactorFirstExtension.class);
        if (ext == null) {
            ext = new RefactorFirstExtension();
        }

        final File baseDir = getProject().getProjectDir();
        final File outputDir = ext.resolveOutputDir(baseDir);

        JsonReportExecutor jsonReportExecutor = new JsonReportExecutor();
        jsonReportExecutor.execute(baseDir, RefactorFirstPlugin.relativizeToProject(baseDir, outputDir));
    }
}
