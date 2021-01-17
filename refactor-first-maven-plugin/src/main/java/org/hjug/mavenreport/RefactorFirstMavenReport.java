package org.hjug.mavenreport;

import org.apache.maven.doxia.markup.HtmlMarkup;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkEventAttributes;
import org.apache.maven.doxia.sink.impl.SinkEventAttributeSet;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.gdg.GraphDataGenerator;
import org.hjug.git.GitLogReader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;


@Mojo(
        name = "report",
        defaultPhase = LifecyclePhase.SITE,
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        requiresProject = true,
        threadSafe = true,
        inheritByDefault = false
)
public class RefactorFirstMavenReport extends AbstractMavenReport {

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
    protected void executeReport(Locale locale) throws MavenReportException {

        GitLogReader gitLogReader = new GitLogReader();
        getLog().info("Project Basedir: " + project.getBasedir().getPath());
        getLog().info("Parent of Git Dir: " + gitLogReader.getGitDir(project.getBasedir()).getParentFile().getPath());
        if(!project.getBasedir().getPath().equals(gitLogReader.getGitDir(project.getBasedir()).getParentFile().getPath())) {
            Sink mainSink = getSink();
            mainSink.paragraph();
            mainSink.text("Please refer to this report in the base of the project");
            mainSink.paragraph_();
            return;
        }

        GraphDataGenerator graphDataGenerator = new GraphDataGenerator();


        List<RankedDisharmony> rankedDisharmonies =
                graphDataGenerator.getRankedDisharmonies(project.getBasedir().getPath());

        rankedDisharmonies.sort(Comparator.comparing(RankedDisharmony::getPriority).reversed());

        // Get the logger
        Log logger = getLog();

        // Some info
        logger.info("Generating " + getOutputName() + ".html"
                + " for " + project.getName() + " " + project.getVersion());

        // Get the Maven Doxia Sink, which will be used to generate the
        // various elements of the document
        Sink mainSink = getSink();
        if (mainSink == null) {
            throw new MavenReportException("Could not get the Doxia sink");
        }



        // Page head
        mainSink.head();
        mainSink.title();
        mainSink.text("Refactor First Report for " + project.getName() + " " + project.getVersion());
        mainSink.title_();

        generateChart(graphDataGenerator, mainSink);

        mainSink.head_();

        mainSink.body();

        // Heading 1
        mainSink.section1();
        mainSink.sectionTitle1();
        mainSink.text("God Class Report for " + project.getName() + " " + project.getVersion());
        mainSink.sectionTitle1_();

        // Content

        SinkEventAttributeSet divAttrs = new SinkEventAttributeSet();
        divAttrs.addAttribute( SinkEventAttributes.ID, "series_chart_div" );
        mainSink.unknown( "div", new Object[]{HtmlMarkup.TAG_TYPE_START}, divAttrs );
        mainSink.unknown( "div", new Object[]{HtmlMarkup.TAG_TYPE_END}, null );

        SinkEventAttributes tableAttributes = new SinkEventAttributeSet();
        tableAttributes.addAttribute(SinkEventAttributes.BORDER, "5px");
        tableAttributes.addAttribute(SinkEventAttributes.CLASS, "table table-striped");
        mainSink.table(tableAttributes);
        mainSink.tableRow();

        mainSink.tableHeaderCell();
        mainSink.text("Class");
        mainSink.tableHeaderCell_();

        mainSink.tableHeaderCell();
        mainSink.text("Priority");
        mainSink.tableHeaderCell_();

        mainSink.tableHeaderCell();
        mainSink.text("Change Proneness Rank");
        mainSink.tableHeaderCell_();

        mainSink.tableHeaderCell();
        mainSink.text("Effort Rank");
        mainSink.tableHeaderCell_();

        mainSink.tableHeaderCell();
        mainSink.text("WMC");
        mainSink.tableHeaderCell_();

        mainSink.tableHeaderCell();
        mainSink.text("WMC Rank");
        mainSink.tableHeaderCell_();

        mainSink.tableHeaderCell();
        mainSink.text("ATFD");
        mainSink.tableHeaderCell_();

        mainSink.tableHeaderCell();
        mainSink.text("ATFD Rank");
        mainSink.tableHeaderCell_();

        mainSink.tableHeaderCell();
        mainSink.text("TCC");
        mainSink.tableHeaderCell_();

        mainSink.tableHeaderCell();
        mainSink.text("TCC Rank");
        mainSink.tableHeaderCell_();

        mainSink.tableHeaderCell();
        mainSink.text("Date of First Commit");
        mainSink.tableHeaderCell_();

        mainSink.tableHeaderCell();
        mainSink.text("Date of Most Recent Commit");
        mainSink.tableHeaderCell_();

        mainSink.tableHeaderCell();
        mainSink.text("Commit Count");
        mainSink.tableHeaderCell_();

        mainSink.tableHeaderCell();
        mainSink.text("Full Path");
        mainSink.tableHeaderCell_();

        mainSink.tableRow_();

        DateTimeFormatter formatter =
                DateTimeFormatter.ofLocalizedDateTime( FormatStyle.SHORT )
                        .withLocale( Locale.getDefault() )
                        .withZone( ZoneId.systemDefault() );

        for (RankedDisharmony rankedDisharmony : rankedDisharmonies) {
            mainSink.tableRow();

            mainSink.tableCell();
            mainSink.text(rankedDisharmony.getClassName());
            mainSink.tableCell_();

            mainSink.tableCell();
            mainSink.text(rankedDisharmony.getPriority().toString());
            mainSink.tableCell_();

            mainSink.tableCell();
            mainSink.text(rankedDisharmony.getChangePronenessRank().toString());
            mainSink.tableCell_();

            mainSink.tableCell();
            mainSink.text(rankedDisharmony.getEffortRank().toString());
            mainSink.tableCell_();

            mainSink.tableCell();
            mainSink.text(rankedDisharmony.getWmc().toString());
            mainSink.tableCell_();

            mainSink.tableCell();
            mainSink.text(rankedDisharmony.getWmcRank().toString());
            mainSink.tableCell_();

            mainSink.tableCell();
            mainSink.text(rankedDisharmony.getAtfd().toString());
            mainSink.tableCell_();

            mainSink.tableCell();
            mainSink.text(rankedDisharmony.getAtfdRank().toString());
            mainSink.tableCell_();

            mainSink.tableCell();
            mainSink.text(rankedDisharmony.getTcc().toString());
            mainSink.tableCell_();

            mainSink.tableCell();
            mainSink.text(rankedDisharmony.getTccRank().toString());
            mainSink.tableCell_();

            mainSink.tableCell();
            mainSink.text(formatter.format(rankedDisharmony.getFirstCommitTime()));
            mainSink.tableCell_();

            mainSink.tableCell();
            mainSink.text(formatter.format(rankedDisharmony.getMostRecentCommitTime()));
            mainSink.tableCell_();

            mainSink.tableCell();
            mainSink.text(rankedDisharmony.getCommitCount().toString());
            mainSink.tableCell_();

            mainSink.tableCell();
            mainSink.text(rankedDisharmony.getPath());
            mainSink.tableCell_();

            mainSink.tableRow_();
        }


        mainSink.table_();
        mainSink.paragraph_();

        // Close
        mainSink.section1_();
        mainSink.body_();

    }

