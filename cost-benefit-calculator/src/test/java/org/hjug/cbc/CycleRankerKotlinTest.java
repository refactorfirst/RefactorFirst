package org.hjug.cbc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * CycleRanker round-trip on a Kotlin-only repository.
 *
 * <p>This test exercises {@link CycleRanker#generateClassReferencesGraph(boolean, String)} routing
 * through the new {@link org.hjug.graphbuilder.CompositeGraphBuilder}
 * orchestrator and for
 * {@link CycleRanker#rankCycles(Graph)} to surface cycles from Kotlin
 * source files. CompositeGraphBuilder runs Kotlin analysis
 * unconditionally; this test has {@code rewrite-kotlin} on its test
 * classpath via the module's test-scoped dependency (see
 * {@code pom.xml}).
 *
 * <p>Fixture: a {@code src/main/kotlin/com/kotlin/cycles/} directory
 * containing three top-level Kotlin classes
 * ({@code KotlinCycleA.kt}, {@code KotlinCycleB.kt}, {@code KotlinCycleC.kt})
 * that reference each other in a triangle A -> B -> C -> A. The Kotlin
 * visitor records each reference as a class-graph edge, so
 * {@link CycleRanker#rankCycles(Graph)} must surface exactly one cycle
 * containing all three vertices.
 *
 * <p>The cross-cutting assertion is intentionally minimal:
 * <ol>
 *   <li>{@code generateClassReferencesGraph} returns a non-null
 *       {@link CodebaseGraphDTO} whose classReferencesGraph contains the
 *       three Kotlin class vertices (proves the Kotlin parser was actually
 *       invoked by the orchestrator).
 *       </li>
 *   <li>{@code rankCycles} returns one {@link RankedCycle} whose
 *       {@code vertexSet} is exactly {KotlinCycleA, B, C}, proving the
 *       Kotlin-generated edges feed the existing
 *       {@link org.hjug.dsm.CircularReferenceChecker}.</li>
 *   <li>{@link CycleNode#getPathToCycleClass()} for each node derives the
 *       {@code .kt} source path through the source-path mapping
 *       (verifying the round-trip through {@link CycleRanker}'s
 *       {@code getClassRepoPath}).</li>
 * </ol>
 */
class CycleRankerKotlinTest {

    @TempDir
    public File tempFolder;

    private Git git;
    private String repoPath;
    private String srcRoot;

    @BeforeEach
    public void setUp() throws GitAPIException {
        git = Git.init().setDirectory(tempFolder).call();
        repoPath = git.getRepository().getWorkTree().getAbsolutePath();
        srcRoot = "src/main/kotlin/com/kotlin/cycles";
        new File(tempFolder, srcRoot).mkdirs();
    }

    @AfterEach
    public void tearDown() {
        if (git != null) {
            git.close();
        }
    }

    @DisplayName("CycleRanker detects cycles in a Kotlin-only repo")
    @Test
    void cycleRanker_detectsKotlinCycleViaCompositeGraphBuilder() throws IOException, GitAPIException {
        writeKtFile(
                "KotlinCycleA.kt",
                ""
                        + "package com.kotlin.cycles\n"
                        + "\n"
                        + "class KotlinCycleA {\n"
                        + "    fun makeB(): KotlinCycleB = KotlinCycleB()\n"
                        + "}\n");
        writeKtFile(
                "KotlinCycleB.kt",
                ""
                        + "package com.kotlin.cycles\n"
                        + "\n"
                        + "class KotlinCycleB {\n"
                        + "    fun makeC(): KotlinCycleC = KotlinCycleC()\n"
                        + "}\n");
        writeKtFile(
                "KotlinCycleC.kt",
                ""
                        + "package com.kotlin.cycles\n"
                        + "\n"
                        + "class KotlinCycleC {\n"
                        + "    fun makeA(): KotlinCycleA = KotlinCycleA()\n"
                        + "}\n");

        // The repo must have at least one commit — some code paths in the
        // downstream cost-benefit calculator touch git history. Although
        // CycleRanker itself doesn't use jgit, committing the fixture keeps
        // the temp dir in the same shape as CostBenefitCalculatorTest's
        // setup, in case future iterations extend this test.
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Kotlin cycle fixture").call();

        CycleRanker cycleRanker = new CycleRanker(repoPath, repoPath);
        CodebaseGraphDTO dto = cycleRanker.generateClassReferencesGraph(true, "src/test");

        assertNotNull(dto, "CompositeGraphBuilder should produce a non-null CodebaseGraphDTO");
        Graph<String, DefaultWeightedEdge> classGraph = dto.getClassReferencesGraph();
        assertNotNull(classGraph);

        String a = "com.kotlin.cycles.KotlinCycleA";
        String b = "com.kotlin.cycles.KotlinCycleB";
        String c = "com.kotlin.cycles.KotlinCycleC";

        assertTrue(
                classGraph.containsVertex(a),
                "KotlinCycleA vertex missing from class graph. Vertices: " + classGraph.vertexSet());
        assertTrue(
                classGraph.containsVertex(b),
                "KotlinCycleB vertex missing from class graph. Vertices: " + classGraph.vertexSet());
        assertTrue(
                classGraph.containsVertex(c),
                "KotlinCycleC vertex missing from class graph. Vertices: " + classGraph.vertexSet());

        // Edges form the triangle A -> B -> C -> A (each class instantiates
        // the next one as a method return value, which KotlinDependencyVisitor
        // records as a class dependency).
        assertTrue(classGraph.containsEdge(a, b), "Missing edge A -> B. Edges: " + classGraph.edgeSet());
        assertTrue(classGraph.containsEdge(b, c), "Missing edge B -> C. Edges: " + classGraph.edgeSet());
        assertTrue(classGraph.containsEdge(c, a), "Missing edge C -> A. Edges: " + classGraph.edgeSet());

        List<RankedCycle> rankedCycles = cycleRanker.rankCycles(classGraph);
        assertNotNull(rankedCycles);
        assertFalse(rankedCycles.isEmpty(), "rankCycles should surface at least one cycle from the Kotlin triangle");

        // Exactly one cycle exists because the three vertices form a single
        // strongly-connected component.
        assertEquals(1, rankedCycles.size(), "Expected exactly one RankedCycle, got: " + rankedCycles);
        RankedCycle cycle = rankedCycles.get(0);
        assertEquals(
                3,
                cycle.getVertexSet().size(),
                "Cycle vertex set should contain KotlinCycleA/B/C, got: " + cycle.getVertexSet());
        assertTrue(cycle.getVertexSet().contains(a), "Cycle should include KotlinCycleA");
        assertTrue(cycle.getVertexSet().contains(b), "Cycle should include KotlinCycleB");
        assertTrue(cycle.getVertexSet().contains(c), "Cycle should include KotlinCycleC");

        // source-path mapping hook integration — each CycleNode's path should resolve
        // through the classToSourceFilePathMapping produced by the Kotlin
        // visitor's sourceFileExtension() hook (i.e. end in .kt).
        for (CycleNode node : cycle.getCycleNodes()) {
            assertNotNull(node.getFileRepoPath(), "CycleNode fileRepoPath should be non-null");
            assertTrue(
                    node.getFileRepoPath().endsWith(".kt"),
                    "Kotlin cycle node path should end with .kt (source-path mapping hook), got: "
                            + node.getFileRepoPath());
        }
    }

    private void writeKtFile(String name, String content) throws IOException {
        File file = new File(tempFolder, srcRoot + "/" + name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(UTF_8));
        }
    }
}
