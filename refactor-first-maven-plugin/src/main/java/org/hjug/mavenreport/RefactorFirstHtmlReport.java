package org.hjug.mavenreport;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.hjug.refactorfirst.report.HtmlReport;

@Slf4j
@Mojo(
        name = "htmlReport",
        defaultPhase = LifecyclePhase.SITE,
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        requiresProject = false,
        threadSafe = true,
        inheritByDefault = false)
public class RefactorFirstHtmlReport extends AbstractMojo {

    @Parameter(property = "showDetails")
    private boolean showDetails = false;

    @Parameter(property = "backEdgeAnalysisCount")
    protected int backEdgeAnalysisCount = 50;

    @Parameter(property = "analyzeCycles")
    private boolean analyzeCycles = true;

    @Parameter(property = "minifyHtml")
    private boolean minifyHtml = false;

    @Parameter(property = "excludeTests")
    private boolean excludeTests = true;

    /**
     * The test source directory containing test class sources.
     */
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

        log.info(outputDirectory.getPath());
        HtmlReport htmlReport = new HtmlReport();
        htmlReport.execute(
                backEdgeAnalysisCount,
                analyzeCycles,
                showDetails,
                minifyHtml,
                excludeTests,
                testSourceDirectory,
                projectName,
                projectVersion,
                project.getBasedir(),
                project.getModel()
                        .getReporting()
                        .getOutputDirectory()
                        .replace("${project.basedir}" + File.separator, ""));
    }
}
