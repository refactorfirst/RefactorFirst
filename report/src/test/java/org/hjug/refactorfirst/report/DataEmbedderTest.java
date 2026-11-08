package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.hjug.refactorfirst.report.model.ClassCyclesDTO;
import org.hjug.refactorfirst.report.model.ClassRelationshipDTO;
import org.hjug.refactorfirst.report.model.ClassRelationshipsToRemoveDTO;
import org.hjug.refactorfirst.report.model.GraphVisualDTO;
import org.hjug.refactorfirst.report.model.PackageRelationshipsToRemoveDTO;
import org.hjug.refactorfirst.report.model.ProjectMetadataDTO;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pins the HTML-script data embedding contract: JSON out, script-safe JSON in, round-trip equality out. */
class DataEmbedderTest {

    private final DataEmbedder dataEmbedder = new DataEmbedder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @DisplayName("Embedded report JSON escapes </ sequences so the payload cannot close a script block")
    @Test
    void embedsJsonWithClosingTagSafeEscaping() throws Exception {
        // Given: a serialized report whose class name carries a script-closing sequence
        RefactorFirstReportDTO report = fixtureReport();
        String json = objectMapper.writeValueAsString(report);

        // When: the payload is embedded for inline HTML script use
        String embedded = dataEmbedder.embed(report);

        // Then: `</` is escaped as `<\/` and the raw closing tag cannot appear
        assertTrue(
                embedded.contains("<\\/script>"), "embedded payload should carry the <\\/script> escape: " + embedded);
        assertFalse(embedded.contains("</script>"), "embedded payload must not carry a raw closing tag");
        assertFalse(embedded.contains("</"), "embedded payload must not carry a raw </ sequence");
    }

    @DisplayName("Embedded report JSON escapes Unicode line separators U+2028 and U+2029")
    @Test
    void embedsJsonWithUnicodeLineSeparatorEscaping() throws Exception {
        // Given: a serialized report containing Unicode line separators
        RefactorFirstReportDTO report = fixtureReport();
        String json = objectMapper.writeValueAsString(report);
        assertTrue(json.contains("\u2028"), "fixture JSON should carry a raw U+2028");

        // When: the payload is embedded for inline HTML script use
        String embedded = dataEmbedder.embed(report);

        // Then: U+2028/U+2029 become JSON \u2028 / \u2029 escapes
        assertTrue(embedded.contains("\\u2028"), "embedded payload should carry the \\u2028 escape");
        assertTrue(embedded.contains("\\u2029"), "embedded payload should carry the \\u2029 escape");
    }

    @DisplayName("Embedded report JSON round-trips through JSON parsing preserving hostile class names")
    @Test
    void embeddedJsonRoundTripsThroughJsonParsing() throws Exception {
        // Given: a fixture DTO whose class names contain script closers, backticks and template
        // interpolation markers (the Kotlin-style hostile naming from the Kotlin report fixture)
        RefactorFirstReportDTO report = fixtureReport();
        String embedded = dataEmbedder.embed(report);

        // When: the embedded payload is parsed back into a report DTO
        RefactorFirstReportDTO parsed = objectMapper.readValue(embedded, RefactorFirstReportDTO.class);

        // Then: the hostile strings survive the escape/encode/decode cycle unchanged
        ClassRelationshipDTO relationship =
                parsed.getClassRelationshipsToRemove().getRelationships().get(0);
        assertEquals("com.example.`Weird${Owner}</script>\u2028\u2029", relationship.getSourceClass());
        assertEquals("com.example.`Weird${Owner}</script>\u2028\u2029", relationship.getSimpleSourceClassName());
        assertEquals("com.example.KotlinThing", relationship.getTargetClass());
    }

    /** Builds a report whose relationship names include script closers, backticks and interpolation markers. */
    private RefactorFirstReportDTO fixtureReport() {
        ProjectMetadataDTO project = ProjectMetadataDTO.builder()
                .name("TestProject")
                .version("1.0.0")
                .repoUrl("https://github.com/test/test/blob/")
                .baseDir("/test")
                .scanTimestamp("9/8/26, 7:34 PM")
                .hasAnyDisharmony(true)
                .analysisFailed(false)
                .build();

        ClassRelationshipDTO relationship = ClassRelationshipDTO.builder()
                .sourceClass("com.example.`Weird${Owner}</script>\u2028\u2029")
                .targetClass("com.example.KotlinThing")
                .sourceClassPath("src/main/kotlin/com/example/Wierd.kt")
                .targetClassPath("src/main/kotlin/com/example/KotlinThing.kt")
                .simpleSourceClassName("com.example.`Weird${Owner}</script>\u2028\u2029")
                .simpleTargetClassName("KotlinThing")
                .sourceMarked(true)
                .targetMarked(false)
                .weight(5)
                .priority(1)
                .cycleCount(3)
                .effortRank(2)
                .build();

        ClassRelationshipsToRemoveDTO classRels = ClassRelationshipsToRemoveDTO.builder()
                .cycleCount(1)
                .relationshipsToRemoveCount(1)
                .hasRelationships(true)
                .relationships(List.of(relationship))
                .build();

        return RefactorFirstReportDTO.builder()
                .project(project)
                .classMap(GraphVisualDTO.builder()
                        .graphId("classGraph")
                        .classCount(10)
                        .relationshipCount(20)
                        .dotThreshold(4000)
                        .dotThresholdExceeded(false)
                        .build())
                .classRelationshipsToRemove(classRels)
                .packageMap(GraphVisualDTO.builder().hasEdges(false).build())
                .packageRelationshipsToRemove(PackageRelationshipsToRemoveDTO.builder()
                        .cycleCount(0)
                        .relationshipsToRemoveCount(0)
                        .hasRelationships(false)
                        .build())
                .hasDisharmonies(true)
                .disharmonies(List.of())
                .classCycles(ClassCyclesDTO.builder().hasCycles(false).build())
                .build();
    }
}
