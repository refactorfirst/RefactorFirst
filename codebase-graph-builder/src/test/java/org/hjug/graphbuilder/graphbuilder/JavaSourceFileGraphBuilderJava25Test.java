package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.GraphBuilderConfig;
import org.hjug.graphbuilder.JavaRuntimeDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.openrewrite.java.JavaParser;

/**
 * Integration tests for Java 25 parser selection in
 * {@link JavaSourceFileGraphBuilder}.
 */
class JavaSourceFileGraphBuilderJava25Test {

    private static final String FIXTURE_DIR = "src/test/resources/javaSrcDirectory";

    /**
     * The fixture lives under {@code src/test/resources}, so test-source
     * exclusion must be disabled or every fixture file is filtered out.
     */
    private static GraphBuilderConfig config(boolean forceJava25Parser) {
        return GraphBuilderConfig.builder()
                .excludeTests(false)
                .forceJava25Parser(forceJava25Parser)
                .build();
    }

    @DisplayName("Standard parser is used when runtime is older than Java 25")
    @Test
    void standardParserUsedOnOlderRuntime() {
        if (JavaRuntimeDetector.isJava25OrHigher()) {
            return; // covered by the JAVA_25-only test below
        }
        JavaParser parser = JavaSourceFileGraphBuilder.createJavaParser(config(false));
        assertFalse(
                parser.getClass().getSimpleName().contains("25"),
                "Expected a non-Java-25 parser, got: " + parser.getClass().getName());
    }

    @DisplayName("Java 25 parser is used on Java 25+ runtimes")
    @EnabledOnJre(JRE.JAVA_25)
    @Test
    void java25ParserUsedOnJava25Runtime() {
        JavaParser parser = JavaSourceFileGraphBuilder.createJavaParser(config(false));
        assertTrue(
                parser.getClass().getName().contains("Java25")
                        || parser.getClass().getSimpleName().contains("25"),
                "Expected a Java 25 parser, got: " + parser.getClass().getName());
    }

    @DisplayName("forceJava25Parser on an older runtime falls back to the standard parser")
    @Test
    void forceJava25ParserFallsBackGracefully() {
        GraphBuilderConfig config = config(true);
        JavaParser parser = JavaSourceFileGraphBuilder.createJavaParser(config);
        assertNotNull(parser, "Parser creation must never fail, even with a forced load that cannot succeed");
        if (!JavaRuntimeDetector.isJava25OrHigher()) {
            assertFalse(
                    parser.getClass().getSimpleName().contains("25"),
                    "Expected fallback to a non-Java-25 parser, got: "
                            + parser.getClass().getName());
        }
    }

    @DisplayName("Graph build succeeds with forceJava25Parser enabled regardless of runtime")
    @Test
    void buildGraphSucceedsWithForcedJava25Parser() throws IOException {
        File srcDirectory = new File(FIXTURE_DIR);
        CodebaseGraphDTO dto =
                new JavaSourceFileGraphBuilder().buildGraph(srcDirectory.getAbsolutePath(), "", config(true));
        assertEquals(
                5,
                dto.getClassReferencesGraph().vertexSet().size(),
                "Forced Java 25 parser attempt must not break graph building");
    }

    @DisplayName("Graph build with default config still works (regression guard)")
    @Test
    void buildGraphWithDefaultConfig() throws IOException {
        File srcDirectory = new File(FIXTURE_DIR);
        CodebaseGraphDTO dto =
                new JavaSourceFileGraphBuilder().buildGraph(srcDirectory.getAbsolutePath(), "", config(false));
        assertEquals(5, dto.getClassReferencesGraph().vertexSet().size());
    }
}
