package org.hjug.mavenreport;

import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
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
import org.apache.maven.reporting.MavenReportException;
import org.hjug.cbc.CostBenefitCalculator;
import org.hjug.cbc.CycleRanker;
import org.hjug.cbc.RankedCycle;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.gdg.GraphDataGenerator;
import org.hjug.git.GitLogReader;
import org.hjug.refactorfirst.report.HtmlReport;
import org.jetbrains.annotations.NotNull;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

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
        // TODO: pass in the screen width to have d3.js render SVGs within the bounds of the screen
        String report = htmlReport
                .generateReport(showDetails, projectName, projectVersion, project.getBasedir())
                .toString();

        mainSink.rawText(report);
    }

    private void printHead(Sink mainSink) {
        mainSink.head();
        mainSink.title();
        mainSink.text("Refactor First Report for " + projectName + " " + projectVersion);
        mainSink.title_();

        /**
         * @See https://maven.apache.org/doxia/developers/sink.html#How_to_inject_javascript_code_into_HTML
         */
        SinkEventAttributeSet githubButtonJS = new SinkEventAttributeSet();
        githubButtonJS.addAttribute(SinkEventAttributes.SRC, "https://buttons.github.io/buttons.js");

        String script = "script";
        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_START}, githubButtonJS);
        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_END}, null);

        SinkEventAttributeSet googleChartImport = new SinkEventAttributeSet();
        googleChartImport.addAttribute(SinkEventAttributes.TYPE, "text/javascript");
        googleChartImport.addAttribute(SinkEventAttributes.SRC, "https://www.gstatic.com/charts/loader.js");

        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_START}, googleChartImport);
        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_END}, null);

        SinkEventAttributeSet d3js = new SinkEventAttributeSet();
        d3js.addAttribute(SinkEventAttributes.TYPE, "text/javascript");
        d3js.addAttribute(SinkEventAttributes.SRC, "https://d3js.org/d3.v5.min.js");

        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_START}, d3js);
        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_END}, null);

        SinkEventAttributeSet graphViz = new SinkEventAttributeSet();
        graphViz.addAttribute(SinkEventAttributes.TYPE, "text/javascript");
        graphViz.addAttribute(SinkEventAttributes.SRC, "https://unpkg.com/d3-graphviz@3.0.5/build/d3-graphviz.min.js");

        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_START}, graphViz);
        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_END}, null);

        SinkEventAttributeSet wasm = new SinkEventAttributeSet();
        wasm.addAttribute(SinkEventAttributes.TYPE, "text/javascript");
        wasm.addAttribute(SinkEventAttributes.SRC, "https://unpkg.com/@hpcc-js/wasm@0.3.11/dist/index.min.js");

        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_START}, wasm);
        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_END}, null);

        mainSink.head_();
    }
}
