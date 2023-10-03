package org.hjug.mavenreport;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
import org.hjug.cbc.RankedDisharmony;
import org.hjug.gdg.GraphDataGenerator;
import org.hjug.git.GitLogReader;

@Slf4j
@Mojo(
        name = "report",
        defaultPhase = LifecyclePhase.SITE,
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        requiresProject = false,
        threadSafe = true,
        inheritByDefault = false)
public class RefactorFirstRealMavenReport extends AbstractMavenReport {

    @Parameter(property = "showDetails")
    private boolean showDetails = false;

    @Parameter(defaultValue = "${project.name}")
    private String projectName;

    @Parameter(defaultValue = "${project.version}")
    private String projectVersion;

    private DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault());

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
    public void executeReport(Locale locale) throws MavenReportException {

        final String[] godClassSimpleTableHeadings = {
            "Class",
            "Priority",
            "Change Proneness Rank",
            "Effort Rank",
            "Method Count",
            "Most Recent Commit Date",
            "Commit Count"
        };

        final String[] godClassDetailedTableHeadings = {
            "Class",
            "Priority",
            "Raw Priority",
            "Change Proneness Rank",
            "Effort Rank",
            "WMC",
            "WMC Rank",
            "ATFD",
            "ATFD Rank",
            "TCC",
            "TCC Rank",
            "Date of First Commit",
            "Most Recent Commit Date",
            "Commit Count",
            "Full Path"
        };

        final String[] cboTableHeadings = {
            "Class", "Priority", "Change Proneness Rank", "Coupling Count", "Most Recent Commit Date", "Commit Count"
        };

        final String[] godClassTableHeadings =
                showDetails ? godClassDetailedTableHeadings : godClassSimpleTableHeadings;

        if (Objects.equals(project.getName(), "Maven Stub Project (No POM)")) {
            projectName = new File(Paths.get("").toAbsolutePath().toString()).getName();
        }

        String filename = getOutputName() + ".html";
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

        /**
         * @See https://maven.apache.org/doxia/developers/sink.html#How_to_inject_javascript_code_into_HTML
         */
        SinkEventAttributeSet githubButtonJS = new SinkEventAttributeSet();
        // githubButtonJS.addAttribute(SinkEventAttributes.TYPE, "text/javascript");
        // githubButtonJS.addAttribute("async", "");
        // githubButtonJS.addAttribute("defer", "");
        githubButtonJS.addAttribute(SinkEventAttributes.SRC, "https://buttons.github.io/buttons.js");

        String script = "script";
        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_START}, githubButtonJS);
        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_END}, null);

        SinkEventAttributeSet googleChartImport = new SinkEventAttributeSet();
        googleChartImport.addAttribute(SinkEventAttributes.TYPE, "text/javascript");
        googleChartImport.addAttribute(SinkEventAttributes.SRC, "https://www.gstatic.com/charts/loader.js");

        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_START}, googleChartImport);
        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_END}, null);

        SinkEventAttributeSet godClassJavascript = new SinkEventAttributeSet();
        godClassJavascript.addAttribute(SinkEventAttributes.TYPE, "text/javascript");
        godClassJavascript.addAttribute(SinkEventAttributes.SRC, "./godClassChart.js");

        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_START}, godClassJavascript);
        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_END}, null);

        SinkEventAttributeSet cboJavascript = new SinkEventAttributeSet();
        cboJavascript.addAttribute(SinkEventAttributes.TYPE, "text/javascript");
        cboJavascript.addAttribute(SinkEventAttributes.SRC, "./cboChart.js");

        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_START}, cboJavascript);
        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_END}, null);
        mainSink.head_();

        mainSink.body();

        // Heading 1
        mainSink.section1();
        mainSink.sectionTitle1();
        mainSink.text("RefactorFirst Report for " + projectName + " " + projectVersion);
        mainSink.sectionTitle1_();

        GitLogReader gitLogReader = new GitLogReader();
        String projectBaseDir;
        Optional<File> optionalGitDir;

        File baseDir = project.getBasedir();
        if (baseDir != null) {
            projectBaseDir = baseDir.getPath();
            optionalGitDir = Optional.ofNullable(gitLogReader.getGitDir(baseDir));
        } else {
            projectBaseDir = Paths.get("").toAbsolutePath().toString();
            optionalGitDir = Optional.ofNullable(gitLogReader.getGitDir(new File(projectBaseDir)));
        }

        File gitDir;
        if (optionalGitDir.isPresent()) {
            gitDir = optionalGitDir.get();
        } else {
            log.info(
                    "Done! No Git repository found!  Please initialize a Git repository and perform an initial commit.");

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder
                    .append("No Git repository found in project ")
                    .append(projectName)
                    .append(" ")
                    .append(projectVersion)
                    .append(".  ");
            stringBuilder.append("Please initialize a Git repository and perform an initial commit.");
            mainSink.text(stringBuilder.toString());
            return;
        }

        String parentOfGitDir = gitDir.getParentFile().getPath();
        log.info("Project Base Dir: {} ", projectBaseDir);
        log.info("Parent of Git Dir: {}", parentOfGitDir);

        if (!projectBaseDir.equals(parentOfGitDir)) {
            log.warn("Project Base Directory does not match Git Parent Directory");
            mainSink.text("Project Base Directory does not match Git Parent Directory.  "
                    + "Please refer to the report at the root of the site directory.");
            return;
        }

        CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator();
        List<RankedDisharmony> rankedGodClassDisharmonies =
                costBenefitCalculator.calculateGodClassCostBenefitValues(projectBaseDir);

        List<RankedDisharmony> rankedCBODisharmonies =
                costBenefitCalculator.calculateCBOCostBenefitValues(projectBaseDir);

        if (rankedGodClassDisharmonies.isEmpty() && rankedCBODisharmonies.isEmpty()) {
            mainSink.text("Contratulations!  " + projectName + " " + projectVersion
                    + " has no God classes or highly coupled classes!");
            mainSink.section1_();
            renderGitHubButtons(mainSink);
            mainSink.body_();
            log.info("Done! No Disharmonies found!");
            return;
        }

        /*        if (!rankedGodClassDisharmonies.isEmpty() && !rankedCBODisharmonies.isEmpty()) {
            SinkEventAttributeSet godClassesLink = new SinkEventAttributeSet();
            godClassesLink.addAttribute(SinkEventAttributes.SRC, "#GOD");
            mainSink.anchor("God Classes", godClassesLink);
            mainSink.anchor_();

            mainSink.lineBreak();

            SinkEventAttributeSet cboClassesLink = new SinkEventAttributeSet();
            godClassesLink.addAttribute(SinkEventAttributes.SRC, "#CBO");
            mainSink.anchor("Highly Coupled Classes", cboClassesLink);
            mainSink.anchor_();
        }*/

        if (!rankedGodClassDisharmonies.isEmpty()) {
            rankedGodClassDisharmonies.sort(
                    Comparator.comparing(RankedDisharmony::getRawPriority).reversed());

            int godClassPriority = 1;
            for (RankedDisharmony rankedGodClassDisharmony : rankedGodClassDisharmonies) {
                rankedGodClassDisharmony.setPriority(godClassPriority++);
            }

            SinkEventAttributeSet alignCenter = new SinkEventAttributeSet();
            alignCenter.addAttribute(SinkEventAttributes.ALIGN, "center");

            mainSink.division(alignCenter);
            mainSink.section2();
            mainSink.sectionTitle2();
            mainSink.text("God Classes");
            mainSink.sectionTitle2_();
            mainSink.section2_();
            mainSink.division_();

            writeGodClassGchartJs(rankedGodClassDisharmonies, godClassPriority - 1);
            SinkEventAttributeSet seriesChartDiv = new SinkEventAttributeSet();
            seriesChartDiv.addAttribute(SinkEventAttributes.ID, "series_chart_div");
            seriesChartDiv.addAttribute(SinkEventAttributes.ALIGN, "center");
            mainSink.division(seriesChartDiv);
            mainSink.division_();

            renderGitHubButtons(mainSink);

            String legendHeading = "God Class Chart Legend:";
            String xAxis = "Effort to refactor to a non-God class";
            renderLegend(mainSink, legendHeading, xAxis);

            /*
             *God Class table
             */
            mainSink.lineBreak();
            mainSink.lineBreak();
            mainSink.division(alignCenter);
            mainSink.section3();
            mainSink.sectionTitle3();
            mainSink.text("God classes by the numbers: (Refactor Starting with Priority 1)");
            mainSink.sectionTitle3_();
            mainSink.section3_();
            mainSink.division_();

            mainSink.table();
            mainSink.tableRows(new int[] {Sink.JUSTIFY_LEFT}, true);

            // header row
            mainSink.tableRow();
            for (String heading : godClassTableHeadings) {
                drawTableHeaderCell(heading, mainSink);
            }
            mainSink.tableRow_();

            for (RankedDisharmony rankedGodClassDisharmony : rankedGodClassDisharmonies) {
                mainSink.tableRow();

                Object[] simpleRankedGodClassDisharmonyData = {
                    rankedGodClassDisharmony.getFileName(),
                    rankedGodClassDisharmony.getPriority(),
                    rankedGodClassDisharmony.getChangePronenessRank(),
                    rankedGodClassDisharmony.getEffortRank(),
                    rankedGodClassDisharmony.getWmc(),
                    rankedGodClassDisharmony.getMostRecentCommitTime(),
                    rankedGodClassDisharmony.getCommitCount()
                };

                Object[] detailedRankedGodClassDisharmonyData = {
                    rankedGodClassDisharmony.getFileName(),
                    rankedGodClassDisharmony.getPriority(),
                    rankedGodClassDisharmony.getRawPriority(),
                    rankedGodClassDisharmony.getChangePronenessRank(),
                    rankedGodClassDisharmony.getEffortRank(),
                    rankedGodClassDisharmony.getWmc(),
                    rankedGodClassDisharmony.getWmcRank(),
                    rankedGodClassDisharmony.getAtfd(),
                    rankedGodClassDisharmony.getAtfdRank(),
                    rankedGodClassDisharmony.getTcc(),
                    rankedGodClassDisharmony.getTccRank(),
                    rankedGodClassDisharmony.getFirstCommitTime(),
                    rankedGodClassDisharmony.getMostRecentCommitTime(),
                    rankedGodClassDisharmony.getCommitCount(),
                    rankedGodClassDisharmony.getPath()
                };

                final Object[] rankedDisharmonyData =
                        showDetails ? detailedRankedGodClassDisharmonyData : simpleRankedGodClassDisharmonyData;

                for (Object rowData : rankedDisharmonyData) {
                    drawTableCell(rowData, mainSink);
                }

                mainSink.tableRow_();
            }

            mainSink.tableRows_();
            mainSink.table_();
        }

        if (!rankedCBODisharmonies.isEmpty()) {
            if (!rankedGodClassDisharmonies.isEmpty()) {
                mainSink.lineBreak();
                mainSink.lineBreak();
                mainSink.lineBreak();
                mainSink.horizontalRule();
                mainSink.lineBreak();
                mainSink.lineBreak();
            }

            rankedCBODisharmonies.sort(
                    Comparator.comparing(RankedDisharmony::getRawPriority).reversed());

            int cboPriority = 1;
            for (RankedDisharmony rankedCBODisharmony : rankedCBODisharmonies) {
                rankedCBODisharmony.setPriority(cboPriority++);
            }

            SinkEventAttributeSet alignCenter = new SinkEventAttributeSet();
            alignCenter.addAttribute(SinkEventAttributes.ALIGN, "center");

            mainSink.division(alignCenter);
            mainSink.section2();
            mainSink.sectionTitle2();
            mainSink.text("Highly Coupled Classes");
            mainSink.sectionTitle2_();
            mainSink.section2_();
            mainSink.division_();

            SinkEventAttributeSet seriesChartDiv = new SinkEventAttributeSet();
            seriesChartDiv.addAttribute(SinkEventAttributes.ID, "series_chart_div_2");
            seriesChartDiv.addAttribute(SinkEventAttributes.ALIGN, "center");
            mainSink.division(seriesChartDiv);
            mainSink.division_();
            writeGCBOGchartJs(rankedCBODisharmonies, cboPriority - 1);

            renderGitHubButtons(mainSink);

            String legendHeading = "Highly Coupled Classes Chart Legend:";
            String xAxis = "Number of objects the class is coupled to";
            renderLegend(mainSink, legendHeading, xAxis);

            mainSink.division(alignCenter);
            mainSink.section3();
            mainSink.sectionTitle3();
            mainSink.text("Highly Coupled classes by the numbers: (Refactor Starting with Priority 1)");
            mainSink.sectionTitle3_();
            mainSink.section3_();
            mainSink.division_();

            SinkEventAttributeSet disharmonyTable = new SinkEventAttributeSet();
            mainSink.table(disharmonyTable);
            mainSink.tableRows(new int[] {Sink.JUSTIFY_LEFT}, true);
            mainSink.tableRow();
            // Content
            for (String heading : cboTableHeadings) {
                drawTableHeaderCell(heading, mainSink);
            }
            mainSink.tableRow_();

            for (RankedDisharmony rankedCboClassDisharmony : rankedCBODisharmonies) {
                mainSink.tableRow();

                String[] rankedCboClassDisharmonyData = {
                    rankedCboClassDisharmony.getFileName(),
                    rankedCboClassDisharmony.getPriority().toString(),
                    rankedCboClassDisharmony.getChangePronenessRank().toString(),
                    rankedCboClassDisharmony.getEffortRank().toString(),
                    formatter.format(rankedCboClassDisharmony.getMostRecentCommitTime()),
                    rankedCboClassDisharmony.getCommitCount().toString()
                };

                for (String rowData : rankedCboClassDisharmonyData) {
                    drawTableCell(rowData, mainSink);
                }

                mainSink.tableRow_();
            }
        }
        mainSink.tableRows_();
        mainSink.table_();

        // Close
        mainSink.section1_();
        mainSink.body_();

        log.info("Done! View the report at target/site/{}", filename);
    }

    private void renderLegend(Sink mainSink, String legendHeading, String xAxis) {
        SinkEventAttributeSet width = new SinkEventAttributeSet();
        width.addAttribute(SinkEventAttributes.STYLE, "width:350px");
        mainSink.division(width);
        mainSink.table();
        mainSink.tableRows(new int[] {Sink.JUSTIFY_LEFT}, true);
        mainSink.tableRow(width);
        drawTableHeaderCell(legendHeading, mainSink);
        mainSink.tableRow_();
        legendRow(mainSink, "X-Axis:", xAxis);
        legendRow(mainSink, "Y-Axis:", "Relative Churn");
        legendRow(mainSink, "Color:", "Priority of what to fix first");
        legendRow(mainSink, "Circle Size:", "Priority (Visual) of what to fix first");
        mainSink.tableRows_();
        mainSink.table_();
        mainSink.division_();
    }

    private static void legendRow(Sink mainSink, String boldText, String explanation) {
        mainSink.tableRow();
        mainSink.tableCell();
        mainSink.bold();
        mainSink.text(boldText);
        mainSink.bold_();
        mainSink.text(explanation);
        mainSink.tableCell_();
        mainSink.tableRow_();
    }

    void drawTableHeaderCell(String cellText, Sink mainSink) {
        mainSink.tableHeaderCell();
        mainSink.text(cellText);
        mainSink.tableHeaderCell_();
    }

    void drawTableCell(Object cellText, Sink mainSink) {
        SinkEventAttributeSet align = new SinkEventAttributeSet();
        if (cellText instanceof Integer || cellText instanceof Instant) {
            align.addAttribute(SinkEventAttributes.ALIGN, "right");
        } else {
            align.addAttribute(SinkEventAttributes.ALIGN, "left");
        }

        mainSink.tableCell(align);

        if (cellText instanceof Instant) {
            mainSink.text(formatter.format((Instant) cellText));
        } else {
            mainSink.text(cellText.toString());
        }

        mainSink.tableCell_();
    }

    /*
    <a class="github-button" href="https://github.com/jimbethancourt/refactorfirst" data-icon="octicon-star" data-size="large" data-show-count="true" aria-label="Star jimbethancourt/refactorfirst on GitHub">Star</a>
    <a class="github-button" href="https://github.com/jimbethancourt/refactorfirst/fork" data-icon="octicon-repo-forked" data-size="large" data-show-count="true" aria-label="Fork jimbethancourt/refactorfirst on GitHub">Fork</a>
    <a class="github-button" href="https://github.com/jimbethancourt/refactorfirst/subscription" data-icon="octicon-eye" data-size="large" data-show-count="true" aria-label="Watch jimbethancourt/refactorfirst on GitHub">Watch</a>
    <a class="github-button" href="https://github.com/jimbethancourt/refactorfirst/issues" data-icon="octicon-issue-opened" data-size="large" data-show-count="false" aria-label="Issue jimbethancourt/refactorfirst on GitHub">Issue</a>
    <a class="github-button" href="https://github.com/sponsors/jimbethancourt" data-icon="octicon-heart" data-size="large" aria-label="Sponsor @jimbethancourt on GitHub">Sponsor</a>
    */
    void renderGitHubButtons(Sink mainSink) {
        SinkEventAttributeSet alignCenter = new SinkEventAttributeSet();
        alignCenter.addAttribute(SinkEventAttributes.ALIGN, "center");

        mainSink.division(alignCenter);
        mainSink.text("Show RefactorFirst some &#10084;&#65039;");
        mainSink.lineBreak();

        renderGitHubButton(
                mainSink,
                "https://github.com/refactorfirst/refactorfirst",
                "octicon-star",
                "true",
                "Star refactorfirst/refactorfirst on GitHub",
                "Star");
        renderGitHubButton(
                mainSink,
                "https://github.com/refactorfirst/refactorfirst/fork",
                "octicon-repo-forked",
                "true",
                "Fork refactorfirst/refactorfirst on GitHub",
                "Fork");
        renderGitHubButton(
                mainSink,
                "https://github.com/refactorfirst/refactorfirst/subscription",
                "octicon-eye",
                "true",
                "Watch refactorfirst/refactorfirst on GitHub",
                "Watch");
        renderGitHubButton(
                mainSink,
                "https://github.com/refactorfirst/refactorfirst/issue",
                "octicon-issue-opened",
                "false",
                "Issue refactorfirst/refactorfirst on GitHub",
                "Issue");
        renderGitHubButton(
                mainSink,
                "https://github.com/jimbethancourt/refactorfirst/issue",
                "octicon-heart",
                "false",
                "Sponsor @jimbethancourt on GitHub",
                "Sponsor");

        mainSink.division_();
    }

    private static void renderGitHubButton(
            Sink mainSink,
            String url,
            String dataIconValue,
            String dataShowCount,
            String ariaLabel,
            String anchorText) {
        SinkEventAttributeSet starButton = new SinkEventAttributeSet();
        starButton.addAttribute(SinkEventAttributes.HREF, url);
        starButton.addAttribute("class", "github-button");
        starButton.addAttribute("data-icon", dataIconValue);
        starButton.addAttribute("data-size", "large");
        starButton.addAttribute("data-show-count", dataShowCount);
        starButton.addAttribute("aria-label", ariaLabel);
        mainSink.unknown("a", new Object[] {HtmlMarkup.TAG_TYPE_START}, starButton);
        mainSink.text(anchorText);
        mainSink.unknown("a", new Object[] {HtmlMarkup.TAG_TYPE_END}, null);
    }

    // TODO: Move to another class to allow use by Gradle plugin
    void writeGodClassGchartJs(List<RankedDisharmony> rankedDisharmonies, int maxPriority) {
        GraphDataGenerator graphDataGenerator = new GraphDataGenerator();
        String scriptStart = graphDataGenerator.getGodClassScriptStart();
        String bubbleChartData = graphDataGenerator.generateGodClassBubbleChartData(rankedDisharmonies, maxPriority);
        String scriptEnd = graphDataGenerator.getGodClassScriptEnd();

        String javascriptCode = scriptStart + bubbleChartData + scriptEnd;

        String reportOutputDirectory = project.getModel().getReporting().getOutputDirectory();
        File reportOutputDir = new File(reportOutputDirectory);
        if (!reportOutputDir.exists()) {
            reportOutputDir.mkdirs();
        }
        String pathname = reportOutputDirectory + File.separator + "godClassChart.js";

        File scriptFile = new File(pathname);
        try {
            scriptFile.createNewFile();
        } catch (IOException e) {
            log.error("Failure creating God Class chart script file", e);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(scriptFile))) {
            writer.write(javascriptCode);
        } catch (IOException e) {
            log.error("Error writing chart script file", e);
        }
    }

    void writeGCBOGchartJs(List<RankedDisharmony> rankedDisharmonies, int maxPriority) {
        GraphDataGenerator graphDataGenerator = new GraphDataGenerator();
        String scriptStart = graphDataGenerator.getCBOScriptStart();
        String bubbleChartData = graphDataGenerator.generateCBOBubbleChartData(rankedDisharmonies, maxPriority);
        String scriptEnd = graphDataGenerator.getCBOScriptEnd();

        String javascriptCode = scriptStart + bubbleChartData + scriptEnd;

        String reportOutputDirectory = project.getModel().getReporting().getOutputDirectory();
        File reportOutputDir = new File(reportOutputDirectory);
        if (!reportOutputDir.exists()) {
            reportOutputDir.mkdirs();
        }
        String pathname = reportOutputDirectory + File.separator + "cboChart.js";

        File scriptFile = new File(pathname);
        try {
            scriptFile.createNewFile();
        } catch (IOException e) {
            log.error("Failure creating CBO chart script file", e);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(scriptFile))) {
            writer.write(javascriptCode);
        } catch (IOException e) {
            log.error("Error writing CBO chart script file", e);
        }
    }
}
