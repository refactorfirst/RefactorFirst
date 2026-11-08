package org.hjug.refactorfirst.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;

/**
 * Embeds serialized report data into the generated HTML as a JSON script payload.
 *
 * <p>Phase P2 of the feature-parity plan: the escape contract that lets a
 * {@link RefactorFirstReportDTO} serialized to JSON be interpolated into an HTML
 * {@code <script>} block without allowing a hostile string to close the script early or
 * break the JavaScript/JSON source (U+2028/U+2029 line separators terminate JS string
 * literals on legacy engines).
 */
public class DataEmbedder {

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Serializes the report DTO to JSON and returns it HTML-script-safe for inline embedding. */
    public String embed(RefactorFirstReportDTO report) throws Exception {
        return escapeForHtmlScript(objectMapper.writeValueAsString(report));
    }

    /** Escapes an already-serialized JSON document for safe inline HTML script embedding. */
    public String embed(String json) {
        return escapeForHtmlScript(json);
    }

    /**
     * Returns the JSON document escaped so it can be interpolated into an HTML {@code <script>}
     * block: {@code </} becomes the JSON escape {@code <\/} (so a payload cannot close the script
     * block) and Unicode line separators U+2028/U+2029 become their JSON {@code \u2028}/\u2029}
     * escape sequences.
     */
    public String escapeForHtmlScript(String json) {
        if (json == null) {
            return null;
        }
        String escaped = json.replace("</", "<\\/");
        escaped = escaped.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
        return escaped;
    }
}
