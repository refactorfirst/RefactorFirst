package org.hjug.refactorfirst.report;

import static org.hjug.refactorfirst.report.ReportWriter.writeReportToDisk;

import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.CostBenefitCalculator;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.git.GitLogReader;

@Slf4j
public class CsvReport {

    public void execute(
            boolean showDetails, String projectName, String projectVersion, String outputDirectory, File baseDir) {
        StringBuilder fileNameSB = new StringBuilder();
        String publishedDate = createFileDateTimeFormatter().format(Instant.now());

        fileNameSB
                .append(getOutputNamePrefix())
                .append("_P")
                .append(projectName)
                .append("_PV")
                .append(projectVersion)
                .append("_PD")
                .append(publishedDate)
                .append(".csv");
        String filename = fileNameSB.toString();

        if (Objects.equals(projectName, "Maven Stub Project (No POM)")) {
            projectName = new File(Paths.get("").toAbsolutePath().toString()).getName();
        }

        log.info("Generating {} for {} - {} date: {}", filename, projectName, projectVersion, publishedDate);

        StringBuilder contentBuilder = new StringBuilder();

        // git management
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
            contentBuilder
                    .append("No Git repository found in project ")
                    .append(projectName)
                    .append(" ")
                    .append(projectVersion)
                    .append(". ");
            contentBuilder.append("Please initialize a Git repository and perform an initial commit.");
            writeReportToDisk(outputDirectory, filename, contentBuilder);
            return;
        }

        String parentOfGitDir = gitDir.getParentFile().getPath();
        log.info("Project Base Dir: {} ", projectBaseDir);
        log.info("Parent of Git Dir: {}", parentOfGitDir);

        if (!projectBaseDir.equals(parentOfGitDir)) {
            log.warn("Project Base Directory does not match Git Parent Directory");
            contentBuilder.append("Project Base Directory does not match Git Parent Directory.  "
                    + "Please refer to the report at the root of the site directory.");
            return;
        }

        // actual calcualte
        List<RankedDisharmony> rankedDisharmonies;
        try (CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator(projectBaseDir)) {
            costBenefitCalculator.runPmdAnalysis();
            rankedDisharmonies = costBenefitCalculator.calculateGodClassCostBenefitValues();
        } catch (Exception e) {
            log.error("Error running analysis.");
            throw new RuntimeException(e);
        }

        rankedDisharmonies.sort(Comparator.comparing(RankedDisharmony::getPriority));

        // perfect score: no god classes
        if (rankedDisharmonies.isEmpty()) {
            contentBuilder
                    .append("Congratulations!  ")
                    .append(projectName)
                    .append(" ")
                    .append(projectVersion)
                    .append(" has no God classes!");
            log.info("Done! No God classes found!");

            writeReportToDisk(outputDirectory, filename, contentBuilder);
            return;
        }

        // create Content
        // header
        final String[] tableHeadings = getHeaderList(showDetails);
        addsRow(contentBuilder, tableHeadings);
        contentBuilder.append("\n");
        // rows
        for (RankedDisharmony rankedDisharmony : rankedDisharmonies) {
            final String[] rankedDisharmonyData = getDataList(rankedDisharmony, showDetails);

            contentBuilder.append(projectVersion).append(",");
            addsRow(contentBuilder, rankedDisharmonyData);
            contentBuilder.append("eol" + "\n");
        }

        log.info(contentBuilder.toString());

        writeReportToDisk(outputDirectory, filename, contentBuilder);
    }

    private DateTimeFormatter createFileDateTimeFormatter() {
        return DateTimeFormatter.ofPattern("yyyyMMddhhmm")
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault());
    }

    private DateTimeFormatter createCsvDateTimeFormatter() {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault());
    }

    private String[] getDataList(RankedDisharmony rankedDisharmony, boolean showDetails) {
        String[] simpleRankedDisharmonyData = {
            rankedDisharmony.getFileName(),
            rankedDisharmony.getPriority().toString(),
            rankedDisharmony.getChangePronenessRank().toString(),
            rankedDisharmony.getEffortRank().toString(),
            rankedDisharmony.getWmc().toString(),
            createCsvDateTimeFormatter().format(rankedDisharmony.getMostRecentCommitTime()),
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
            createCsvDateTimeFormatter().format(rankedDisharmony.getFirstCommitTime()),
            createCsvDateTimeFormatter().format(rankedDisharmony.getMostRecentCommitTime()),
            rankedDisharmony.getCommitCount().toString(),
            rankedDisharmony.getPath()
        };

        return showDetails ? detailedRankedDisharmonyData : simpleRankedDisharmonyData;
    }

    private String[] getHeaderList(boolean showDetails) {

        final String[] simpleTableHeadings = {
            "Ver",
            "Class",
            "Priority",
            "Change Proneness Rank",
            "Effort Rank",
            "Method Count",
            "Most Recent Commit Date",
            "Commit Count"
        };

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
            "Full Path"
        };

        return showDetails ? detailedTableHeadings : simpleTableHeadings;
    }

    private void addsRow(StringBuilder contentBuilder, String[] rankedDisharmonyData) {
        for (String rowData : rankedDisharmonyData) {
            contentBuilder.append(rowData).append(",");
        }
    }

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
}
