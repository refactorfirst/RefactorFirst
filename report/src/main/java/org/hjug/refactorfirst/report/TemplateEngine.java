package org.hjug.refactorfirst.report;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads classpath mustache templates and renders view models against them.
 *
 * <p>Phase P2 of the feature-parity plan: the server-side Mustache engine for the generated
 * reports. Mustache interpolates with HTML escaping by default; triple-brace values render raw,
 * which the templates use for pre-rendered HTML (e.g. disharmony solutions).
 */
@Slf4j
public class TemplateEngine {

    /** Cache of compiled templates keyed by classpath resource name. */
    private final ConcurrentHashMap<String, Mustache> cache = new ConcurrentHashMap<>();

    private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

    /** Loads (and caches) the compiled template registered under the given classpath name. */
    public Mustache load(String templateName) {
        return cache.computeIfAbsent(templateName, this::compile);
    }

    /** Compiles the template registered under the given classpath name. */
    private Mustache compile(String templateName) {
        try (InputStream in = getClass().getResourceAsStream("/" + templateName)) {
            if (in == null) {
                throw new IllegalStateException("Mustache template not found on classpath: /" + templateName);
            }
            String template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return mustacheFactory.compile(new StringReader(template), templateName);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load mustache template: " + templateName, e);
        }
    }

    /** Renders the template registered under the given classpath name against the view model. */
    public String render(String templateName, Object view) throws Exception {
        MustacheFactory factory = new DefaultMustacheFactory();
        Mustache mustache = factory.compile(new StringReader(readTemplate(templateName)), templateName);
        StringWriter writer = new StringWriter();
        mustache.execute(writer, view).flush();
        return writer.toString();
    }

    /** Inserts assets into trusted template slots before interpolating report data. */
    public String renderWithAssets(String templateName, Object view) throws Exception {
        return renderInline(new AssetInliner().inline(readTemplate(templateName)), templateName, view);
    }

    /** Renders an inline template source against the view model (used by tests and the simple report). */
    public String renderInline(String templateSource, Object view) throws Exception {
        MustacheFactory factory = new DefaultMustacheFactory();
        Mustache mustache = factory.compile(new StringReader(templateSource), "inline");
        StringWriter writer = new StringWriter();
        mustache.execute(writer, view).flush();
        return writer.toString();
    }

    /** Renders an inline template source against the view model, under an explicit template name. */
    public String renderInline(String templateSource, String templateName, Object view) throws Exception {
        MustacheFactory factory = new DefaultMustacheFactory();
        Mustache mustache = factory.compile(new StringReader(templateSource), templateName);
        StringWriter writer = new StringWriter();
        mustache.execute(writer, view).flush();
        return writer.toString();
    }

    private String readTemplate(String templateName) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/" + templateName)) {
            if (in == null) {
                throw new IllegalStateException("Mustache template not found on classpath: /" + templateName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
