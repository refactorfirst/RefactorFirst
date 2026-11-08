package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Dual-engine golden for the Mustache phases: the fixture view model rendered by the Java
 * mustache engine must equal the same fixture view model rendered by the Node mustache.js engine
 * byte for byte (see the P2 plan, T2.3).
 */
class DualEngineGoldenTest {

    private final TemplateEngine templateEngine = new TemplateEngine();

    private static final String TEMPLATE_RESOURCE = "templates/refactor-first-report.mustache";
    private static final String GOLDEN_RESOURCE = "golden/mustache/refactor-first-report.mustache.html";

    @DisplayName("Java and mustache.js render the fixture view model byte-identically")
    @Test
    void javaEngineAndMustacheJsRenderTheFixtureViewModelByteIdentically() throws Exception {
        // Given: a fixture report DTO translated into the view model
        Map<String, Object> view = viewModel();

        // When: the Java engine renders the bundled template
        String rendered = templateEngine.render(TEMPLATE_RESOURCE, view);

        // Then: the output matches the cross-language golden byte for byte
        assertEquals(readGolden(), rendered);
    }

    /** Reads the cross-language golden DOT-adjacent mustache fixture. */
    private String readGolden() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/" + GOLDEN_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing mustache golden resource: /" + GOLDEN_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Builds the small fixture report DTO the golden renders against. */
    private Map<String, Object> viewModel() {
        return InteractiveTemplateFixture.viewModel();
    }
}
