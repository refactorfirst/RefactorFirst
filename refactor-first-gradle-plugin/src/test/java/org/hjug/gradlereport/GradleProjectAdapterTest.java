package org.hjug.gradlereport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GradleProjectAdapterTest {
    private Project project;
    private RefactorFirstExtension extension;

    /** Creates a Gradle project and extension for each test. */
    @BeforeEach
    void setUp() {
        project = ProjectBuilder.builder().build();
        extension = project.getExtensions().create("refactorFirst", RefactorFirstExtension.class);
    }

    /** Verifies main sources are included in the adapter. */
    @Test
    void getSourceDirectoriesReturnsMainSourceSet() {
        extension.getExcludeTests().set(true);
        GradleProjectAdapter adapter = new GradleProjectAdapter(project, extension);

        List<File> sources = adapter.getSourceDirectories();

        assertFalse(sources.isEmpty(), "Should return main source directories");
    }

    /** Verifies test sources are included when configured. */
    @Test
    void getSourceDirectoriesIncludesTestSourcesWhenNotExcluded() {
        extension.getExcludeTests().set(false);
        GradleProjectAdapter adapter = new GradleProjectAdapter(project, extension);

        List<File> sources = adapter.getSourceDirectories();

        assertTrue(sources.size() >= 2, "Should include both main and test sources");
    }

    /** Verifies the adapter retains the project directory. */
    @Test
    void getProjectBaseDirReturnsProjectDirectory() {
        GradleProjectAdapter adapter = new GradleProjectAdapter(project, extension);

        File baseDir = adapter.getProjectBaseDir();

        assertEquals(project.getProjectDir(), baseDir);
    }

    /** Verifies the adapter retains the project name. */
    @Test
    void getProjectNameReturnsProjectName() {
        GradleProjectAdapter adapter = new GradleProjectAdapter(project, extension);

        String name = adapter.getProjectName();

        assertEquals(project.getName(), name);
    }

    /** Verifies the adapter retains the project version. */
    @Test
    void getProjectVersionReturnsProjectVersion() {
        GradleProjectAdapter adapter = new GradleProjectAdapter(project, extension);

        String version = adapter.getProjectVersion();

        assertEquals(project.getVersion().toString(), version);
    }
}
