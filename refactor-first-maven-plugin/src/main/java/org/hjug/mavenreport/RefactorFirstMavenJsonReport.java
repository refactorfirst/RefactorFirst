package org.hjug.mavenreport;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.hjug.refactorfirst.report.json.JsonReportExecutor;

@Mojo(
        name = "jsonreport",
        defaultPhase = LifecyclePhase.SITE,
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        requiresProject = false,
        threadSafe = true,
        inheritByDefault = false)
public class RefactorFirstMavenJsonReport extends AbstractMojo {
    private static final String FILE_NAME = "refactor-first-data.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    @Override
    public void execute() {
        JsonReportExecutor jsonReportExecutor = new JsonReportExecutor();
        jsonReportExecutor.execute(
                project.getBasedir(),
                project.getModel()
                        .getReporting()
                        .getOutputDirectory()
                        .replace("${project.basedir}" + File.separator, ""));
    }
}