    /**
     * @See https://maven.apache.org/doxia/developers/sink.html#How_to_inject_javascript_code_into_HTML
     */
    private void generateChart(GraphDataGenerator graphDataGenerator, Sink mainSink) {

        SinkEventAttributeSet googleChartImport = new SinkEventAttributeSet();
        googleChartImport.addAttribute( SinkEventAttributes.TYPE, "text/javascript" );
        googleChartImport.addAttribute( SinkEventAttributes.SRC, "https://www.gstatic.com/charts/loader.js" );

        String script = "script";
        mainSink.unknown(script, new Object[]{HtmlMarkup.TAG_TYPE_START}, googleChartImport);
        mainSink.unknown(script, new Object[]{HtmlMarkup.TAG_TYPE_END}, null);
        String scriptStart = graphDataGenerator.getScriptStart();
        String bubbleChartData = graphDataGenerator.generateBubbleChartData(project.getBasedir().getPath());
        String scriptEnd = graphDataGenerator.getScriptEnd();

        String javascriptCode = scriptStart + bubbleChartData + scriptEnd;

        String reportOutputDirectory = project.getModel().getReporting().getOutputDirectory();
        File reportOutputDir = new File(reportOutputDirectory);
        if(!reportOutputDir.exists()) {
            reportOutputDir.mkdirs();
        }
        String pathname = reportOutputDirectory + File.separator + "gchart.js";

        File scriptFile = new File(pathname);
        try {
            scriptFile.createNewFile();
        } catch (IOException e) {
            getLog().error("Failure creating chart script file", e);
        }

        try(BufferedWriter writer = new BufferedWriter(new FileWriter(scriptFile))) {
            writer.write(javascriptCode);
        } catch (IOException e) {
            getLog().error("Error writing chart script file",e);
        }

        SinkEventAttributeSet javascript = new SinkEventAttributeSet();
        javascript.addAttribute( SinkEventAttributes.TYPE, "text/javascript");
        javascript.addAttribute( SinkEventAttributes.SRC, "./gchart.js");

        mainSink.unknown(script, new Object[]{HtmlMarkup.TAG_TYPE_START }, javascript );
        mainSink.unknown(script, new Object[]{HtmlMarkup.TAG_TYPE_END}, null );
    }
}
