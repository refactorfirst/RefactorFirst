package org.hjug.mavenreport;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.hjug.refactorfirst.report.JsonGenerator;

@Slf4j
@Mojo(
        name = "jsonReport",
        defaultPhase = LifecyclePhase.SITE,
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        requiresProject = false,
        threadSafe = true,
        inheritByDefault = false)
public class RefactorFirstMavenJsonGenerator extends AbstractMojo {

    @Parameter(property = "showDetails")
    private boolean showDetails;

    @Parameter(property = "backEdgeAnalysisCount")
    protected int backEdgeAnalysisCount = 50;

    @Parameter(property = "analyzeCycles")
    private boolean analyzeCycles = true;

    @Parameter(property = "excludeTests")
    private boolean excludeTests = true;

    @Parameter(property = "testSourceDirectory")
    private String testSourceDirectory;

    @Parameter(defaultValue = "${project.name}")
    private String projectName;

    @Parameter(defaultValue = "${project.version}")
    private String projectVersion;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    @Parameter(property = "project.build.directory")
    protected File outputDirectory;

    @Override
    public void execute() {
        JsonGenerator generator = new JsonGenerator();
        generator.execute(
                backEdgeAnalysisCount,
                analyzeCycles,
                showDetails,
                excludeTests,
                testSourceDirectory,
                projectName,
                projectVersion,
                project.getBasedir(),
                outputDirectory);
    }
}
