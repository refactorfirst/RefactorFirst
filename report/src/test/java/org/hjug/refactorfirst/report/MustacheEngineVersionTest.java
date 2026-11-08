package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests for the Mustache template engine used by the report pipeline
 * (plan: feature-parity-implementation-plan).
 *
 * <p>T0.1 of Phase P0 pins the engine half of the dual-engine golden strategy
 * (Java mustache.java vs the page repo's mustache.js): the compiler must resolve
 * at compile scope (pom bump 0.9.10 → 0.9.14) and render the template subset the
 * report templates rely on.
 */
class MustacheEngineVersionTest {

    private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

    @Test
    void rendersSimpleTextTemplate() throws Exception {
        Mustache mustache = mustacheFactory.compile(new StringReader("Hello {{name}}!"), "test");
        StringWriter writer = new StringWriter();
        mustache.execute(writer, java.util.Map.of("name", "World"));
        assertEquals("Hello World!", writer.toString());
    }

    @Test
    void rendersSectionsAndInvertedSections() throws Exception {
        Mustache mustache = mustacheFactory.compile(
                new StringReader("{{#items}}{{name}};{{/items}}{{^items}}none{{/items}}"), "test");
        StringWriter writer = new StringWriter();
        mustache.execute(
                writer,
                java.util.Map.of(
                        "items", java.util.List.of(java.util.Map.of("name", "a"), java.util.Map.of("name", "b"))));
        assertEquals("a;b;", writer.toString());

        StringWriter emptyWriter = new StringWriter();
        mustache.execute(emptyWriter, java.util.Map.of("items", java.util.List.of()));
        assertEquals("none", emptyWriter.toString());
    }

    @Test
    void rendersDotNotationKeysAndHtmlEscaping() throws Exception {
        Mustache mustache = mustacheFactory.compile(new StringReader("{{project.name}}"), "test");
        StringWriter writer = new StringWriter();
        mustache.execute(writer, java.util.Map.of("project", java.util.Map.of("name", "<b>Hi</b>")));
        assertEquals("&lt;b&gt;Hi&lt;/b&gt;", writer.toString());

        Mustache sectioned =
                mustacheFactory.compile(new StringReader("{{#disharmonies}}{{anchorId}}{{/disharmonies}}"), "test");
        StringWriter sectionWriter = new StringWriter();
        sectioned.execute(
                sectionWriter,
                java.util.Map.of("disharmonies", java.util.List.of(java.util.Map.of("anchorId", "GOD"))));
        assertTrue(sectionWriter.toString().contains("GOD"));
    }
}
