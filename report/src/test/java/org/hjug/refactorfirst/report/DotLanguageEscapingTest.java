package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hjug.refactorfirst.report.model.GraphVisualDTO;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
import org.junit.jupiter.api.Test;

class DotLanguageEscapingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Verifies that JSON round-trips DOT quotes and line breaks. */
    @Test
    void testDotDigraphWithQuotesAndNewlines() throws Exception {
        String rawDot = "strict digraph G {\n"
                + "  ActiveTestSuite -> TestSuite [ label = \"6\" weight = \"6\" ];\n"
                + "  ActiveTestSuite -> TestCase [ label = \"2\" weight = \"2\" ];\n"
                + "}";

        GraphVisualDTO classMap = GraphVisualDTO.builder()
                .graphId("classGraph")
                .classCount(3)
                .relationshipCount(2)
                .dot(rawDot)
                .build();

        RefactorFirstReportDTO report =
                RefactorFirstReportDTO.builder().classMap(classMap).build();

        String json = objectMapper.writeValueAsString(report);

        // Verify JSON escapes newlines and quotes properly
        assertTrue(json.contains("\\\"6\\\""));
        assertTrue(json.contains("\\n"));

        // Verify deserialization restores the exact raw DOT string
        RefactorFirstReportDTO deserialized = objectMapper.readValue(json, RefactorFirstReportDTO.class);
        assertEquals(rawDot, deserialized.getClassMap().getDot());
    }

    /** Verifies that JSON preserves Java inner-class dollar signs in DOT. */
    @Test
    void testDotDigraphWithJavaInnerClassDollarSign() throws Exception {
        String rawDot = "strict digraph G {\n"
                + "  Outer_Inner [ label=\"Outer\\$Inner\" ];\n"
                + "  Outer_1 [ label=\"Outer\\$1\" color=red style=filled ];\n"
                + "  Outer -> Outer_Inner [ label = \"1\" weight = \"1\" ];\n"
                + "}";

        GraphVisualDTO classMap =
                GraphVisualDTO.builder().graphId("classGraph").dot(rawDot).build();

        RefactorFirstReportDTO report =
                RefactorFirstReportDTO.builder().classMap(classMap).build();

        String json = objectMapper.writeValueAsString(report);
        // Backslash in raw string is escaped as \\ in JSON
        assertTrue(json.contains("Outer\\\\$Inner"));
        assertTrue(json.contains("Outer\\\\$1"));

        RefactorFirstReportDTO deserialized = objectMapper.readValue(json, RefactorFirstReportDTO.class);
        assertEquals(rawDot, deserialized.getClassMap().getDot());
    }

    /** Verifies that JSON preserves Kotlin anonymous-class labels in DOT. */
    @Test
    void testDotDigraphWithKotlinAnonymousLiteral() throws Exception {
        String rawDot = "strict digraph G {\n"
                + "  DeveloperWASDControl_anonymous [ label=\"DeveloperWASDControl\\$anonymous\" ];\n"
                + "  lt_anonymous_gt [ label=\"<anonymous>\" ];\n"
                + "  DeveloperWASDControl -> DeveloperWASDControl_anonymous [ label = \"1\" weight = \"1\" ];\n"
                + "}";

        GraphVisualDTO classMap =
                GraphVisualDTO.builder().graphId("classGraph").dot(rawDot).build();

        RefactorFirstReportDTO report =
                RefactorFirstReportDTO.builder().classMap(classMap).build();

        String json = objectMapper.writeValueAsString(report);
        assertTrue(json.contains("DeveloperWASDControl\\\\$anonymous"));
        assertTrue(json.contains("<anonymous>"));

        RefactorFirstReportDTO deserialized = objectMapper.readValue(json, RefactorFirstReportDTO.class);
        assertEquals(rawDot, deserialized.getClassMap().getDot());
    }

    /** Verifies that JSON round-trips DOT hyperlink attributes. */
    @Test
    void testDotDigraphWithHyperlinkAttributes() throws Exception {
        String rawDot = "strict digraph G {\n"
                + "  A [URL=\"https://github.com/refactorfirst/RefactorFirst/blob/src/A.java\" target=\"_blank\"];\n"
                + "  B [URL=\"https://github.com/refactorfirst/RefactorFirst/blob/src/B.java\" target=\"_blank\"];\n"
                + "  A -> B [ label = \"2\" weight = \"2\" color = \"red\" ];\n"
                + "}";

        GraphVisualDTO classMap =
                GraphVisualDTO.builder().graphId("classGraph").dot(rawDot).build();

        RefactorFirstReportDTO report =
                RefactorFirstReportDTO.builder().classMap(classMap).build();

        String json = objectMapper.writeValueAsString(report);
        assertTrue(json.contains("URL=\\\"https://github.com/refactorfirst/RefactorFirst/blob/src/A.java\\\""));
        assertTrue(json.contains("target=\\\"_blank\\\""));

        RefactorFirstReportDTO deserialized = objectMapper.readValue(json, RefactorFirstReportDTO.class);
        assertEquals(rawDot, deserialized.getClassMap().getDot());
    }
}
