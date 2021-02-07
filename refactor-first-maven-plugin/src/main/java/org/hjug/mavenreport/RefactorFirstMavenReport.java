package org.hjug.mavenreport;

import lombok.extern.slf4j.Slf4j;
import org.apache.maven.doxia.markup.HtmlMarkup;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkEventAttributes;
import org.apache.maven.doxia.sink.impl.SinkEventAttributeSet;
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

@Slf4j
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
        String projectBaseDir = project.getBasedir().getPath();
        String parentOfGitDir = gitLogReader.getGitDir(project.getBasedir()).getParentFile().getPath();

        final String[] tableHeadings = {"Class",
                                        "Priority",
                                        "Change Proneness Rank",
                                        "Effort Rank",
                                        "WMC",
                                        "WMC Rank",
                                        "ATFD",
                                        "ATFD Rank",
                                        "TCC",
                                        "TCC Rank",
                                        "Date of First Commit",
                                        "Date of Most Recent Commit",
                                        "Commit Count",
                                        "Full Path"};

        log.info("Project Base Dir: {} ", projectBaseDir);
        log.info("Parent of Git Dir: {}", parentOfGitDir);

        if(!projectBaseDir.equals(parentOfGitDir)) {
            log.warn("Project Base Directory does not match Git Parent Directory");
            Sink mainSink = getSink();
            mainSink.paragraph();
            mainSink.text("Data could not be captured. Please ensure to refer to this report from the base of the project");
            mainSink.paragraph_();
            return;
        }

        GraphDataGenerator graphDataGenerator = new GraphDataGenerator();


        List<RankedDisharmony> rankedDisharmonies =
                graphDataGenerator.getRankedDisharmonies(projectBaseDir);

        rankedDisharmonies.sort(Comparator.comparing(RankedDisharmony::getPriority).reversed());

        String filename = getOutputName() + ".html";
        String projectName = project.getName();
        String projectVersion = project.getVersion();

        // Some info
        log.info("Generating {} for {} - {}", filename, projectName, projectVersion);

        // Get the Maven Doxia Sink, which will be used to generate the
        // various elements of the document
        Sink mainSink = getSink();
        if (mainSink == null) {
            throw new MavenReportException("Could not get the Doxia sink");
        }



        // Page head
        mainSink.head();
        mainSink.title();
        mainSink.text("Refactor First Report for " + projectName + " " + projectVersion);
        mainSink.title_();

        generateChart(graphDataGenerator, mainSink);

        mainSink.head_();

        mainSink.body();

        // Heading 1
        mainSink.section1();
        mainSink.sectionTitle1();
        mainSink.text("God Class Report for " + projectName + " " + projectVersion);
        mainSink.sectionTitle1_();

        if(rankedDisharmonies.isEmpty()) {
            mainSink.text("Contratulations!  " + projectName + " " + projectVersion + " has no God classes!");
            mainSink.section1_();
            mainSink.body_();
            log.info("Done! No God classes found!");
            return;
        }

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

        for (String heading : tableHeadings) {
            drawTableHeaderCell(heading, mainSink);
        }

        mainSink.tableRow_();

        DateTimeFormatter formatter =
                DateTimeFormatter.ofLocalizedDateTime( FormatStyle.SHORT )
                        .withLocale( Locale.getDefault() )
                        .withZone( ZoneId.systemDefault() );

        for (RankedDisharmony rankedDisharmony : rankedDisharmonies) {
            mainSink.tableRow();

            final String[] rankedDisharmonyData = {rankedDisharmony.getClassName(),
                                                   rankedDisharmony.getPriority().toString(),
                                                   rankedDisharmony.getChangePronenessRank().toString(),
                                                   rankedDisharmony.getEffortRank().toString(),
                                                   rankedDisharmony.getWmc().toString(),
                                                   rankedDisharmony.getWmcRank().toString(),
                                                   rankedDisharmony.getAtfd().toString(),
                                                   rankedDisharmony.getAtfdRank().toString(),
                                                   rankedDisharmony.getTcc().toString(),
                                                   rankedDisharmony.getTccRank().toString(),
                                                   formatter.format(rankedDisharmony.getFirstCommitTime()),
                                                   formatter.format(rankedDisharmony.getMostRecentCommitTime()),
                                                   rankedDisharmony.getCommitCount().toString(),
                                                   rankedDisharmony.getPath()};

            for (String rowData : rankedDisharmonyData) {
                drawTableCell(rowData, mainSink);
            }

            mainSink.tableRow_();
        }


        mainSink.table_();
        mainSink.paragraph_();

        // Close
        mainSink.section1_();
        mainSink.body_();
        log.info("Done! View the report at target/site/{}", filename);
    }

    private void drawTableHeaderCell(String cellText, Sink mainSink) {
        mainSink.tableHeaderCell();
        mainSink.text(cellText);
        mainSink.tableHeaderCell_();
    }

    private void drawTableCell(String cellText, Sink mainSink) {
        mainSink.tableCell();
        mainSink.text(cellText);
        mainSink.tableCell_();
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
            log.error("Failure creating chart script file", e);
        }

        try(BufferedWriter writer = new BufferedWriter(new FileWriter(scriptFile))) {
            writer.write(javascriptCode);
        } catch (IOException e) {
            log.error("Error writing chart script file", e);
        }

        SinkEventAttributeSet javascript = new SinkEventAttributeSet();
        javascript.addAttribute( SinkEventAttributes.TYPE, "text/javascript");
        javascript.addAttribute( SinkEventAttributes.SRC, "./gchart.js");

        mainSink.unknown(script, new Object[]{HtmlMarkup.TAG_TYPE_START }, javascript );
        mainSink.unknown(script, new Object[]{HtmlMarkup.TAG_TYPE_END}, null );
    }
}
