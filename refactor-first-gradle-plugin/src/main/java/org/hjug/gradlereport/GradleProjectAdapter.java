package org.hjug.gradlereport;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSetContainer;

public class GradleProjectAdapter implements Serializable {
    private static final long serialVersionUID = 1L;

    private final File projectBaseDir;
    private final String projectName;
    private final String projectVersion;
    private final List<String> sourceDirectoryPaths;

    public GradleProjectAdapter(Project project, RefactorFirstExtension extension) {
        this.projectBaseDir = project.getProjectDir();
        this.projectName = project.getName();
        this.projectVersion = project.getVersion() == null
                ? "unspecified"
                : project.getVersion().toString();
        this.sourceDirectoryPaths = extractSourceDirectoryPaths(project, extension);
        project.getPluginManager().apply("java");
    }

    private List<String> extractSourceDirectoryPaths(Project project, RefactorFirstExtension extension) {
        List<String> sourcePaths = new ArrayList<>();
        SourceSetContainer sourceSets = project.getExtensions().findByType(SourceSetContainer.class);

        if (sourceSets != null) {
            for (File file : sourceSets.getByName("main").getAllJava().getSrcDirs()) {
                if (file != null) {
                    sourcePaths.add(file.getAbsolutePath());
                }
            }
            if (!extension.getExcludeTests().get()) {
                for (File file : sourceSets.getByName("test").getAllJava().getSrcDirs()) {
                    if (file != null) {
                        sourcePaths.add(file.getAbsolutePath());
                    }
                }
            }
        } else {
            sourcePaths.add(new File(project.getProjectDir(), "src/main/java").getAbsolutePath());
            if (!extension.getExcludeTests().get()) {
                sourcePaths.add(new File(project.getProjectDir(), "src/test/java").getAbsolutePath());
            }
        }

        return sourcePaths;
    }

    public List<File> getSourceDirectories() {
        List<File> files = new ArrayList<>();
        for (String path : sourceDirectoryPaths) {
            files.add(new File(path));
        }
        return files;
    }

    public File getProjectBaseDir() {
        return projectBaseDir;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getProjectVersion() {
        return projectVersion;
    }
}
