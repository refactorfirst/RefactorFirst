package org.hjug.refactorfirst.report;

import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.CostBenefitCalculator;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.gdg.GraphDataGenerator;
import org.hjug.git.GitLogReader;

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

import static org.hjug.refactorfirst.report.ReportWriter.writeReportToDisk;

@Slf4j
public class HtmlReport {

    public static final String THE_BEGINNING =
            "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\n" + "  <head>\n"
                    + "    <meta charset=\"UTF-8\" />\n"
                    + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n"
                    + "    <meta name=\"generator\" content=\"Apache Maven Doxia Site Renderer 1.9.2\" />";

    public static final String GOD_CLASS_CHART_LEGEND =
            "       <h2>God Class Chart Legend:</h2>" + "       <table border=\"5px\">\n"
                    + "          <tbody>\n"
                    + "            <tr><td><strong>X-Axis:</strong> Effort to refactor to a non-God class</td></tr>\n"
                    + "            <tr><td><strong>Y-Axis:</strong> Relative churn</td></tr>\n"
                    + "            <tr><td><strong>Color:</strong> Priority of what to fix first</td></tr>\n"
                    + "            <tr><td><strong>Circle size:</strong> Priority (Visual) of what to fix first</td></tr>\n"
                    + "          </tbody>\n"
                    + "        </table>"
                    + "        <br/>";

    public static final String COUPLING_BETWEEN_OBJECT_CHART_LEGEND =
            "       <h2>Coupling Between Objects Chart Legend:</h2>" + "       <table border=\"5px\">\n"
                    + "          <tbody>\n"
                    + "            <tr><td><strong>X-Axis:</strong> Number of objects the class is coupled to</td></tr>\n"
                    + "            <tr><td><strong>Y-Axis:</strong> Relative churn</td></tr>\n"
                    + "            <tr><td><strong>Color:</strong> Priority of what to fix first</td></tr>\n"
                    + "            <tr><td><strong>Circle size:</strong> Priority (Visual) of what to fix first</td></tr>\n"
                    + "          </tbody>\n"
                    + "        </table>"
                    + "        <br/>";

    public static final String THE_END = "</div>\n" + "    </div>\n"
            + "    <div class=\"clear\">\n"
            + "      <hr/>\n"
            + "    </div>\n"
            + "    <div id=\"footer\">\n"
            + "      <div class=\"xright\">\n"
            + "        Copyright &#169;      2002&#x2013;2021<a href=\"https://www.apache.org/\">The Apache Software Foundation</a>.\n"
            + ".      </div>\n"
            + "      <div class=\"clear\">\n"
            + "        <hr/>\n"
            + "      </div>\n"
            + "    </div>\n"
            + "  </body>\n"
            + "</html>\n";

    public void execute(boolean showDetails, String projectName, String projectVersion, String outputDirectory, File baseDir) {

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

        String filename = getOutputName() + ".html";

        log.info("Generating {} for {} - {}", filename, projectName, projectVersion);

        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault());

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(THE_BEGINNING);

        stringBuilder
                .append("<title>Refactor First Report for ")
                .append(projectName)
                .append(" ")
                .append(projectVersion)
                .append(" </title>");

        stringBuilder.append("<link rel=\"stylesheet\" href=\"./css/maven-base.css\" />\n"
                + "    <link rel=\"stylesheet\" href=\"./css/maven-theme.css\" />\n"
                + "    <link rel=\"stylesheet\" href=\"./css/site.css\" />\n"
                + "    <link rel=\"stylesheet\" href=\"./css/print.css\" media=\"print\" />\n"
                + "<script type=\"text/javascript\" src=\"https://www.gstatic.com/charts/loader.js\">"
                + "</script><script type=\"text/javascript\" src=\"./gchart.js\"></script>"
                + "<script type=\"text/javascript\" src=\"./gchart2.js\"></script>"
                + "  </head>\n"
                + "  <body class=\"composite\">\n"
                + "    <div id=\"banner\">\n"
                + "      <div class=\"clear\">\n"
                + "        <hr/>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "    <div id=\"breadcrumbs\">\n"
                + "      <div class=\"xleft\">");

        stringBuilder
                .append("<span id=\"publishDate\">Last Published: ")
                .append(formatter.format(Instant.now()))
                .append("</span>");
        stringBuilder
                .append("<span id=\"projectVersion\"> Version: ")
                .append(projectVersion)
                .append("</span>");

        stringBuilder.append("</div>\n" + "      <div class=\"xright\">      </div>\n"
                + "      <div class=\"clear\">\n"
                + "        <hr/>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "    <div id=\"bodyColumn\">\n"
                + "      <div id=\"contentBox\">");

        stringBuilder
                .append("<section>\n" + "<h2>RefactorFirst Report for ")
                .append(projectName)
                .append(" ")
                .append(projectVersion)
                .append("</h2>\n");

