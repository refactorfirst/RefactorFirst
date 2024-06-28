package org.hjug.refactorfirst.report;

import static org.hjug.refactorfirst.report.ReportWriter.writeReportToDisk;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.CostBenefitCalculator;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.git.GitLogReader;

/**
 * Strictly HTML report that contains no JavaScript
 * Generates only tables
 */
@Slf4j
public class SimpleHtmlReport {

    public static final String THE_BEGINNING =
            "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\n" + "  <head>\n"
                    + "    <meta charset=\"UTF-8\" />\n"
                    + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n"
                    + "    <meta name=\"generator\" content=\"Apache Maven Doxia Site Renderer 1.9.2\" />";

    public static final String THE_END = "</div>\n" + "    </div>\n"
            /*+ "    <div class=\"clear\">\n"
            + "      <hr/>\n"
            + "    </div>\n"
            + "    <div id=\"footer\">\n"
            + "      <div class=\"xright\">\n"
            + "        Copyright &#169;      2002&#x2013;2021<a href=\"https://www.apache.org/\">The Apache Software Foundation</a>.\n"
            + ".      </div>\n"
            + "      <div class=\"clear\">\n"
            + "        <hr/>\n"
            + "      </div>\n"
            + "    </div>\n"*/
            + "  </body>\n"
            + "</html>\n";

    public final String[] godClassSimpleTableHeadings = {
        "Class",
        "Priority",
        "Change Proneness Rank",
        "Effort Rank",
        "Method Count",
        "Most Recent Commit Date",
        "Commit Count"
    };

    public final String[] godClassDetailedTableHeadings = {
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

    public final String[] cboTableHeadings = {
        "Class", "Priority", "Change Proneness Rank", "Coupling Count", "Most Recent Commit Date", "Commit Count"
    };

    public void execute(
            boolean showDetails, String projectName, String projectVersion, String outputDirectory, File baseDir) {

        final String[] godClassTableHeadings =
                showDetails ? godClassDetailedTableHeadings : godClassSimpleTableHeadings;

        String filename = getOutputName() + ".html";

        log.info("Generating {} for {} - {}", filename, projectName, projectVersion);

        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault());

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(THE_BEGINNING);

        printTitle(projectName, projectVersion, stringBuilder);
        printHead(stringBuilder);
        printBreadcrumbs(stringBuilder);
        printProjectHeader(projectName, projectVersion, stringBuilder, formatter);

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
        try {
            costBenefitCalculator.runPmdAnalysis(projectBaseDir);
        } catch (IOException e) {
            log.error("Error running PMD analysis.");
            throw new RuntimeException(e);
        }
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
            renderGithubButtons(stringBuilder);
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
            int maxGodClassPriority = rankedGodClassDisharmonies
                    .get(rankedGodClassDisharmonies.size() - 1)
                    .getPriority();

            stringBuilder.append("<div style=\"text-align: center;\"><a id=\"GOD\"><h1>God Classes</h1></a></div>");

            renderGodClassChart(outputDirectory, rankedGodClassDisharmonies, maxGodClassPriority, stringBuilder);

            stringBuilder.append(
                    "<h2 align=\"center\">God classes by the numbers: (Refactor Starting with Priority 1)</h2>");
            stringBuilder.append("<table align=\"center\" border=\"5px\">");

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
                    drawTableCell(rowData, stringBuilder);
                }

                stringBuilder.append("</tr>");
            }

            stringBuilder.append("</tbody>");
            stringBuilder.append("</table>");
        }

        if (!rankedCBODisharmonies.isEmpty()) {

            stringBuilder.append("<br/>\n" + "<br/>\n" + "<br/>\n" + "<br/>\n" + "<hr/>\n" + "<br/>\n" + "<br/>");

            stringBuilder.append(
                    "<div style=\"text-align: center;\"><a id=\"CBO\"><h1>Highly Coupled Classes</h1></a></div>");

            int maxCboPriority =
                    rankedCBODisharmonies.get(rankedCBODisharmonies.size() - 1).getPriority();

            renderCBOChart(outputDirectory, rankedCBODisharmonies, maxCboPriority, stringBuilder);

            stringBuilder.append(
                    "<h2 align=\"center\">Highly Coupled classes by the numbers: (Refactor starting with Priority 1)</h2>");
            stringBuilder.append("<table align=\"center\" border=\"5px\">");

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
                    drawTableCell(rowData, stringBuilder);
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

    void drawTableCell(String rowData, StringBuilder stringBuilder) {
        if (isNumber(rowData) || isDateTime(rowData)) {
            stringBuilder.append("<td align=\"right\">").append(rowData).append("</td>");
        } else {
            stringBuilder.append("<td align=\"left\">").append(rowData).append("</td>");
        }
    }

    boolean isNumber(String rowData) {
        return rowData.matches("-?\\d+(\\.\\d+)?");
    }

    boolean isDateTime(String rowData) {
        return rowData.contains(", ");
    }

    public void printTitle(String projectName, String projectVersion, StringBuilder stringBuilder) {
        // empty on purpose
    }

    public void printHead(StringBuilder stringBuilder) {
        // empty on purpose
    }

    public void printBreadcrumbs(StringBuilder stringBuilder) {
        stringBuilder.append("  <body class=\"composite\">\n"
                + "    <div id=\"banner\">\n"
                + "      <div class=\"clear\">\n"
                + "        <hr/>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "    <div id=\"breadcrumbs\">\n"
                + "      <div class=\"xleft\">");
    }

    public void printProjectHeader(
            String projectName, String projectVersion, StringBuilder stringBuilder, DateTimeFormatter formatter) {
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
    }

    void renderGithubButtons(StringBuilder stringBuilder) {
        // empty on purpose
    }

    String getOutputName() {
        // This report will generate simple-report.html when invoked in a project with `mvn site`
        return "refactor-first-report";
    }

    void renderGodClassChart(
            String outputDirectory,
            List<RankedDisharmony> rankedGodClassDisharmonies,
            int maxGodClassPriority,
            StringBuilder stringBuilder) {
        // empty on purpose
    }

    void writeGodClassGchartJs(
            List<RankedDisharmony> rankedDisharmonies, int maxPriority, String reportOutputDirectory) {
        // empty on purpose
    }

    void writeGCBOGchartJs(List<RankedDisharmony> rankedDisharmonies, int maxPriority, String reportOutputDirectory) {
        // empty on purpose
    }

    void renderCBOChart(
            String outputDirectory,
            List<RankedDisharmony> rankedCBODisharmonies,
            int maxCboPriority,
            StringBuilder stringBuilder) {
        // empty on purpose
    }
}
