package org.hjug.mavenreport;

import static org.hjug.mavenreport.ReportWriter.writeReportToDisk;

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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
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
public class RefactorFirstMavenReport extends AbstractMojo {

    private static final String THE_BEGINNING =
            "<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\" lang=\"en\">\n" + "  <head>\n"
                    + "    <meta charset=\"UTF-8\" />\n"
                    + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n"
                    + "    <meta name=\"generator\" content=\"Apache Maven Doxia Site Renderer 1.9.2\" />";

    private static final String LEGEND = "       <h2>Chart Legend:</h2>" + "       <table border=\"5px\">\n"
            + "          <tbody>\n"
            + "            <tr><td><strong>X-Axis:</strong> Effort to refactor to a non-God class</td></tr>\n"
            + "            <tr><td><strong>Y-Axis:</strong> Relative churn</td></tr>\n"
            + "            <tr><td><strong>Color:</strong> Rank of what to fix first</td></tr>\n"
            + "            <tr><td><strong>Circle size:</strong> Number of non-getter/setter methods</td></tr>\n"
            + "          </tbody>\n"
            + "        </table>"
            + "        <br/>";

    private static final String THE_END = "</div>\n" + "    </div>\n"
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

    @Parameter(property = "showDetails")
    private boolean showDetails = false;

    @Parameter(defaultValue = "${project.name}")
    private String projectName;

    @Parameter(defaultValue = "${project.version}")
    private String projectVersion;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    @Parameter(property = "project.build.directory")
    protected File outputDirectory;

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
    public void execute() {

        final String[] simpleTableHeadings = {
            "Class",
            "Priority",
            "Change Proneness Rank",
            "Effort Rank",
            "Method Count",
            "Most Recent Commit Date",
            "Commit Count"
        };

        final String[] detailedTableHeadings = {
            "Class",
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
            "Most Recent Commit Date",
            "Commit Count",
            "Full Path"
        };

        final String[] tableHeadings = showDetails ? detailedTableHeadings : simpleTableHeadings;

        String filename = getOutputName() + ".html";

        if (Objects.equals(project.getName(), "Maven Stub Project (No POM)")) {
            projectName = new File(Paths.get("").toAbsolutePath().toString()).getName();
        }

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
                + "</script><script type=\"text/javascript\" src=\"./gchart.js\"></script>  </head>\n"
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
                + "    <div id=\"leftColumn\">\n"
                + "      <div id=\"navcolumn\">\n"
                + "      <a href=\"http://maven.apache.org/\" title=\"Built by Maven\" class=\"poweredBy\">\n"
                + "        <img class=\"poweredBy\" alt=\"Built by Maven\" src=\"./images/logos/maven-feather.png\" />\n"
                + "      </a>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "    <div id=\"bodyColumn\">\n"
                + "      <div id=\"contentBox\">");

        stringBuilder
                .append("<section>\n" + "<h2>God Class Report for ")
                .append(projectName)
                .append(" ")
                .append(projectVersion)
                .append("</h2>\n")
                .append("<div id=\"series_chart_div\"></div>");

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
            stringBuilder
                    .append("No Git repository found in project ")
                    .append(projectName)
                    .append(" ")
                    .append(projectVersion)
                    .append(".  ");
            stringBuilder.append("Please initialize a Git repository and perform an initial commit.");
            stringBuilder.append(THE_END);
            writeReportToDisk(project, filename, stringBuilder);
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
        List<RankedDisharmony> rankedDisharmonies = costBenefitCalculator.calculateCostBenefitValues(projectBaseDir);

        rankedDisharmonies.sort(
                Comparator.comparing(RankedDisharmony::getPriority).reversed());

        if (rankedDisharmonies.isEmpty()) {
            stringBuilder
                    .append("Congratulations!  ")
                    .append(projectName)
                    .append(" ")
                    .append(projectVersion)
                    .append(" has no God classes!");
            log.info("Done! No God classes found!");
            stringBuilder.append(THE_END);
            writeReportToDisk(project, filename, stringBuilder);
            return;
        }

        writeGchartJs(rankedDisharmonies);
        stringBuilder.append(LEGEND);

        stringBuilder.append("<h2>God classes by the numbers: (Refactor higher priority classes first)</h2>");
        stringBuilder.append("<table border=\"5px\" class=\"table table-striped\">");

        // Content
        stringBuilder.append("<thead><tr>");
        for (String heading : tableHeadings) {
            stringBuilder.append("<th>").append(heading).append("</th>");
        }
        stringBuilder.append("</tr></thead>");

        stringBuilder.append("<tbody>");
        for (RankedDisharmony rankedDisharmony : rankedDisharmonies) {
            stringBuilder.append("<tr>");

            String[] simpleRankedDisharmonyData = {
                rankedDisharmony.getFileName(),
                rankedDisharmony.getPriority().toString(),
                rankedDisharmony.getChangePronenessRank().toString(),
                rankedDisharmony.getEffortRank().toString(),
                rankedDisharmony.getWmc().toString(),
                formatter.format(rankedDisharmony.getMostRecentCommitTime()),
                rankedDisharmony.getCommitCount().toString()
            };

            String[] detailedRankedDisharmonyData = {
                rankedDisharmony.getFileName(),
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
                rankedDisharmony.getPath()
            };

            final String[] rankedDisharmonyData =
                    showDetails ? detailedRankedDisharmonyData : simpleRankedDisharmonyData;

            for (String rowData : rankedDisharmonyData) {
                stringBuilder.append("<td>").append(rowData).append("</td>");
            }

            stringBuilder.append("</tr>");
        }

        stringBuilder.append("</tbody>");

        log.info("Done! View the report at target/site/{}", filename);

        stringBuilder.append("</table></section>");
        stringBuilder.append(THE_END);

        log.info(stringBuilder.toString());

        writeReportToDisk(project, filename, stringBuilder);
    }

    // TODO: Move to another class to allow use by Gradle plugin
    void writeGchartJs(List<RankedDisharmony> rankedDisharmonies) {
        GraphDataGenerator graphDataGenerator = new GraphDataGenerator();
        String scriptStart = graphDataGenerator.getScriptStart();
        String bubbleChartData = graphDataGenerator.generateBubbleChartData(rankedDisharmonies);
        String scriptEnd = graphDataGenerator.getScriptEnd();

        String javascriptCode = scriptStart + bubbleChartData + scriptEnd;

        String reportOutputDirectory = project.getModel().getReporting().getOutputDirectory();
        File reportOutputDir = new File(reportOutputDirectory);
        if (!reportOutputDir.exists()) {
            reportOutputDir.mkdirs();
        }
        String pathname = reportOutputDirectory + File.separator + "gchart.js";

        File scriptFile = new File(pathname);
        try {
            scriptFile.createNewFile();
        } catch (IOException e) {
            log.error("Failure creating chart script file", e);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(scriptFile))) {
            writer.write(javascriptCode);
        } catch (IOException e) {
            log.error("Error writing chart script file", e);
        }
    }
}
