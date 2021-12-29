package org.hjug.mavenreport;

import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.hjug.cbc.CostBenefitCalculator;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.git.GitLogReader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Mojo(
        name = "csvreport",
        defaultPhase = LifecyclePhase.SITE,
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        requiresProject = true,
        threadSafe = true,
        inheritByDefault = false
)
public class RefactorFirstMavenCsvReport extends AbstractMojo {

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

    public String getOutputNamePrefix() {
        // This report will generate simple-report.html when invoked in a project with `mvn site`
        return "RefFirst";
    }

    public String getName(Locale locale) {
        // Name of the report when listed in the project-reports.html page of a project
        return "Refactor First Report data";
    }

    public String getDescription(Locale locale) {
        // Description of the report when listed in the project-reports.html page of a project
        return "DRACO Ranks the disharmonies in a codebase.  The classes that should be refactored first "
                + " have the highest priority values.";
    }

    @Override
    public void execute() {
        StringBuilder fileNameSB = new StringBuilder();
        String publishedDate = createFileDateTimeFormatter().format(Instant.now());

        fileNameSB
                .append(getOutputNamePrefix())
                .append("_P")
                .append(project.getArtifactId())
                .append("_PV")
                .append(project.getVersion())
                .append("_PD")
                .append(publishedDate)
                .append(".csv");
        String filename = fileNameSB.toString();

        log.info("Generating {} for {} - {} date: {}", filename, projectName, projectVersion, publishedDate );

        StringBuilder contentBuilder = new StringBuilder();

        // git management
        GitLogReader gitLogReader = new GitLogReader();
        String projectBaseDir = project.getBasedir().getPath();
        Optional<File> optionalGitDir = Optional.ofNullable(gitLogReader.getGitDir(project.getBasedir()));
        File gitDir;

        if (optionalGitDir.isPresent()) {
            gitDir = optionalGitDir.get();
        } else {
            log.info("Done! No Git repository found!  Please initialize a Git repository and perform an initial commit.");
            contentBuilder.append("No Git repository found in project ")
                    .append(projectName).append(" ")
                    .append(projectVersion).append(". ");
            contentBuilder.append("Please initialize a Git repository and perform an initial commit.");
            writeReportToDisk(filename, contentBuilder);
            return;
        }

        String parentOfGitDir = gitDir.getParentFile().getPath();
        log.info("Project Base Dir: {} ", projectBaseDir);
        log.info("Parent of Git Dir: {}", parentOfGitDir);

        if(!projectBaseDir.equals(parentOfGitDir)) {
            log.warn("Project Base Directory does not match Git Parent Directory");
            contentBuilder.append(
                    "Project Base Directory does not match Git Parent Directory.  " +
                    "Please refer to the report at the root of the site directory.");
            return;
        }

        // actual calcualte
        CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator();
        List<RankedDisharmony> rankedDisharmonies = costBenefitCalculator.calculateCostBenefitValues(projectBaseDir);

        rankedDisharmonies.sort(Comparator.comparing(RankedDisharmony::getPriority).reversed());

        // perfect score: no god classes
        if(rankedDisharmonies.isEmpty()) {
            contentBuilder.append("Congratulations!  ")
                    .append(projectName).append(" ")
                    .append(projectVersion).append(" has no God classes!");
            log.info("Done! No God classes found!");

            writeReportToDisk(filename, contentBuilder);
            return;
        }

        // create Content
        // header
        final String[] tableHeadings = getHeaderList();
        addsRow(contentBuilder, tableHeadings);
        contentBuilder.append("\n");
        // rows
        for (RankedDisharmony rankedDisharmony : rankedDisharmonies) {
            final String[] rankedDisharmonyData = getDataList(rankedDisharmony);

            contentBuilder.append(project.getVersion()).append(",");
            addsRow(contentBuilder, rankedDisharmonyData);
            contentBuilder.append("eol" + "\n");
        }

        log.info(contentBuilder.toString());

        writeReportToDisk(filename, contentBuilder);
    }

    private DateTimeFormatter createFileDateTimeFormatter() {
        return DateTimeFormatter.ofPattern("yyyyMMddhhmm")
                .withLocale( Locale.getDefault() )
                .withZone( ZoneId.systemDefault() );
    }

    private DateTimeFormatter createCsvDateTimeFormatter() {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME
                .withLocale( Locale.getDefault() )
                .withZone( ZoneId.systemDefault() );
    }


    private String[] getDataList(RankedDisharmony rankedDisharmony ) {
        String[] simpleRankedDisharmonyData = { rankedDisharmony.getClassName(),
                rankedDisharmony.getPriority().toString(),
                rankedDisharmony.getChangePronenessRank().toString(),
                rankedDisharmony.getEffortRank().toString(),
                rankedDisharmony.getWmc().toString(),
                createCsvDateTimeFormatter().format(rankedDisharmony.getMostRecentCommitTime()),
                rankedDisharmony.getCommitCount().toString()};

        String[] detailedRankedDisharmonyData = {  rankedDisharmony.getClassName(),
                rankedDisharmony.getPriority().toString(),
                rankedDisharmony.getChangePronenessRank().toString(),
                rankedDisharmony.getEffortRank().toString(),
                rankedDisharmony.getWmc().toString(),
                rankedDisharmony.getWmcRank().toString(),
                rankedDisharmony.getAtfd().toString(),
                rankedDisharmony.getAtfdRank().toString(),
                rankedDisharmony.getTcc().toString(),
                rankedDisharmony.getTccRank().toString(),
                createCsvDateTimeFormatter().format(rankedDisharmony.getFirstCommitTime()),
                createCsvDateTimeFormatter().format(rankedDisharmony.getMostRecentCommitTime()),
                rankedDisharmony.getCommitCount().toString(),
                rankedDisharmony.getPath()};


        return showDetails ? detailedRankedDisharmonyData : simpleRankedDisharmonyData;
    }

    private String[] getHeaderList() {

        final String[] simpleTableHeadings = {
                "Ver",
                "Class",
                "Priority",
                "Change Proneness Rank",
                "Effort Rank",
                "Method Count",
                "Most Recent Commit Date",
                "Commit Count"};

        final String[] detailedTableHeadings = {
                "Ver",
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
                "Full Path"};

        return showDetails ? detailedTableHeadings : simpleTableHeadings;
    }

    private void addsRow(StringBuilder contentBuilder, String[] rankedDisharmonyData) {
        for (String rowData : rankedDisharmonyData) {
            contentBuilder.append(rowData).append(",");
        }
    }

    private void writeReportToDisk(String filename, StringBuilder stringBuilder) {
        String reportOutputDirectory = project.getModel().getReporting().getOutputDirectory();
        File reportOutputDir = new File(reportOutputDirectory);
        if(!reportOutputDir.exists()) {
            reportOutputDir.mkdirs();
        }
        String pathname = reportOutputDirectory + File.separator + filename;

        File reportFile = new File(pathname);
        try {
            reportFile.createNewFile();
        } catch (IOException e) {
            log.error("Failure creating chart script file", e);
        }

        try(BufferedWriter writer = new BufferedWriter(new FileWriter(reportFile))) {
            writer.write(stringBuilder.toString());
        } catch (IOException e) {
            log.error("Error writing chart script file", e);
        }

        log.info("Done! View the report at target/site/{}", filename);
    }
}
