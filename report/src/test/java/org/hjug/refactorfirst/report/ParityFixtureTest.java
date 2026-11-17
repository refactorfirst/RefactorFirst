package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.InputStream;
import java.util.List;
import org.hjug.refactorfirst.report.model.CycleSummaryDTO;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
import org.junit.jupiter.api.Test;

/**
 * Phase P0/T0.4 parity fixture consumer: loads the page-repo-published report
 * fixture ({@code tests/fixtures/junit4-report.json} copied to
 * {@code report/src/test/resources/parity/}) through the Java DTO pipeline and
 * asserts the round trip, anchoring later phases (P2 view models, P9 parity
 * specs) to real report data.
 */
class ParityFixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            // The page-repo fixture carries fields the current DTO does not model
            // yet (e.g. sourceUrl/targetUrl on class relationships); the P0 round-trip
            // fixture consumer tolerates them until the DTO grows the fields.
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void loadsParityFixtureIntoDto() throws Exception {
        RefactorFirstReportDTO dto = loadDto();

        assertEquals("JUnit", dto.getProject().getName());
        assertTrue(dto.getProject().isHasAnyDisharmony(), "fixture project should have findings");
        assertTrue(dto.getClassMap().isHasEdges() == false, "class map carries a hierarchy, not raw edges");
        assertTrue(dto.getClassMap().isDotThresholdExceeded() == false, "small fixture graph stays under threshold");
        assertTrue(dto.getClassMap().getHierarchy() != null, "class map must carry a hierarchy");
        assertTrue(dto.getClassMap().getHierarchy().getPackages() != null);
        assertTrue(dto.getClassMap().getHierarchy().getEdges() != null);

        List<?> relationships = dto.getClassRelationshipsToRemove().getRelationships();
        assertTrue(relationships != null);
    }

    @Test
    void roundTripsParityFixtureThroughJackson() throws Exception {
        RefactorFirstReportDTO dto = loadDto();
        String json = objectMapper.writeValueAsString(dto);
        RefactorFirstReportDTO parsed = objectMapper.readValue(json, RefactorFirstReportDTO.class);

        assertEquals(dto.getProject().getName(), parsed.getProject().getName());
        assertEquals(dto.getClassMap().getClassCount(), parsed.getClassMap().getClassCount());
        assertEquals(dto.getDisharmonies().size(), parsed.getDisharmonies().size());

        List<CycleSummaryDTO> summaries = parsed.getClassCycles().getSummary();
        assertTrue(summaries != null, "fixture should carry cycle summaries when cycles exist");
        assertEquals(
                dto.getClassCycles().getSummary() == null
                        ? 0
                        : dto.getClassCycles().getSummary().size(),
                summaries == null ? 0 : summaries.size());
    }

    private RefactorFirstReportDTO loadDto() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/parity/junit4-report.json")) {
            if (is == null) {
                throw new IllegalStateException("Parity fixture not found in classpath: /parity/junit4-report.json");
            }
            return objectMapper.readValue(is, RefactorFirstReportDTO.class);
        }
    }
}
