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
import org.hjug.cbc.RankedCycle;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.gdg.GraphDataGenerator;
import org.hjug.git.GitLogReader;
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

    public final String[] classCycleTableHeadings = {"Classes", "Relationships"};

    private Graph<String, DefaultWeightedEdge> classGraph;

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

        List<RankedDisharmony> rankedGodClassDisharmonies;
        List<RankedDisharmony> rankedCBODisharmonies;
        List<RankedCycle> rankedCycles;
        try (CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator(projectBaseDir)) {
            costBenefitCalculator.runPmdAnalysis();
            rankedGodClassDisharmonies = costBenefitCalculator.calculateGodClassCostBenefitValues();
            rankedCBODisharmonies = costBenefitCalculator.calculateCBOCostBenefitValues();
            rankedCycles = runCycleAnalysis(costBenefitCalculator, outputDirectory.getPath());
            classGraph = costBenefitCalculator.getClassReferencesGraph();
        } catch (Exception e) {
            log.error("Error running analysis.");
            throw new RuntimeException(e);
        }

        if (rankedGodClassDisharmonies.isEmpty() && rankedCBODisharmonies.isEmpty() && rankedCycles.isEmpty()) {
            mainSink.text("Contratulations!  " + projectName + " " + projectVersion
                    + " has no God classes, highly coupled classes, or cycles!");
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
            int maxGodClassPriority = rankedGodClassDisharmonies
                    .get(rankedGodClassDisharmonies.size() - 1)
                    .getPriority();

            SinkEventAttributeSet alignCenter = new SinkEventAttributeSet();
            alignCenter.addAttribute(SinkEventAttributes.ALIGN, "center");

            mainSink.division(alignCenter);
            mainSink.section2();
            mainSink.sectionTitle2();
            mainSink.text("God Classes");
            mainSink.sectionTitle2_();
            mainSink.section2_();
            mainSink.division_();

            String godClassScript = writeGodClassGchartJs(rankedGodClassDisharmonies, maxGodClassPriority - 1);
            SinkEventAttributeSet seriesChartDiv = new SinkEventAttributeSet();
            seriesChartDiv.addAttribute(SinkEventAttributes.ID, "series_chart_div");
            seriesChartDiv.addAttribute(SinkEventAttributes.ALIGN, "center");
            mainSink.division(seriesChartDiv);
            mainSink.division_();

            SinkEventAttributeSet godClassJavascript = new SinkEventAttributeSet();
            godClassJavascript.addAttribute(SinkEventAttributes.TYPE, "text/javascript");
            mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_START}, godClassJavascript);
            mainSink.rawText(godClassScript);
            mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_END}, null);

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

            int maxCboPriority =
                    rankedCBODisharmonies.get(rankedCBODisharmonies.size() - 1).getPriority();

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

            String cboScript = writeGCBOGchartJs(rankedCBODisharmonies, maxCboPriority - 1);

            SinkEventAttributeSet cboJavascript = new SinkEventAttributeSet();
            cboJavascript.addAttribute(SinkEventAttributes.TYPE, "text/javascript");
            mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_START}, cboJavascript);
            mainSink.rawText(cboScript);
            mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_END}, null);

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

        if (!rankedCycles.isEmpty()) {
            mainSink.lineBreak();
            mainSink.lineBreak();
            mainSink.horizontalRule();
            mainSink.lineBreak();
            mainSink.lineBreak();

            renderCycles(outputDirectory.getPath(), mainSink, rankedCycles, formatter);
        }

        // Close
        mainSink.section1_();
        mainSink.body_();

        log.info("Done! View the report at target/site/{}", filename);
    }

    public List<RankedCycle> runCycleAnalysis(CostBenefitCalculator costBenefitCalculator, String outputDirectory) {
        return costBenefitCalculator.runCycleAnalysis();
    }

    private void renderCycles(
            String outputDirectory, Sink mainSink, List<RankedCycle> rankedCycles, DateTimeFormatter formatter) {

        SinkEventAttributeSet alignCenter = new SinkEventAttributeSet();
        alignCenter.addAttribute(SinkEventAttributes.ALIGN, "center");

        mainSink.division(alignCenter);
        mainSink.section1();
        mainSink.sectionTitle1();
        mainSink.text("Class Cycles");
        mainSink.sectionTitle1_();
        mainSink.section1_();
        mainSink.division_();

        mainSink.division(alignCenter);
        mainSink.section2();
        mainSink.sectionTitle2();
        mainSink.text("Class Cycles by the numbers: (Refactor starting with Priority 1)");
        mainSink.sectionTitle2_();
        mainSink.section2_();
        mainSink.division_();

        mainSink.paragraph(alignCenter);
        mainSink.text("Note: often only one minimum cut relationship needs to be removed");
        mainSink.paragraph_();

        mainSink.table();
        mainSink.tableRows(new int[] {Sink.JUSTIFY_LEFT}, true);

        // Content
        // header row

        String[] cycleTableHeadings;
        if (showDetails) {
            cycleTableHeadings = new String[] {
                "Cycle Name", "Priority", "Change Proneness Rank", "Class Count", "Relationship Count", "Minimum Cuts"
            };
        } else {
            cycleTableHeadings =
                    new String[] {"Cycle Name", "Priority", "Class Count", "Relationship Count", "Minimum Cuts"};
        }

        mainSink.tableRow();
        for (String heading : cycleTableHeadings) {
            drawTableHeaderCell(heading, mainSink);
        }
        mainSink.tableRow_();

        for (RankedCycle rankedCycle : rankedCycles) {
            mainSink.tableRow();

            StringBuilder edgesToCut = new StringBuilder();
            for (DefaultWeightedEdge minCutEdge : rankedCycle.getMinCutEdges()) {
                edgesToCut.append(minCutEdge + ":" + (int) classGraph.getEdgeWeight(minCutEdge));
            }

            String[] rankedCycleData;
            if (showDetails) {
                // "Cycle Name", "Priority", "Change Proneness Rank", "Class Count", "Relationship Count", "Min Cuts"
                rankedCycleData = new String[] {
                    rankedCycle.getCycleName(),
                    rankedCycle.getPriority().toString(),
                    rankedCycle.getChangePronenessRank().toString(),
                    String.valueOf(rankedCycle.getCycleNodes().size()),
                    String.valueOf(rankedCycle.getEdgeSet().size()),
                    edgesToCut.toString()
                };
            } else {
                // "Cycle Name", "Priority", "Class Count", "Relationship Count", "Min Cuts"
                rankedCycleData = new String[] {
                    rankedCycle.getCycleName(),
                    rankedCycle.getPriority().toString(),
                    String.valueOf(rankedCycle.getCycleNodes().size()),
                    String.valueOf(rankedCycle.getEdgeSet().size()),
                    edgesToCut.toString()
                };
            }

            for (String rowData : rankedCycleData) {
                drawCycleTableCell(rowData, mainSink);
            }

            mainSink.tableRow_();
        }
        mainSink.tableRows_();

        mainSink.table_();

        for (RankedCycle rankedCycle : rankedCycles) {
            renderCycle(outputDirectory, mainSink, rankedCycle, formatter);
        }
    }

    private void renderCycle(String outputDirectory, Sink mainSink, RankedCycle cycle, DateTimeFormatter formatter) {

        mainSink.lineBreak();
        mainSink.lineBreak();
        mainSink.lineBreak();
        mainSink.lineBreak();
        mainSink.lineBreak();

        SinkEventAttributeSet alignCenter = new SinkEventAttributeSet();
        alignCenter.addAttribute(SinkEventAttributes.ALIGN, "center");

        mainSink.division(alignCenter);
        mainSink.section2();
        mainSink.sectionTitle2();
        mainSink.text("Class Cycle : " + cycle.getCycleName());
        mainSink.sectionTitle2_();
        mainSink.section2_();
        mainSink.division_();

        renderCycleImage(classGraph, cycle, mainSink);

        mainSink.division(alignCenter);
        mainSink.bold();
        mainSink.text("\"*\" indicates relationship(s) to remove to decompose cycle");
        mainSink.bold_();
        mainSink.division_();

        mainSink.table();
        mainSink.tableRows(new int[] {Sink.JUSTIFY_LEFT}, true);

        // Content
        mainSink.tableRow();
        for (String heading : classCycleTableHeadings) {
            drawTableHeaderCell(heading, mainSink);
        }
        mainSink.tableRow_();

        for (String vertex : cycle.getVertexSet()) {
            mainSink.tableRow();
            drawTableCell(vertex, mainSink);
            StringBuilder edges = new StringBuilder();
            for (org.jgrapht.graph.DefaultWeightedEdge edge : cycle.getEdgeSet()) {
                if (edge.toString().startsWith("(" + vertex + " :")) {
                    if (cycle.getMinCutEdges().contains(edge)) {
                        edges.append(edge);
                        edges.append(":")
                                .append((int) classGraph.getEdgeWeight(edge))
                                .append("*");
                    } else {
                        edges.append(edge);
                        edges.append(":").append((int) classGraph.getEdgeWeight(edge));
                    }
                }
            }
            drawCycleTableCell(edges.toString(), mainSink);
            mainSink.tableRow_();
        }

        mainSink.tableRows_();
        mainSink.table_();
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

    void drawCycleTableCell(String cellText, Sink mainSink) {
        SinkEventAttributeSet align = new SinkEventAttributeSet();
        align.addAttribute(SinkEventAttributes.ALIGN, "left");

        mainSink.tableCell(align);

        for (String string : cellText.split("\\(")) {
            if (string.contains("*")) {
                mainSink.bold();
                mainSink.text("(" + string);
                mainSink.bold_();
            } else {
                if (string.contains(")")) {
                    mainSink.text("(" + string);
                } else {
                    mainSink.text(string);
                }
            }

            mainSink.lineBreak();
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

    String writeGodClassGchartJs(List<RankedDisharmony> rankedDisharmonies, int maxPriority) {
        GraphDataGenerator graphDataGenerator = new GraphDataGenerator();
        String scriptStart = graphDataGenerator.getGodClassScriptStart();
        String bubbleChartData = graphDataGenerator.generateGodClassBubbleChartData(rankedDisharmonies, maxPriority);
        String scriptEnd = graphDataGenerator.getGodClassScriptEnd();

        return scriptStart + bubbleChartData + scriptEnd;
    }

    String writeGCBOGchartJs(List<RankedDisharmony> rankedDisharmonies, int maxPriority) {
        GraphDataGenerator graphDataGenerator = new GraphDataGenerator();
        String scriptStart = graphDataGenerator.getCBOScriptStart();
        String bubbleChartData = graphDataGenerator.generateCBOBubbleChartData(rankedDisharmonies, maxPriority);
        String scriptEnd = graphDataGenerator.getCBOScriptEnd();

        return scriptStart + bubbleChartData + scriptEnd;
    }

    void renderCycleImage(Graph<String, DefaultWeightedEdge> classGraph, RankedCycle cycle, Sink mainSink) {

        SinkEventAttributeSet graphDivAttrs = new SinkEventAttributeSet();
        graphDivAttrs.addAttribute(SinkEventAttributes.ALIGN, "center");
        graphDivAttrs.addAttribute(SinkEventAttributes.ID, cycle.getCycleName());
        graphDivAttrs.addAttribute(SinkEventAttributes.STYLE, "border: thin solid black");

        mainSink.division(graphDivAttrs);
        mainSink.division_();

        String dot = buildDot(classGraph, cycle);

        StringBuilder d3chart = new StringBuilder();
        d3chart.append("d3.select(\"#" + cycle.getCycleName() + "\")\n");
        d3chart.append(".graphviz()\n");
        d3chart.append(".width(screen.width - 300)\n");
        d3chart.append(".height(screen.height)\n");
        d3chart.append(".fit(true)\n");
        d3chart.append(".renderDot(" + dot + ");\n");

        SinkEventAttributeSet dotChartScript = new SinkEventAttributeSet();
        dotChartScript.addAttribute(SinkEventAttributes.TYPE, "text/javascript");

        String script = "script";
        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_START}, dotChartScript);

        mainSink.rawText(d3chart.toString());
        mainSink.unknown(script, new Object[] {HtmlMarkup.TAG_TYPE_END}, null);

        SinkEventAttributeSet alignCenter = new SinkEventAttributeSet();
        alignCenter.addAttribute(SinkEventAttributes.ALIGN, "center");

        mainSink.paragraph(alignCenter);
        mainSink.text("Red arrows represent relationship(s) to remove to decompose cycle");
        mainSink.paragraph_();

        mainSink.lineBreak();
        mainSink.lineBreak();
    }

    String buildDot(Graph<String, DefaultWeightedEdge> classGraph, RankedCycle cycle) {
        StringBuilder dot = new StringBuilder();

        dot.append("'strict digraph G {\\n' +\n");

        // render vertices
        // e.g DownloadManager;
        for (String vertex : cycle.getVertexSet()) {
            dot.append("'");
            dot.append(vertex);
            dot.append(";\\n' +\n");
        }

        for (DefaultWeightedEdge edge : cycle.getEdgeSet()) {
            // 'DownloadManager -> Download [ label="1" color="red" ];'

            // render edge
            String[] vertexes =
                    edge.toString().replace("(", "").replace(")", "").split(":");

            String start = vertexes[0].trim();
            String end = vertexes[1].trim();

            dot.append("'");
            dot.append(start);
            dot.append(" -> ");
            dot.append(end);

            // render edge attributes
            dot.append(" [ ");
            dot.append("label = \"");
            dot.append((int) classGraph.getEdgeWeight(edge));
            dot.append("\"");

            if (cycle.getMinCutEdges().contains(edge)) {
                dot.append(" color = \"red\"");
            }

            dot.append(" ];\\n' +\n");
        }

        dot.append("'}'");

        return dot.toString();
    }
}
