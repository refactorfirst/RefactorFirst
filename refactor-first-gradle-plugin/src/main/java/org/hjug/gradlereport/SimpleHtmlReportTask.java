package org.hjug.gradlereport;

import java.io.File;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.hjug.refactorfirst.report.SimpleHtmlReport;

/**
 * Generates the simplified RefactorFirst HTML report at
 * {@code <projectDir>/build/reports/refactorfirst} (or the extension's {@code outputDirectory}).
 */
@DisableCachingByDefault(
        because = "Report tasks always regenerate their output; they are never up-to-date or loaded from cache.")
public class SimpleHtmlReportTask extends DefaultTask {

    private RefactorFirstExtension extension;
    private File projectDirectory;
    private Provider<Directory> defaultOutputDirectory;
    private String defaultProjectName;
    private String defaultProjectVersion;

    public SimpleHtmlReportTask() {
        // Contract: reports always regenerate — this task is never UP-TO-DATE and never FROM-CACHE.
        getOutputs().upToDateWhen(t -> false);
    }

    void configureFrom(
            RefactorFirstExtension extension,
            File projectDirectory,
            Provider<Directory> defaultOutputDirectory,
            String defaultProjectName,
            String defaultProjectVersion) {
        this.extension = extension;
        this.projectDirectory = projectDirectory;
        this.defaultOutputDirectory = defaultOutputDirectory;
        this.defaultProjectName = defaultProjectName;
        this.defaultProjectVersion = defaultProjectVersion;
    }

    @TaskAction
    public void generate() {
        String projectName = isBlank(extension.getProjectName()) ? defaultProjectName : extension.getProjectName();
        String projectVersion =
                isBlank(extension.getProjectVersion()) ? defaultProjectVersion : extension.getProjectVersion();
        File outputDir = ExtensionValues.resolveOutputDirectory(
                extension, projectDirectory, defaultOutputDirectory.get().getAsFile());

        SimpleHtmlReport htmlReport = new SimpleHtmlReport();
        htmlReport.execute(
                extension.getBackEdgeAnalysisCount(),
                extension.isAnalyzeCycles(),
                extension.isShowDetails(),
                extension.isMinifyHtml(),
                extension.isExcludeTests(),
                extension.getTestSourceDirectory(),
                projectName,
                projectVersion,
                projectDirectory,
                outputDir.getAbsolutePath());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
