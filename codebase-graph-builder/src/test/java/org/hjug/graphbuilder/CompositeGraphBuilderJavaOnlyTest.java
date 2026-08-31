package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the behavioural consequences of review.md item #3 (option b):
 * {@code rewrite-kotlin} is a hard, non-optional compile dependency of
 * {@code codebase-graph-builder}, Kotlin analysis runs unconditionally,
 * and the reflective "is Kotlin on the classpath?" guard has been removed
 * entirely.
 *
 * <p>Three guarantees are codified here:
 * <ol>
 *   <li>{@link CompositeGraphBuilder} declares <strong>no</strong>
 *       {@code isKotlinAvailable()} method and no
 *       {@code KOTLIN_PARSER_CLASS} field — the reflection guard was dead
 *       code (the Kotlin builder is {@code new}ed directly and imports
 *       {@code org.openrewrite.kotlin.*} at compile time, so the guard
 *       could never save anyone) and has been deleted per option (b).</li>
 *   <li>{@link GraphBuilderConfig} exposes <strong>no</strong>
 *       {@code analyzeKotlin} field and no {@code isAnalyzeKotlin()}
 *       method — Kotlin analysis is now unconditional, so the switch is
 *       gone entirely (no {@code .analyzeKotlin(...)} on the builder
 *       either).</li>
 *   <li>The default {@link GraphBuilderConfig} (built with no explicit
 *       switches) produces a graph that <em>includes</em> Kotlin-parsed
 *   vertices and {@code .kt} source-path entries — i.e. Kotlin analysis
 *   is on by default and is no longer opt-in.</li>
 * </ol>
 */
class CompositeGraphBuilderJavaOnlyTest {

    @DisplayName("CompositeGraphBuilder exposes no isKotlinAvailable() reflection guard (option b)")
    @Test
    void isKotlinAvailableReflectionGuardHasBeenRemoved() {
        Method[] methods = CompositeGraphBuilder.class.getDeclaredMethods();
        boolean hasIsKotlinAvailable = Arrays.stream(methods).anyMatch(m -> "isKotlinAvailable".equals(m.getName()));
        assertFalse(
                hasIsKotlinAvailable,
                "CompositeGraphBuilder must not declare isKotlinAvailable(): "
                        + "under option (b) rewrite-kotlin is a hard dependency and the reflection guard is dead code. "
                        + "Declared methods: "
                        + Arrays.toString(
                                Arrays.stream(methods).map(Method::getName).toArray()));

        boolean hasKotlinParserClassConstant = Arrays.stream(CompositeGraphBuilder.class.getDeclaredFields())
                .anyMatch(f -> "KOTLIN_PARSER_CLASS".equals(f.getName()));
        assertFalse(
                hasKotlinParserClassConstant,
                "CompositeGraphBuilder must not declare a KOTLIN_PARSER_CLASS constant: "
                        + "the reflection guard it served has been removed.");
    }

    @DisplayName("GraphBuilderConfig exposes no analyzeKotlin switch (Kotlin is always analyzed)")
    @Test
    void analyzeKotlinSwitchHasBeenRemoved() {
        Field[] fields = GraphBuilderConfig.class.getDeclaredFields();
        boolean hasAnalyzeKotlinField = Arrays.stream(fields).anyMatch(f -> "analyzeKotlin".equals(f.getName()));
        assertFalse(
                hasAnalyzeKotlinField,
                "GraphBuilderConfig must not declare an analyzeKotlin field: "
                        + "Kotlin analysis is now unconditional. Fields: "
                        + Arrays.toString(
                                Arrays.stream(fields).map(Field::getName).toArray()));

        Method[] methods = GraphBuilderConfig.class.getDeclaredMethods();
        boolean hasIsAnalyzeKotlin = Arrays.stream(methods).anyMatch(m -> "isAnalyzeKotlin".equals(m.getName()));
        assertFalse(
                hasIsAnalyzeKotlin,
                "GraphBuilderConfig must not declare isAnalyzeKotlin(): "
                        + "the switch has been removed. Methods: "
                        + Arrays.toString(
                                Arrays.stream(methods).map(Method::getName).toArray()));
    }

    @DisplayName("Default config parses Kotlin sources unconditionally (.kt entries appear)")
    @Test
    void defaultConfigParsesKotlinSources() throws IOException {
        File srcDirectory = new File("src/test/resources/mixedSrcDirectory");
        GraphBuilderConfig config = GraphBuilderConfig.builder()
                .excludeTests(false)
                .testSourceDirectory("")
                .build(); // no analyzeKotlin switch — Kotlin is always on

        CompositeGraphBuilder compositeGraphBuilder = new CompositeGraphBuilder();
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), config);

        Graph<String, DefaultWeightedEdge> classGraph = dto.getClassReferencesGraph();
        assertNotNull(classGraph);

        // KConsumer is declared only in a .kt file; its presence proves Kotlin parsing ran.
        assertTrue(
                classGraph.containsVertex("com.ideacrest.parser.mixedclasses.KConsumer"),
                "KConsumer must be a vertex when Kotlin analysis runs unconditionally, vertices: "
                        + classGraph.vertexSet());

        String kconsumerPath = dto.getClassToSourceFilePathMapping().get("com.ideacrest.parser.mixedclasses.KConsumer");
        assertNotNull(
                kconsumerPath,
                "KConsumer source-path entry must exist when Kotlin analysis runs unconditionally, mapping: "
                        + dto.getClassToSourceFilePathMapping());
        assertTrue(kconsumerPath.endsWith(".kt"), "KConsumer source path must end with .kt, was: " + kconsumerPath);
    }
}
