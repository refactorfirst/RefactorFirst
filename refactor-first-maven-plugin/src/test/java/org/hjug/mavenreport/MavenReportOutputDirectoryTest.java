package org.hjug.mavenreport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Path;
import org.apache.maven.model.Build;
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

    @Test
    void resolve_usesConfiguredReportingOutputDirectory(@TempDir Path tempDir) {
        Reporting reporting = new Reporting();
        reporting.setOutputDirectory("custom/reports");
        MavenProject project = projectAt(tempDir, reporting);

        assertEquals(
                tempDir.resolve("custom/reports").toString(),
                MavenReportOutputDirectory.resolve(
                        project, tempDir.resolve("target").toFile()));
    }

    @Test
    void resolve_ignoresUnresolvedPlaceholdersWhenRunWithoutPom() {
        MavenProject project = projectWithoutPom();

        assertEquals(
                Path.of("").toAbsolutePath().resolve("target/site").toString(),
                MavenReportOutputDirectory.resolve(project, null));
    }

    @Test
    void resolve_ignoresUnresolvedBuildDirectoryParameterWhenRunWithoutPom() {
        MavenProject project = projectWithoutPom();

        assertEquals(
                Path.of("").toAbsolutePath().resolve("target/site").toString(),
                MavenReportOutputDirectory.resolve(project, new File("${project.basedir}/target")));
    }

    @Test
    void resolve_usesBuildDirectoryParameterWhenRunWithoutPom() {
        MavenProject project = projectWithoutPom();
        File buildDirectory =
                Path.of("").toAbsolutePath().resolve("custom-output").toFile();

        assertEquals(
                new File(buildDirectory, "site").getPath(),
                MavenReportOutputDirectory.resolve(project, buildDirectory));
    }

    /** Mirrors the stub project Maven supplies in a directory with no pom.xml: super-POM defaults, not interpolated. */
    private static MavenProject projectWithoutPom() {
        Reporting reporting = new Reporting();
        reporting.setOutputDirectory("${project.basedir}/target/site");
        Build build = new Build();
        build.setDirectory("${project.basedir}/target");
        Model model = new Model();
        model.setReporting(reporting);
        model.setBuild(build);
        return new MavenProject(model);
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