        GitLogReader gitLogReader = new GitLogReader();
        String projectBaseDir;
        Optional<File> optionalGitDir;

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
            stringBuilder
                    .append("No Git repository found in project ")
                    .append(projectName)
                    .append(" ")
                    .append(projectVersion)
                    .append(".  ");
            stringBuilder.append("Please initialize a Git repository and perform an initial commit.");
            stringBuilder.append(THE_END);
            writeReportToDisk(outputDirectory, filename, stringBuilder);
            return;
        }

        String parentOfGitDir = gitDir.getParentFile().getPath();
        log.info("Project Base Dir: {} ", projectBaseDir);
        log.info("Parent of Git Dir: {}", parentOfGitDir);

        if (!projectBaseDir.equals(parentOfGitDir)) {
            log.warn("Project Base Directory does not match Git Parent Directory");
            stringBuilder.append("Project Base Directory does not match Git Parent Directory.  "
                    + "Please refer to the report at the root of the site directory.");
            stringBuilder.append(THE_END);
            return;
        }

        CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator();
        List<RankedDisharmony> rankedGodClassDisharmonies =
                costBenefitCalculator.calculateGodClassCostBenefitValues(projectBaseDir);

        List<RankedDisharmony> rankedCBODisharmonies =
                costBenefitCalculator.calculateCBOCostBenefitValues(projectBaseDir);

        if (rankedGodClassDisharmonies.isEmpty() && rankedCBODisharmonies.isEmpty()) {
            stringBuilder
                    .append("Congratulations!  ")
                    .append(projectName)
                    .append(" ")
                    .append(projectVersion)
                    .append(" has no God classes or highly coupled classes!");
            log.info("Done! No Disharmonies found!");
            stringBuilder.append(THE_END);
            writeReportToDisk(outputDirectory, filename, stringBuilder);
            return;
        }

        if (!rankedGodClassDisharmonies.isEmpty() && !rankedCBODisharmonies.isEmpty()) {
            stringBuilder.append("<a href=\"#GOD\">God Classes</a>");
            stringBuilder.append("<br/>");
            stringBuilder.append("<a href=\"#CBO\">Highly Coupled Classes</a>");
        }

        if (!rankedGodClassDisharmonies.isEmpty()) {
            rankedGodClassDisharmonies.sort(
                    Comparator.comparing(RankedDisharmony::getRawPriority).reversed());

            int godClassPriority = 1;
            for (RankedDisharmony rankedGodClassDisharmony : rankedGodClassDisharmonies) {
                rankedGodClassDisharmony.setPriority(godClassPriority++);
            }

            stringBuilder.append("<div style=\"text-align: center;\"><a id=\"GOD\"><h1>God Classes</h1></a></div>");

            writeGodClassGchartJs(rankedGodClassDisharmonies, godClassPriority - 1, outputDirectory);
            stringBuilder.append("<div id=\"series_chart_div\"></div>");
            stringBuilder.append(GOD_CLASS_CHART_LEGEND);

            stringBuilder.append("<h2>God classes by the numbers: (Refactor Starting with Priority 1)</h2>");
            stringBuilder.append("<table border=\"5px\" class=\"table table-striped\">");

            // Content
            stringBuilder.append("<thead><tr>");
            for (String heading : godClassTableHeadings) {
                stringBuilder.append("<th>").append(heading).append("</th>");
            }
            stringBuilder.append("</tr></thead>");

            stringBuilder.append("<tbody>");
            for (RankedDisharmony rankedGodClassDisharmony : rankedGodClassDisharmonies) {
                stringBuilder.append("<tr>");

                String[] simpleRankedGodClassDisharmonyData = {
                        rankedGodClassDisharmony.getFileName(),
                        rankedGodClassDisharmony.getPriority().toString(),
                        rankedGodClassDisharmony.getChangePronenessRank().toString(),
                        rankedGodClassDisharmony.getEffortRank().toString(),
                        rankedGodClassDisharmony.getWmc().toString(),
                        formatter.format(rankedGodClassDisharmony.getMostRecentCommitTime()),
                        rankedGodClassDisharmony.getCommitCount().toString()
                };

                String[] detailedRankedGodClassDisharmonyData = {
                        rankedGodClassDisharmony.getFileName(),
                        rankedGodClassDisharmony.getPriority().toString(),
                        rankedGodClassDisharmony.getRawPriority().toString(),
                        rankedGodClassDisharmony.getChangePronenessRank().toString(),
                        rankedGodClassDisharmony.getEffortRank().toString(),
                        rankedGodClassDisharmony.getWmc().toString(),
                        rankedGodClassDisharmony.getWmcRank().toString(),
                        rankedGodClassDisharmony.getAtfd().toString(),
                        rankedGodClassDisharmony.getAtfdRank().toString(),
                        rankedGodClassDisharmony.getTcc().toString(),
                        rankedGodClassDisharmony.getTccRank().toString(),
                        formatter.format(rankedGodClassDisharmony.getFirstCommitTime()),
                        formatter.format(rankedGodClassDisharmony.getMostRecentCommitTime()),
                        rankedGodClassDisharmony.getCommitCount().toString(),
                        rankedGodClassDisharmony.getPath()
                };

                final String[] rankedDisharmonyData =
                        showDetails ? detailedRankedGodClassDisharmonyData : simpleRankedGodClassDisharmonyData;

                for (String rowData : rankedDisharmonyData) {
                    stringBuilder.append("<td>").append(rowData).append("</td>");
                }

                stringBuilder.append("</tr>");
            }

            stringBuilder.append("</tbody>");
            stringBuilder.append("</table>");
        }

        if (!rankedCBODisharmonies.isEmpty()) {

            stringBuilder.append("<br/>\n" + "<br/>\n" + "<br/>\n" + "<br/>\n" + "<hr/>\n" + "<br/>\n" + "<br/>");

            rankedCBODisharmonies.sort(
                    Comparator.comparing(RankedDisharmony::getRawPriority).reversed());

            int cboPriority = 1;
            for (RankedDisharmony rankedCBODisharmony : rankedCBODisharmonies) {
                rankedCBODisharmony.setPriority(cboPriority++);
            }

            stringBuilder.append(
                    "<div style=\"text-align: center;\"><a id=\"CBO\"><h1>Highly Coupled Classes</h1></a></div>");

            stringBuilder.append("<div id=\"series_chart_div_2\"></div>");
            writeGCBOGchartJs(rankedCBODisharmonies, cboPriority - 1, outputDirectory);
            stringBuilder.append(COUPLING_BETWEEN_OBJECT_CHART_LEGEND);

            stringBuilder.append("<h2>Highly Coupled classes by the numbers: (Refactor starting with Priority 1)</h2>");
            stringBuilder.append("<table border=\"5px\" class=\"table table-striped\">");

            // Content
            stringBuilder.append("<thead><tr>");
            for (String heading : cboTableHeadings) {
                stringBuilder.append("<th>").append(heading).append("</th>");
            }
            stringBuilder.append("</tr></thead>");

            stringBuilder.append("<tbody>");
            for (RankedDisharmony rankedCboClassDisharmony : rankedCBODisharmonies) {
                stringBuilder.append("<tr>");

                String[] rankedCboClassDisharmonyData = {
                        rankedCboClassDisharmony.getFileName(),
                        rankedCboClassDisharmony.getPriority().toString(),
                        rankedCboClassDisharmony.getChangePronenessRank().toString(),
                        rankedCboClassDisharmony.getEffortRank().toString(),
                        formatter.format(rankedCboClassDisharmony.getMostRecentCommitTime()),
                        rankedCboClassDisharmony.getCommitCount().toString()
                };

                for (String rowData : rankedCboClassDisharmonyData) {
                    stringBuilder.append("<td>").append(rowData).append("</td>");
                }

                stringBuilder.append("</tr>");
            }

            stringBuilder.append("</tbody>");
        }

        stringBuilder.append("</table></section>");
        stringBuilder.append(THE_END);

        log.debug(stringBuilder.toString());

        writeReportToDisk(outputDirectory, filename, stringBuilder);
        log.info("Done! View the report at target/site/{}", filename);
    }

    // TODO: Move to another class to allow use by Gradle plugin
    void writeGodClassGchartJs(List<RankedDisharmony> rankedDisharmonies, int maxPriority, String reportOutputDirectory) {
        GraphDataGenerator graphDataGenerator = new GraphDataGenerator();
        String scriptStart = graphDataGenerator.getGodClassScriptStart();
        String bubbleChartData = graphDataGenerator.generateGodClassBubbleChartData(rankedDisharmonies, maxPriority);
        String scriptEnd = graphDataGenerator.getGodClassScriptEnd();

        String javascriptCode = scriptStart + bubbleChartData + scriptEnd;

        File reportOutputDir = new File(reportOutputDirectory);
        if (!reportOutputDir.exists()) {
            reportOutputDir.mkdirs();
        }
        String pathname = reportOutputDirectory + File.separator + "gchart.js";

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

    void writeGCBOGchartJs(List<RankedDisharmony> rankedDisharmonies, int maxPriority, String reportOutputDirectory) {
        GraphDataGenerator graphDataGenerator = new GraphDataGenerator();
        String scriptStart = graphDataGenerator.getCBOScriptStart();
        String bubbleChartData = graphDataGenerator.generateCBOBubbleChartData(rankedDisharmonies, maxPriority);
        String scriptEnd = graphDataGenerator.getCBOScriptEnd();

        String javascriptCode = scriptStart + bubbleChartData + scriptEnd;

        File reportOutputDir = new File(reportOutputDirectory);
        if (!reportOutputDir.exists()) {
            reportOutputDir.mkdirs();
        }
        String pathname = reportOutputDirectory + File.separator + "gchart2.js";

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
}
