package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.GraphBuilderConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.openrewrite.java.JavaParser;

/**
 * Tests for parser selection in {@link JavaSourceFileGraphBuilder}.
 *
 * <p>Two mechanisms select a Java 25-capable parser on JDK 25+ runtimes: the
 * JEP 238 shadowed variant of {@link Java25ParserFactory} when classes are
 * loaded from the packaged multi-release jar (verified by
 * {@code MultiReleaseJarIT}), and OpenRewrite's own
 * {@link JavaParser#fromJavaVersion()}, which reflectively elevates to
 * {@code Java25Parser} when the runtime is 25+ and {@code rewrite-java-25} is
 * on the classpath (as it always is in this module and in the shipped
 * distributions). Under surefire's exploded {@code target/classes} classpath
 * the factory's base variant answers, so the elevation path is what this test
 * exercises on JDK 25.
 */
class JavaSourceFileGraphBuilderJava25Test {

    private static final String FIXTURE_DIR = "src/test/resources/javaSrcDirectory";

    /**
     * The fixture lives under {@code src/test/resources}, so test-source
     * exclusion must be disabled or every fixture file is filtered out.
     */
    private static GraphBuilderConfig config() {
        return GraphBuilderConfig.builder().excludeTests(false).build();
    }

    @DisplayName("Standard parser is used when runtime is older than Java 25")
    @Test
    void standardParserUsedOnOlderRuntime() {
        if (Runtime.version().feature() >= 25) {
            return; // covered by the JAVA_25-only test below
        }
        JavaParser parser = JavaSourceFileGraphBuilder.createJavaParser();
        assertFalse(
                parser.getClass().getSimpleName().contains("25"),
                "Expected a non-Java-25 parser, got: " + parser.getClass().getName());
    }

    @DisplayName("JDK 25: base factory variant answers on exploded classpath; fromJavaVersion still elevates")
    @EnabledForJreRange(min = JRE.JAVA_25)
    @Test
    void jdk25YieldsJava25CapableParser() {
        assertTrue(
                Java25ParserFactory.createJava25Parser().isEmpty(),
                "Base factory variant must answer when classes are loaded from an exploded classpath "
                        + "(JEP 238 shadowing applies only to jars)");
        // fromJavaVersion() reflectively elevates to Java25Parser on a 25+
        // runtime because rewrite-java-25 is on the (test) classpath.
        JavaParser parser = JavaSourceFileGraphBuilder.createJavaParser();
        assertNotNull(parser, "Parser creation must never fail");
        assertTrue(
                parser.getClass().getName().contains("Java25"),
                "Expected a Java 25-capable parser on JDK 25, got: "
                        + parser.getClass().getName());
    }

    @DisplayName("Graph build with default config succeeds (regression guard)")
    @Test
    void buildGraphWithDefaultConfig() throws IOException {
        File srcDirectory = new File(FIXTURE_DIR);
        CodebaseGraphDTO dto =
                new JavaSourceFileGraphBuilder().buildGraph(srcDirectory.getAbsolutePath(), "", config());
        assertEquals(5, dto.getClassReferencesGraph().vertexSet().size());
    }
}
