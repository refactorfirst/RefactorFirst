package org.hjug.gradlereport;

import java.io.File;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.hjug.refactorfirst.report.JsonGenerator;

/**
 * Generates the RefactorFirst JSON report via {@link JsonGenerator} — the same generator the
 * Maven plugin's {@code jsonReport} mojo uses. Output always lands in
 * {@code <projectDir>/.refactorfirst} (JSON plus the bundled viewer);
 * the extension's {@code outputDirectory} does not apply to this task.
 */
@DisableCachingByDefault(
        because = "Report tasks always regenerate their output; they are never up-to-date or loaded from cache.")
public class JsonReportTask extends DefaultTask {

    private RefactorFirstExtension extension;
    private File projectDirectory;
    private String defaultProjectName;
    private String defaultProjectVersion;

    public JsonReportTask() {
        // Contract: reports always regenerate — this task is never UP-TO-DATE and never FROM-CACHE.
        getOutputs().upToDateWhen(t -> false);
    }

    void configureFrom(
            RefactorFirstExtension extension,
            File projectDirectory,
            String defaultProjectName,
            String defaultProjectVersion) {
        this.extension = extension;
        this.projectDirectory = projectDirectory;
        this.defaultProjectName = defaultProjectName;
        this.defaultProjectVersion = defaultProjectVersion;
    }

    @TaskAction
    public void generate() {
        String projectName = isBlank(extension.getProjectName()) ? defaultProjectName : extension.getProjectName();
        String projectVersion =
                isBlank(extension.getProjectVersion()) ? defaultProjectVersion : extension.getProjectVersion();

        // A null output directory makes JsonGenerator write to <projectDir>/.refactorfirst.
        JsonGenerator jsonGenerator = new JsonGenerator();
        jsonGenerator.execute(
                extension.getBackEdgeAnalysisCount(),
                extension.isAnalyzeCycles(),
                extension.isShowDetails(),
                extension.isExcludeTests(),
                extension.getTestSourceDirectory(),
                projectName,
                projectVersion,
                projectDirectory,
                null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
