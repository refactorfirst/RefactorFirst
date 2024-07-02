package org.hjug.refactorfirst.report.json;

import static org.hjug.refactorfirst.report.ReportWriter.writeReportToDisk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hjug.cbc.CostBenefitCalculator;
import org.hjug.cbc.RankedDisharmony;

@Slf4j
public class JsonReportExecutor {

    private static final String FILE_NAME = "refactor-first-data.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public void execute(File baseDir, String outputDirectory) {
        String projectBaseDir;

        if (baseDir != null) {
            projectBaseDir = baseDir.getPath();
        } else {
            projectBaseDir = Paths.get("").toAbsolutePath().toString();
        }

        final CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator(projectBaseDir);
        try {
            costBenefitCalculator.runPmdAnalysis();
        } catch (IOException e) {
            log.error("Error running PMD analysis.");
            throw new RuntimeException(e);
        }
        final List<RankedDisharmony> rankedDisharmonies = costBenefitCalculator.calculateGodClassCostBenefitValues();
        final List<JsonReportDisharmonyEntry> disharmonyEntries = rankedDisharmonies.stream()
                .map(JsonReportDisharmonyEntry::fromRankedDisharmony)
                .collect(Collectors.toList());

        final JsonReport report =
                JsonReport.builder().rankedDisharmonies(disharmonyEntries).build();

        try {
            final String reportJson = MAPPER.writeValueAsString(report);

            writeReportToDisk(outputDirectory, FILE_NAME, new StringBuilder(reportJson));
        } catch (final JsonProcessingException jsonProcessingException) {
            final String errorMessage = "Could not generate a json report: " + jsonProcessingException;

            log.error(errorMessage);
            final JsonReport errorReport = JsonReport.builder()
                    .errors(new ArrayList<>(Collections.singletonList(errorMessage)))
                    .build();

            writeErrorReport(errorReport, outputDirectory);
        }
    }

    private void writeErrorReport(final JsonReport errorReport, String outputDirectory) {
        try {
            writeReportToDisk(outputDirectory, FILE_NAME, new StringBuilder(MAPPER.writeValueAsString(errorReport)));
        } catch (final JsonProcessingException jsonProcessingException) {
            log.error("failed to write error report: ", jsonProcessingException);
        }
    }
}
