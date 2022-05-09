package org.hjug.mavenreport.RefactorFirstJsonReport;

import static org.hjug.mavenreport.ReportWriter.writeReportToDisk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.hjug.cbc.CostBenefitCalculator;
import org.hjug.cbc.RankedDisharmony;

@Slf4j
@Mojo(
        name = "jsonreport",
        defaultPhase = LifecyclePhase.SITE,
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        requiresProject = false,
        threadSafe = true,
        inheritByDefault = false)
public class RefactorFirstMavenJsonReport extends AbstractMojo {
    private static final String FILE_NAME = "refactor-first-data.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    @Override
    public void execute() {
        String projectBaseDir;

        File baseDir = project.getBasedir();
        if (baseDir != null) {
            projectBaseDir = baseDir.getPath();
        } else {
            projectBaseDir = Paths.get("").toAbsolutePath().toString();
        }

        final CostBenefitCalculator costBenefitCalculator = new CostBenefitCalculator();
        final List<RankedDisharmony> rankedDisharmonies =
                costBenefitCalculator.calculateCostBenefitValues(projectBaseDir);
        final List<JsonReportDisharmonyEntry> disharmonyEntries = rankedDisharmonies.stream()
                .map(JsonReportDisharmonyEntry::fromRankedDisharmony)
                .collect(Collectors.toList());

        final JsonReport report =
                JsonReport.builder().rankedDisharmonies(disharmonyEntries).build();

        try {
            final String reportJson = MAPPER.writeValueAsString(report);

            writeReportToDisk(project, FILE_NAME, new StringBuilder(reportJson));
        } catch (final JsonProcessingException jsonProcessingException) {
            final String errorMessage = "Could not generate a json report: " + jsonProcessingException;

            log.error(errorMessage);
            final JsonReport errorReport = JsonReport.builder()
                    .errors(new ArrayList<>(Collections.singletonList(errorMessage)))
                    .build();

            writeErrorReport(errorReport);
        }
    }

    private void writeErrorReport(final JsonReport errorReport) {
        try {
            writeReportToDisk(project, FILE_NAME, new StringBuilder(MAPPER.writeValueAsString(errorReport)));
        } catch (final JsonProcessingException jsonProcessingException) {
            log.error("failed to write error report: ", jsonProcessingException);
        }
    }
}
