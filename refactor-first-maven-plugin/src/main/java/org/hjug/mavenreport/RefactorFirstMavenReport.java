package org.hjug.mavenreport;

import java.util.Locale;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.reporting.AbstractMavenReport;
import org.hjug.refactorfirst.report.HtmlReport;

/**
 * Phase P4/T4.6: the Doxia site report renders through the same pipeline as the
 * {@code htmlReport} goal — the template pipeline with the rf-data payload and inlined
 * assets. The site page's duplicated CDN head imports and the dead {@code renderStyle}
 * call are gone; the rendered document carries everything it needs.
 */
@Slf4j
@Mojo(
        name = "report",
        defaultPhase = LifecyclePhase.SITE,
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        requiresProject = false,
        threadSafe = true,
        inheritByDefault = false)
public class RefactorFirstMavenReport extends AbstractMavenReport {

    @Parameter(property = "showDetails")
    private boolean showDetails;

    @Parameter(property = "backEdgeAnalysisCount")
    protected int backEdgeAnalysisCount = 50;

    @Parameter(property = "analyzeCycles")
    private boolean analyzeCycles = true;

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

    public String getOutputName() {
        // This report will generate refactor-first-report.html when invoked in a project with `mvn site`
        return "refactor-first-report";
    }

    public String getName(Locale locale) {
        // Name of the report when listed in the project-reports.html page of a project
        return "Refactor First Report";
    }

    public String getDescription(Locale locale) {
        // Description of the report when listed in the project-reports.html page of a project
        return "Ranks the disharmonies in a codebase.  The classes that should be refactored first "
                + " have the highest priority values.";
    }

    @SneakyThrows
    @Override
    public void executeReport(Locale locale) {
        HtmlReport htmlReport = new HtmlReport();

        String report = htmlReport.renderInteractivePage(
                showDetails,
                backEdgeAnalysisCount,
                analyzeCycles,
                excludeTests,
                testSourceDirectory,
                projectName,
                projectVersion,
                project.getBasedir());

        getSink().rawText(report);
    }
}
