package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.hjug.cbc.CycleNode;
import org.hjug.cbc.RankedCycle;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reporting smoke test for Kotlin source files.
 *
 * <p>This Kotlin case is added to
 * {@code HtmlReportTest} (the existing {@link #buildClassCycleDot} Java test
 * only), with the expectation that {@link HtmlReport} / {@link SimpleHtmlReport}
 * require no production change — the report renders the
 * {@link CodebaseGraphDTO#getClassToSourceFilePathMapping()} entries verbatim
 * into {@code URL="..."} links, so a Kotlin class whose mapping entry ends in
 * {@code .kt} simply renders an {@code .kt} source-path link in the DOT
 * output without any code path branching on the file extension.
 *
 * <p>This test pins that behaviour:
 *
 * <ol>
 *   <li>Build a synthetic class graph + {@link RankedCycle} mirroring
 *       {@link HtmlReportTest#buildClassCycleDot}'s Java triangle, but with
 *       Kotlin-style FQNs ({@code com.kotlin.cycles.KotlinCycleA/B/C}).</li>
 *   <li>Populate the {@code classToSourceFilePathMapping} mock with paths
 *       ending in {@code .kt} (mirroring what the source-path mapping
 *       on a Kotlin-enabled run).</li>
 *   <li>Assert {@link HtmlReport#buildClassCycleDot} produces DOT with the
 *       exact {@code .kt} URLs — proving no report-side change is needed
 *       and no character of the output differs in shape between Java and
 *       Kotlin source paths.</li>
 * </ol>
 *
 * <p>This is the full Kotlin reporting smoke test — a test against the
 * report renderer's class-cycle DOT path. There is intentionally no
 * end-to-end test invoking {@link SimpleHtmlReport#execute} here because
 * that path requires a real git repo plus CSS/JS rendering machinery
 * already covered by {@link HtmlReportTest}'s existing Java case — adding
 * a Kotlin twin would duplicate the Kotlin cycle-round-trip coverage
 * without exercising any additional report-side code.
 */
class HtmlReportKotlinTest {

    @DisplayName("HtmlReport.buildClassCycleDot emits .kt URLs from Kotlin source-path mapping")
    @Test
    void buildClassCycleDot_kotlinSourcePaths() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        // The renderer uses simple class names for the edge spec (matches the
        // existing Java case which expects A -> B, not FQN). The class-graph
        // vertices are still FQNs; the renderer strips the package prefix.
        String a = "com.kotlin.cycles.KotlinCycleA";
        String b = "com.kotlin.cycles.KotlinCycleB";
        String c = "com.kotlin.cycles.KotlinCycleC";
        classGraph.addVertex(a);
        classGraph.addVertex(b);
        classGraph.addVertex(c);
        classGraph.addEdge(a, b);
        classGraph.addEdge(b, c);
        classGraph.addEdge(c, a);
        classGraph.setEdgeWeight(a, b, 2);

        List<CycleNode> cycleNodes = new ArrayList<>();
        RankedCycle rankedCycle =
                new RankedCycle("KotlinCycle", classGraph.vertexSet(), classGraph.edgeSet(), cycleNodes);

        HtmlReport htmlReport = new HtmlReport();
        CodebaseGraphDTO dto = mock(CodebaseGraphDTO.class);
        HashMap<String, String> map = new HashMap<>();
        // Mirror the existing Java case: paths in the mapping are absolute
        // (start with "/") so the renderer's `repoUrl + path` produces a
        // well-formed URL.
        map.put(a, "/src/main/kotlin/com/kotlin/cycles/KotlinCycleA.kt");
        map.put(b, "/src/main/kotlin/com/kotlin/cycles/KotlinCycleB.kt");
        map.put(c, "/src/main/kotlin/com/kotlin/cycles/KotlinCycleC.kt");
        when(dto.getClassToSourceFilePathMapping()).thenReturn(map);

        String repoUrl = "https://github.com/refactorfirst/RefactorFirst/blob";
        String dot = htmlReport.buildClassCycleDot(classGraph, rankedCycle, repoUrl, dto);

        String expectedDot =
                """
                `strict digraph G {
                KotlinCycleA -> KotlinCycleB [ label = "2" weight = "2" ];
                KotlinCycleB -> KotlinCycleC [ label = "1" weight = "1" ];
                KotlinCycleC -> KotlinCycleA [ label = "1" weight = "1" ];
                KotlinCycleA [URL="https://github.com/refactorfirst/RefactorFirst/blob/src/main/kotlin/com/kotlin/cycles/KotlinCycleA.kt" target="_blank"];
                KotlinCycleB [URL="https://github.com/refactorfirst/RefactorFirst/blob/src/main/kotlin/com/kotlin/cycles/KotlinCycleB.kt" target="_blank"];
                KotlinCycleC [URL="https://github.com/refactorfirst/RefactorFirst/blob/src/main/kotlin/com/kotlin/cycles/KotlinCycleC.kt" target="_blank"];
                }`;\
                """;

        assertEquals(expectedDot, dot);
    }
}
