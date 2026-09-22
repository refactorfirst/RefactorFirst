package org.hjug.mavenreport;

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

    /**
     * Force an attempt to load the Java 25 parser even when the runtime is not
     * detected as Java 25 or higher. Escape hatch for exotic JVMs where version
     * detection fails; a failed attempt falls back to the standard parser.
     */
    @Parameter(property = "forceJava25Parser")
    private boolean forceJava25Parser = false;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    /** Generates the RefactorFirst JSON report for the current Maven project. */
    @Override
    public void execute() {
        JsonGenerator generator = new JsonGenerator();
        // Pass null outputDirectory so the report is written to <project-root>/.refactorfirst/refactor-first.json
        generator.execute(
                backEdgeAnalysisCount,
                analyzeCycles,
                showDetails,
                excludeTests,
                testSourceDirectory,
                projectName,
                projectVersion,
                project.getBasedir(),
                null,
                forceJava25Parser);
    }
}
