package org.hjug.refactorfirst.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;

/**
 * Builds the interactive report's rf-data payload (plan D3): the serialized
 * {@link RefactorFirstReportDTO} the page repo's viewers consume. Since P7 the graphs
 * synthesize client-side from the inline {@code hierarchy} payloads via rf-hierarchy-dot.js
 * (byte-identical to the Java NestedDotBuilder, cross-pinned by hierarchy-dot.test.cjs) —
 * the report embeds no raw DOT. The JSON is escaped for inline
 * {@code <script type="application/json" id="rf-data">} embedding.
 */
public class RfDataBlock {

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Serializes the DTO into the escaped rf-data JSON payload. */
    public String build(RefactorFirstReportDTO report) throws Exception {
        return new DataEmbedder().escapeForHtmlScript(objectMapper.writeValueAsString(report));
    }
}
