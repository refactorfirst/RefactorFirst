package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

/**
 * End-to-end integration test for Java 25 source analysis. The fixture uses
 * Java 25-only syntax (JEP 513 flexible constructor bodies — statements before
 * an explicit {@code super()} call) which earlier source-level parsers reject.
 *
 * <p>On a JDK 25+ runtime a Java 25-capable parser is always selected: either
 * by the JEP 238 multi-release variant of {@link Java25ParserFactory} (when
 * classes are loaded from the packaged jar — verified by
 * {@code MultiReleaseJarIT}) or by {@code JavaParser.fromJavaVersion()}, which
 * reflectively elevates to {@code Java25Parser} when {@code rewrite-java-25}
 * is on the classpath (always true here and in shipped distributions). The
 * versioned-variant construction itself is covered by
 * {@code Java25ParserFactoryTest}.
 */
@EnabledForJreRange(min = JRE.JAVA_25)
class Java25AnalysisIntegrationTest {

    private static final String FIXTURE_DIR = "src/test/resources/java25SrcDirectory";

    @DisplayName("Java 25 source using flexible constructor bodies is parsed and appears in the graph")
    @Test
    void java25SourceIsAnalyzed() throws IOException {
        File srcDirectory = new File(FIXTURE_DIR);
        GraphBuilderConfig config =
                GraphBuilderConfig.builder().excludeTests(false).build();
        CodebaseGraphDTO dto = new CompositeGraphBuilder().getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), config);

        assertTrue(
                dto.getClassReferencesGraph().containsVertex("com.example.java25.FlexibleConstructorBody"),
                "Java 25 fixture class must be a vertex in the class graph. Vertices: "
                        + dto.getClassReferencesGraph().vertexSet());
        assertTrue(
                dto.getClassToSourceFilePathMapping().containsKey("com.example.java25.FlexibleConstructorBody"),
                "Java 25 fixture class must be attributed to its source file");
    }
}
