package org.hjug.mavenreport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Path;
import org.apache.maven.model.Model;
import org.apache.maven.model.Reporting;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenReportOutputDirectoryTest {

    @Test
    void resolve_defaultsToBuildDirectorySiteWhenReportingIsAbsent(@TempDir Path tempDir) {
        MavenProject project = projectAt(tempDir, null);

        assertEquals(
                tempDir.resolve("target/site").toString(),
                MavenReportOutputDirectory.resolve(
                        project, tempDir.resolve("target").toFile()));
    }

    @Test
    void resolve_defaultsToBuildDirectorySiteWhenReportingOutputIsAbsent(@TempDir Path tempDir) {
        MavenProject project = projectAt(tempDir, new Reporting());

        assertEquals(
                tempDir.resolve("target/site").toString(),
                MavenReportOutputDirectory.resolve(
                        project, tempDir.resolve("target").toFile()));
    }

    private static MavenProject projectAt(Path baseDirectory, Reporting reporting) {
        Model model = new Model();
        model.setPomFile(new File(baseDirectory.toFile(), "pom.xml"));
        model.setReporting(reporting);
        MavenProject project = new MavenProject(model);
        project.setFile(model.getPomFile());
        return project;
    }
}
