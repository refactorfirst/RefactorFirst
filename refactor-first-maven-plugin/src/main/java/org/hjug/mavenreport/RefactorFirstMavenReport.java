package org.hjug.mavenreport;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.doxia.markup.HtmlMarkup;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkEventAttributes;
import org.apache.maven.doxia.sink.impl.SinkEventAttributeSet;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.reporting.AbstractMavenReport;
import org.hjug.refactorfirst.report.HtmlReport;

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
    private boolean showDetails = false;

    @Parameter(property = "backEdgeAnalysisCount")
    protected int backEdgeAnalysisCount = 50;

    @Parameter(property = "analyzeCycles")
    private boolean analyzeCycles = true;

    @Parameter(defaultValue = "${project.name}")
    private String projectName;

    @Parameter(defaultValue = "${project.version}")
    private String projectVersion;

    public String getOutputName() {
        // This report will generate simple-report.html when invoked in a project with `mvn site`
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

    @Override
    public void executeReport(Locale locale) {
        HtmlReport htmlReport = new HtmlReport();

        Sink mainSink = getSink();
        printHead(mainSink);
        String report = htmlReport
                .generateReport(
                        showDetails,
                        projectName,
                        projectVersion,
                        project.getBasedir(),
                        300,
                        backEdgeAnalysisCount,
                        analyzeCycles)
                .toString();

        mainSink.rawText(report);
    }

    private void printHead(Sink mainSink) {
        mainSink.head();
        mainSink.title();
        mainSink.text("Refactor First Report for " + projectName + " " + projectVersion);
        mainSink.title_();

        // GH Buttons import
        renderJsDeclaration(mainSink, "https://buttons.github.io/buttons.js");
        // google chart import
        renderJsDeclaration(mainSink, "https://www.gstatic.com/charts/loader.js");
        // d3 dot graph imports
        renderJsDeclaration(mainSink, "https://d3js.org/d3.v5.min.js");
        renderJsDeclaration(mainSink, "https://unpkg.com/d3-graphviz@3.0.5/build/d3-graphviz.min.js");
        renderJsDeclaration(mainSink, "https://unpkg.com/@hpcc-js/wasm@0.3.11/dist/index.min.js");

        // sigma graph imports - sigma, graphology, graphlib, and graphlib-dot
        renderJsDeclaration(mainSink, "https://cdnjs.cloudflare.com/ajax/libs/sigma.js/2.4.0/sigma.min.js");
        renderJsDeclaration(mainSink, "https://cdnjs.cloudflare.com/ajax/libs/graphology/0.25.4/graphology.umd.min.js");
        // may only need graphlib-dot
        renderJsDeclaration(mainSink, "https://cdnjs.cloudflare.com/ajax/libs/graphlib/2.1.8/graphlib.min.js");
        renderJsDeclaration(mainSink, "https://cdn.jsdelivr.net/npm/graphlib-dot@0.6.4/dist/graphlib-dot.min.js");

        renderJsDeclaration(mainSink, HtmlReport.SUGIYAMA_SIGMA_GRAPH);
        renderJsDeclaration(mainSink, HtmlReport.POPUP_FUNCTIONS);

        renderStyle(mainSink);

        mainSink.head_();
    }

    /**
     * @See https://maven.apache.org/doxia/developers/sink.html#How_to_inject_javascript_code_into_HTML
     */
    private void renderJsDeclaration(Sink mainSink, String scriptUrl) {
        SinkEventAttributeSet githubButtonJS = new SinkEventAttributeSet();
        githubButtonJS.addAttribute(SinkEventAttributes.TYPE, "text/javascript");
        githubButtonJS.addAttribute(SinkEventAttributes.SRC, scriptUrl);
        mainSink.unknown("script", new Object[] {HtmlMarkup.TAG_TYPE_START}, githubButtonJS);
        mainSink.unknown("script", new Object[] {HtmlMarkup.TAG_TYPE_END}, null);
    }

    private void renderStyle(Sink mainSink) {
        SinkEventAttributeSet githubButtonJS = new SinkEventAttributeSet();
        githubButtonJS.addAttribute(SinkEventAttributes.SRC, HtmlReport.POPUP_STYLE);
        mainSink.unknown("script", new Object[] {HtmlMarkup.TAG_TYPE_START}, githubButtonJS);
        mainSink.unknown("script", new Object[] {HtmlMarkup.TAG_TYPE_END}, null);
    }
}
