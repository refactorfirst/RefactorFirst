package org.hjug.mavenreport;

import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.hjug.refactorfirst.report.SimpleHtmlReport;

@Slf4j
@Mojo(
        name = "simpleHtmlReport",
        defaultPhase = LifecyclePhase.SITE,
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        requiresProject = false,
        threadSafe = true,
        inheritByDefault = false)
public class RefactorFirstSimpleHtmlReport extends AbstractMojo {

    @Parameter(property = "showDetails")
    private boolean showDetails = false;

    @Parameter(property = "backEdgeAnalysisCount")
    private int backEdgeAnalysisCount = 50;

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
        SimpleHtmlReport htmlReport = new SimpleHtmlReport();
        htmlReport.execute(
                showDetails,
                projectName,
                projectVersion,
                project.getModel()
                        .getReporting()
                        .getOutputDirectory()
                        .replace("${project.basedir}" + File.separator, ""),
                project.getBasedir(),
                backEdgeAnalysisCount);
    }
}
