package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.mustachejava.Mustache;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pins the classpath template engine contract used by the Mustache report phases. */
class TemplateEngineTest {

    private final TemplateEngine engine = new TemplateEngine();

    private static final String TEMPLATE_RESOURCE = "templates/refactor-first-report.mustache";

    /** Renders interpolated values from the bundled classpath template. */
    @Test
    void rendersInterpolatedValuesFromTheBundledClasspathTemplate() throws Exception {
        // Given: a view model with nested project metadata
        Map<String, Object> project = new HashMap<>();
        project.put("name", "TestProject");
        project.put("version", "1.0.0");
        Map<String, Object> view = new HashMap<>();
        view.put("project", project);

        // When: the bundled template is rendered
        String rendered = engine.render(TEMPLATE_RESOURCE, view);

        // Then: the interpolated values appear in the output
        assertTrue(rendered.contains("<title>Refactor First Report for TestProject 1.0.0</title>"), rendered);
        assertTrue(rendered.contains("Refactor First Report for TestProject 1.0.0"));
    }

    /** HTML-escapes every interpolated value by default, so injected markup cannot break out. */
    @Test
    void escapesInterpolatedValuesByDefault() throws Exception {
        // Given: a project name carrying markup
        Map<String, Object> project = new HashMap<>();
        project.put("name", "A <script>alert(1)</script>");
        Map<String, Object> view = new HashMap<>();
        view.put("project", project);

        // When: the bundled template is rendered
        String rendered = engine.render(TEMPLATE_RESOURCE, view);

        // Then: the markup is HTML-escaped, never passed through raw
        assertTrue(rendered.contains("A &lt;script&gt;alert(1)&lt;/script&gt;"), rendered);
        assertFalse(rendered.contains("<script>alert(1)</script>"), rendered);
    }

    /** Renders raw triple-mustache values unescaped (the escape hatch for pre-rendered HTML). */
    @Test
    void rendersRawTripleMustacheValuesWithoutEscaping() throws Exception {
        // Given: a view model with a raw value
        Map<String, Object> view = new HashMap<>();
        view.put("raw", "<b>Bold</b>");

        // When: a small inline template with a raw section is rendered through the engine
        String rendered = engine.renderInline("Hello {{{raw}}}", view);

        // Then: the raw value passes through unescaped
        assertEquals("Hello <b>Bold</b>", rendered);
    }

    /** Compiles the same template name to the same cached instance on repeated loads. */
    @Test
    void cachesCompiledTemplatesByName() {
        // When: the same template is loaded twice
        Mustache first = engine.load(TEMPLATE_RESOURCE);
        Mustache second = engine.load(TEMPLATE_RESOURCE);

        // Then: the compiled template is cached per name
        assertNotNull(first);
        assertSame(first, second);
    }

    /**
     * Renders the implicit-iterator and parent-scope dotted-lookup subset the simple-report
     * template relies on (P3/T3.4; mirrored by {@code mustache.dual-engine.test.cjs}).
     */
    @Test
    void rendersImplicitIteratorsAndParentScopeDottedLookups() throws Exception {
        // Given: string-list headers and a repo url at the view root
        Map<String, Object> view = new HashMap<>();
        Map<String, Object> project = new HashMap<>();
        project.put("repoUrl", "https://github.com/test/test/blob/");
        view.put("project", project);
        view.put("headers", java.util.List.of("Class", "Priority"));
        view.put("rows", java.util.List.of(Map.of("path", "src/main/java/TestClass.java")));

        // When: an inline template mirrors the simple-report table subset
        String rendered = engine.renderInline(
                "{{#headers}}<th scope=\"col\">{{.}}</th>{{/headers}}"
                        + "{{#rows}}{{#path}}<a href=\"{{project.repoUrl}}{{path}}\">{{/path}}x{{#path}}</a>{{/path}}{{/rows}}",
                view);

        // Then: the implicit iterator emits the list items and the nested section resolves the parent scope
        assertEquals(
                "<th scope=\"col\">Class</th><th scope=\"col\">Priority</th>"
                        + "<a href=\"https://github.com/test/test/blob/src/main/java/TestClass.java\">x</a>",
                rendered);
    }
}
