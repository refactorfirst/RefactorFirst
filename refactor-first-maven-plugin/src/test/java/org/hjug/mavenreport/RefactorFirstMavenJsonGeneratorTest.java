package org.hjug.mavenreport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RefactorFirstMavenJsonGeneratorTest {

    @TempDir
    Path tempDir;

    /** Verifies the JSON report is written to the project root, not the build directory. */
    @Test
    void writesJsonToProjectRootNotBuildDirectory() throws Exception {
        Path targetDir = tempDir.resolve("target");
        Files.createDirectories(targetDir);

        RefactorFirstMavenJsonGenerator mojo = new RefactorFirstMavenJsonGenerator();
        setField(mojo, "project", projectAt(tempDir));
        setField(mojo, "projectName", "TestProject");
        setField(mojo, "projectVersion", "1.0.0");
        setField(mojo, "testSourceDirectory", "src/test");
        // Mirrors real Maven execution, where ${project.build.directory} is injected
        setFieldIfPresent(mojo, "outputDirectory", targetDir.toFile());

        mojo.execute();

        Path expected = tempDir.resolve(".refactorfirst").resolve("refactor-first.json");
        assertTrue(
                Files.exists(expected),
                "JSON report should be written to <project-root>/.refactorfirst/refactor-first.json");
        assertFalse(
                Files.exists(targetDir.resolve(".refactorfirst").resolve("refactor-first.json")),
                "JSON report must not be written under the build directory");
    }

    /** Creates a Maven project rooted at the supplied directory. */
    private static MavenProject projectAt(Path baseDirectory) {
        Model model = new Model();
        model.setPomFile(new File(baseDirectory.toFile(), "pom.xml"));
        MavenProject project = new MavenProject(model);
        project.setFile(model.getPomFile());
        return project;
    }

    /** Assigns a private generator field for test setup. */
    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = RefactorFirstMavenJsonGenerator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Assigns a private generator field when that field exists. */
    private static void setFieldIfPresent(Object target, String name, Object value) throws Exception {
        try {
            setField(target, name, value);
        } catch (NoSuchFieldException ignored) {
            // Field removed from the mojo; nothing to inject
        }
    }
}
