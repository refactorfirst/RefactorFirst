package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;

/**
 * End-to-end integration test for Java 25 source analysis. Only runs on a
 * Java 25+ runtime: the fixture uses Java 25-only syntax (JEP 513 flexible
 * constructor bodies — statements before an explicit {@code super()} call)
 * which earlier source-level parsers reject, and the Java 25 parser itself
 * can only load on a Java 25+ runtime. On older runtimes this test is skipped
 * and the graceful-fallback behavior is covered by
 * {@code JavaSourceFileGraphBuilderJava25Test} instead.
 */
@EnabledOnJre(JRE.JAVA_25)
class Java25AnalysisIntegrationTest {

    private static final String FIXTURE_DIR = "src/test/resources/java25SrcDirectory";

    @DisplayName("Java 25 source using flexible constructor bodies is parsed and appears in the graph")
    @Test
    void java25SourceIsAnalyzed() throws IOException {
        assertTrue(JavaRuntimeDetector.isJava25OrHigher(), "This test requires a Java 25+ runtime");

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
