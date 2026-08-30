package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Test;

class CompositeGraphBuilderReconciliationTest {

    /**
     * Tests the reconciliation of a fabricated cross-language vertex into its canonical FQN.
     * The fabricated vertex has a wrong package (caller's package) but the correct simple name.
     * The canonical vertex exists in the source path mapping with the correct package.
     */
    @Test
    void reconcileUnattributedVertices_uniqueMatch_redirectsAndSumsWeights() {
        // Build a class graph with:
        // - Canonical Kotlin class: com.almasb.fxgl.app.GameSettings (has source mapping)
        // - Fabricated Java reference: com.other.pkg.GameSettings (no source mapping)
        // - Caller: com.other.pkg.SomeClass -> fabricated GameSettings (weight 3)
        // - Another caller: com.third.pkg.OtherClass -> canonical GameSettings (weight 2)
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.almasb.fxgl.app.GameSettings"); // canonical
        classGraph.addVertex("com.other.pkg.GameSettings"); // fabricated
        classGraph.addVertex("com.other.pkg.SomeClass");
        classGraph.addVertex("com.third.pkg.OtherClass");

        DefaultWeightedEdge e1 = classGraph.addEdge("com.other.pkg.SomeClass", "com.other.pkg.GameSettings");
        classGraph.setEdgeWeight(e1, 3);
        DefaultWeightedEdge e2 = classGraph.addEdge("com.third.pkg.OtherClass", "com.almasb.fxgl.app.GameSettings");
        classGraph.setEdgeWeight(e2, 2);

        // Package graph
        Graph<String, DefaultWeightedEdge> packageGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        packageGraph.addVertex("com.other.pkg");
        packageGraph.addVertex("com.third.pkg");
        packageGraph.addVertex("com.almasb.fxgl.app");
        packageGraph.addEdge("com.other.pkg", "com.other.pkg"); // self-edge from fabricated (ignored by collector)
        packageGraph.addEdge("com.third.pkg", "com.almasb.fxgl.app");

        // Source path mapping has the canonical FQN AND the caller classes (they're in the codebase)
        Map<String, String> sourcePathMapping = new HashMap<>();
        sourcePathMapping.put(
                "com.almasb.fxgl.app.GameSettings", "/fxgl-core/src/main/kotlin/com/almasb/fxgl/app/Settings.kt");
        sourcePathMapping.put("com.other.pkg.SomeClass", "/src/main/java/com/other/pkg/SomeClass.java");
        sourcePathMapping.put("com.third.pkg.OtherClass", "/src/main/java/com/third/pkg/OtherClass.java");

        // Call the reconciliation helper (to be implemented)
        CompositeGraphBuilder.reconcileUnattributedVertices(classGraph, packageGraph, sourcePathMapping);

        // Assertions
        // 1. Fabricated vertex should be removed
        assertFalse(
                classGraph.containsVertex("com.other.pkg.GameSettings"),
                "Fabricated vertex should be removed after reconciliation");

        // 2. Canonical vertex should still exist
        assertTrue(classGraph.containsVertex("com.almasb.fxgl.app.GameSettings"), "Canonical vertex should remain");

        // 3. Edge from SomeClass should now point to canonical with its original weight
        assertTrue(
                classGraph.containsEdge("com.other.pkg.SomeClass", "com.almasb.fxgl.app.GameSettings"),
                "Edge should be redirected to canonical vertex");
        DefaultWeightedEdge redirectedEdge =
                classGraph.getEdge("com.other.pkg.SomeClass", "com.almasb.fxgl.app.GameSettings");
        assertEquals(
                3.0, classGraph.getEdgeWeight(redirectedEdge), "Edge weight should be preserved from fabricated edge");

        // 4. The pre-existing edge from OtherClass to canonical should remain with its weight
        assertTrue(
                classGraph.containsEdge("com.third.pkg.OtherClass", "com.almasb.fxgl.app.GameSettings"),
                "Pre-existing edge to canonical should remain");
        DefaultWeightedEdge existingEdge =
                classGraph.getEdge("com.third.pkg.OtherClass", "com.almasb.fxgl.app.GameSettings");
        assertEquals(2.0, classGraph.getEdgeWeight(existingEdge), "Pre-existing edge weight should be preserved");

        // 5. Original edge to fabricated should be gone
        assertFalse(
                classGraph.containsEdge("com.other.pkg.SomeClass", "com.other.pkg.GameSettings"),
                "Original edge to fabricated vertex should be removed");

        // 6. Package graph should have the real cross-package edge
        assertTrue(
                packageGraph.containsEdge("com.other.pkg", "com.almasb.fxgl.app"),
                "Package graph should have the real cross-package edge");
    }

    /**
     * Tests the reconciliation when both a fabricated edge and a real edge exist
     * from the SAME source to the canonical target - weights should be summed.
     */
    @Test
    void reconcileUnattributedVertices_sameSourceMultipleEdges_sumsWeights() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.almasb.fxgl.app.GameSettings"); // canonical
        classGraph.addVertex("com.other.pkg.GameSettings"); // fabricated
        classGraph.addVertex("com.other.pkg.SomeClass");

        // Two edges from the SAME source: one to fabricated, one to canonical
        DefaultWeightedEdge e1 = classGraph.addEdge("com.other.pkg.SomeClass", "com.other.pkg.GameSettings");
        classGraph.setEdgeWeight(e1, 3);
        DefaultWeightedEdge e2 = classGraph.addEdge("com.other.pkg.SomeClass", "com.almasb.fxgl.app.GameSettings");
        classGraph.setEdgeWeight(e2, 2);

        Graph<String, DefaultWeightedEdge> packageGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        Map<String, String> sourcePathMapping = new HashMap<>();
        sourcePathMapping.put(
                "com.almasb.fxgl.app.GameSettings", "/fxgl-core/src/main/kotlin/com/almasb/fxgl/app/Settings.kt");
        sourcePathMapping.put("com.other.pkg.SomeClass", "/src/main/java/com/other/pkg/SomeClass.java");

        CompositeGraphBuilder.reconcileUnattributedVertices(classGraph, packageGraph, sourcePathMapping);

        // Fabricated vertex should be removed
        assertFalse(classGraph.containsVertex("com.other.pkg.GameSettings"));

        // The two edges from SomeClass should be merged into one with summed weight (3 + 2 = 5)
        assertTrue(classGraph.containsEdge("com.other.pkg.SomeClass", "com.almasb.fxgl.app.GameSettings"));
        DefaultWeightedEdge mergedEdge =
                classGraph.getEdge("com.other.pkg.SomeClass", "com.almasb.fxgl.app.GameSettings");
        assertEquals(5.0, classGraph.getEdgeWeight(mergedEdge), "Edge weights from same source should be summed");
    }

    /**
     * Tests that ambiguous simple names (multiple real classes with same simple name)
     * leave the fabricated vertex untouched.
     */
    @Test
    void reconcileUnattributedVertices_ambiguousMatch_leavesVertexUntouched() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.pkg1.Node"); // canonical 1
        classGraph.addVertex("com.pkg2.Node"); // canonical 2
        classGraph.addVertex("com.caller.Node"); // fabricated - SAME simple name "Node"
        classGraph.addVertex("com.caller.Caller");

        classGraph.addEdge("com.caller.Caller", "com.caller.Node");
        classGraph.setEdgeWeight(classGraph.getEdge("com.caller.Caller", "com.caller.Node"), 1);

        Map<String, String> sourcePathMapping = new HashMap<>();
        sourcePathMapping.put("com.pkg1.Node", "/src/main/java/com/pkg1/Node.java");
        sourcePathMapping.put("com.pkg2.Node", "/src/main/java/com/pkg2/Node.java");

        CompositeGraphBuilder.reconcileUnattributedVertices(
                classGraph, new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class), sourcePathMapping);

        // Fabricated vertex should remain (ambiguous - multiple candidates with same simple name)
        assertTrue(
                classGraph.containsVertex("com.caller.Node"),
                "Fabricated vertex should remain when simple name is ambiguous");
    }

    /**
     * Tests that genuinely external classes (no real declaration in source mapping)
     * are REMOVED from the graph (not left untouched).<br>
     * This is the key behavior change: fabricated vertices with zero matching
     * real declarations represent external library classes (e.g., JavaFX) that
     * were incorrectly attributed to the caller's package. They should be
     * pruned entirely.
     */
    @Test
    void reconcileUnattributedVertices_zeroMatch_removesExternalClass() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.myapp.MyClass");
        classGraph.addVertex("com.myapp.Button"); // fabricated - external JavaFX class attributed to caller's package
        classGraph.addEdge("com.myapp.MyClass", "com.myapp.Button");
        classGraph.setEdgeWeight(classGraph.getEdge("com.myapp.MyClass", "com.myapp.Button"), 1);

        Map<String, String> sourcePathMapping = new HashMap<>();
        sourcePathMapping.put("com.myapp.MyClass", "/src/main/java/com/myapp/MyClass.java");

        CompositeGraphBuilder.reconcileUnattributedVertices(
                classGraph, new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class), sourcePathMapping);

        // Fabricated external class should be REMOVED (not left untouched)
        assertFalse(
                classGraph.containsVertex("com.myapp.Button"),
                "External class vertex should be removed when no real declaration exists");

        // Edge to external class should also be removed
        assertFalse(
                classGraph.containsEdge("com.myapp.MyClass", "com.myapp.Button"),
                "Edge to external class should be removed");
    }

    /**
     * Tests that external classes with their REAL package name (not fabricated)
     * are also removed when they have zero matches.
     */
    @Test
    void reconcileUnattributedVertices_zeroMatch_realExternalPackage_removesExternalClass() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.myapp.MyClass");
        classGraph.addVertex("javafx.scene.control.Button"); // external class with real package
        classGraph.addEdge("com.myapp.MyClass", "javafx.scene.control.Button");
        classGraph.setEdgeWeight(classGraph.getEdge("com.myapp.MyClass", "javafx.scene.control.Button"), 1);

        Map<String, String> sourcePathMapping = new HashMap<>();
        sourcePathMapping.put("com.myapp.MyClass", "/src/main/java/com/myapp/MyClass.java");

        CompositeGraphBuilder.reconcileUnattributedVertices(
                classGraph, new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class), sourcePathMapping);

        // External class with real package should also be removed
        assertFalse(
                classGraph.containsVertex("javafx.scene.control.Button"),
                "External class with real package should be removed when no real declaration exists");
    }

    /**
     * Tests that when no package match exists among multiple candidates,
     * the ambiguous case leaves the fabricated vertex untouched (original behavior preserved).
     */
    @Test
    void reconcileUnattributedVertices_noPackageMatch_leavesAmbiguousUntouched() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.pkg1.Node"); // canonical 1
        classGraph.addVertex("com.pkg2.Node"); // canonical 2
        classGraph.addVertex("com.unrelated.Node"); // fabricated - same simple name "Node", NO package match
        classGraph.addVertex("com.unrelated.Caller");

        classGraph.addEdge("com.unrelated.Caller", "com.unrelated.Node");
        classGraph.setEdgeWeight(classGraph.getEdge("com.unrelated.Caller", "com.unrelated.Node"), 1);

        Map<String, String> sourcePathMapping = new HashMap<>();
        sourcePathMapping.put("com.pkg1.Node", "/src/main/java/com/pkg1/Node.java");
        sourcePathMapping.put("com.pkg2.Node", "/src/main/java/com/pkg2/Node.java");

        CompositeGraphBuilder.reconcileUnattributedVertices(
                classGraph, new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class), sourcePathMapping);

        // Fabricated vertex should remain (no package match, ambiguous)
        assertTrue(
                classGraph.containsVertex("com.unrelated.Node"),
                "Fabricated vertex should remain when no package match exists");
    }

    /**
     * Tests that contractVertex guards against self-loops when the source of
     * an incoming edge to the fabricated vertex is the canonical vertex itself.
     * <p>
     * Scenario: A Kotlin class {@code b.Bar} references the fabricated vertex
     * {@code a.Bar} (created by Java parser for the Kotlin class). Reconciliation
     * maps {@code a.Bar} to {@code b.Bar}. If there's an edge {@code b.Bar ->
     * a.Bar}, contracting would create {@code b.Bar -> b.Bar} self-loop.
     * </p>
     */
    @Test
    void reconcileUnattributedVertices_incomingEdgeFromCanonical_guardsAgainstSelfLoop() {
        // Build a class graph with:
        // - Canonical Kotlin class: b.Bar (has source mapping)
        // - Fabricated Java reference: a.Bar (no source mapping)
        // - Caller: b.Bar -> fabricated a.Bar (weight 3)
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("b.Bar"); // canonical
        classGraph.addVertex("a.Bar"); // fabricated
        classGraph.addVertex("b.Bar"); // same as canonical, but we add edge from canonical to fabricated

        DefaultWeightedEdge e1 = classGraph.addEdge("b.Bar", "a.Bar");
        classGraph.setEdgeWeight(e1, 3);

        Graph<String, DefaultWeightedEdge> packageGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        packageGraph.addVertex("a");
        packageGraph.addVertex("b");

        Map<String, String> sourcePathMapping = new HashMap<>();
        sourcePathMapping.put("b.Bar", "/src/main/kotlin/b/Bar.kt");

        CompositeGraphBuilder.reconcileUnattributedVertices(classGraph, packageGraph, sourcePathMapping);

        // Fabricated vertex should be removed
        assertFalse(classGraph.containsVertex("a.Bar"), "Fabricated vertex should be removed");

        // Canonical vertex should exist
        assertTrue(classGraph.containsVertex("b.Bar"), "Canonical vertex should remain");

        // NO self-loop should exist on canonical vertex
        assertFalse(
                classGraph.containsEdge("b.Bar", "b.Bar"),
                "Self-loop should NOT be created when incoming edge source equals canonical vertex");
    }

    /**
     * Tests that contractVertex guards against self-loops when the target of
     * an outgoing edge from the fabricated vertex is the canonical vertex itself.
     * <p>
     * Scenario: The fabricated vertex {@code a.Bar} has an edge to the canonical
     * vertex {@code b.Bar}. Contracting would create {@code b.Bar -> b.Bar} self-loop.
     * </p>
     */
    @Test
    void reconcileUnattributedVertices_outgoingEdgeToCanonical_guardsAgainstSelfLoop() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("b.Bar"); // canonical
        classGraph.addVertex("a.Bar"); // fabricated

        DefaultWeightedEdge e1 = classGraph.addEdge("a.Bar", "b.Bar");
        classGraph.setEdgeWeight(e1, 3);

        Graph<String, DefaultWeightedEdge> packageGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        packageGraph.addVertex("a");
        packageGraph.addVertex("b");

        Map<String, String> sourcePathMapping = new HashMap<>();
        sourcePathMapping.put("b.Bar", "/src/main/kotlin/b/Bar.kt");

        CompositeGraphBuilder.reconcileUnattributedVertices(classGraph, packageGraph, sourcePathMapping);

        // Fabricated vertex should be removed
        assertFalse(classGraph.containsVertex("a.Bar"), "Fabricated vertex should be removed");

        // Canonical vertex should exist
        assertTrue(classGraph.containsVertex("b.Bar"), "Canonical vertex should remain");

        // NO self-loop should exist on canonical vertex
        assertFalse(
                classGraph.containsEdge("b.Bar", "b.Bar"),
                "Self-loop should NOT be created when outgoing edge target equals canonical vertex");
    }

    /**
     * Tests that a graph vertex which IS a real declaration (present in the source
     * path mapping) is never contracted into a same-named class in another package,
     * even when multiple candidates share its simple name.
     * <p>
     * Setup: {@code com.pkg1.Target} and {@code com.pkg2.Target} are both real
     * declarations in {@code sourcePathMapping}, and {@code com.pkg1.Target} is a
     * vertex in the graph with edges. Because it is a key in the mapping, it is
     * excluded from the fabricated-vertex scan and must be left completely untouched.
     * </p>
     * <p>
     * Note: a "fabricated vertex that shares a package with exactly one candidate"
     * is not constructible. Both {@code simpleName} and {@code packageName} are
     * derived from a single {@code lastIndexOf('.')} split, so any candidate sharing
     * the fabricated vertex's simple name AND package would be the same FQN as the
     * fabricated vertex — which is precisely what the source-mapping exclusion
     * removes from consideration. The package-preference loop in
     * {@code reconcileUnattributedVertices} therefore never redirects. This test
     * pins the reachable behavior of that package-collision configuration instead.
     * </p>
     */
    @Test
    void reconcileUnattributedVertices_realDeclarationWithDuplicateSimpleName_neverContracted() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.pkg1.Target"); // real declaration (in mapping)
        classGraph.addVertex("com.pkg2.Target"); // same-named real declaration (in mapping)
        classGraph.addVertex("com.pkg1.Caller"); // real caller (in mapping)

        DefaultWeightedEdge e1 = classGraph.addEdge("com.pkg1.Caller", "com.pkg1.Target");
        classGraph.setEdgeWeight(e1, 2);
        DefaultWeightedEdge e2 = classGraph.addEdge("com.pkg1.Target", "com.pkg2.Target");
        classGraph.setEdgeWeight(e2, 3);

        Map<String, String> sourcePathMapping = new HashMap<>();
        sourcePathMapping.put("com.pkg1.Target", "/src/main/java/com/pkg1/Target.java");
        sourcePathMapping.put("com.pkg2.Target", "/src/main/java/com/pkg2/Target.java");
        sourcePathMapping.put("com.pkg1.Caller", "/src/main/java/com/pkg1/Caller.java");

        CompositeGraphBuilder.reconcileUnattributedVertices(
                classGraph, new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class), sourcePathMapping);

        // Both real declarations must remain despite the shared simple name
        assertTrue(classGraph.containsVertex("com.pkg1.Target"), "Real declaration com.pkg1.Target must remain");
        assertTrue(classGraph.containsVertex("com.pkg2.Target"), "Real declaration com.pkg2.Target must remain");
        assertEquals(3, classGraph.vertexSet().size(), "No vertex may be contracted or removed");

        // Every edge intact with its original weight — nothing redirected
        assertTrue(classGraph.containsEdge("com.pkg1.Caller", "com.pkg1.Target"));
        assertEquals(
                2.0,
                classGraph.getEdgeWeight(classGraph.getEdge("com.pkg1.Caller", "com.pkg1.Target")),
                "Edge to real declaration must keep its weight");
        assertTrue(classGraph.containsEdge("com.pkg1.Target", "com.pkg2.Target"));
        assertEquals(
                3.0,
                classGraph.getEdgeWeight(classGraph.getEdge("com.pkg1.Target", "com.pkg2.Target")),
                "Edge between real declarations must keep its weight");

        // Contraction would have collapsed com.pkg1.Target into com.pkg2.Target,
        // turning its incoming edges into edges on com.pkg2.Target (and its
        // outgoing edge into a self-loop). Neither may exist.
        assertFalse(
                classGraph.containsEdge("com.pkg2.Target", "com.pkg2.Target"),
                "No self-loop may appear from contracting a real declaration into its same-named sibling");
        assertFalse(
                classGraph.containsEdge("com.pkg1.Caller", "com.pkg2.Target"),
                "Caller edge must not be redirected to the other same-named class");
    }

    /**
     * Tests that Java anonymous inner classes (e.g., {@code Outer$1}) are mapped to their
     * enclosing class's source path and are NOT removed as fabricated external vertices.
     * <p>
     * Scenario: {@code com.example.Outer} creates an anonymous class {@code com.example.Outer$1}
     * via {@code new Runnable() { ... }}. The anonymous class vertex enters the graph but has
     * no source mapping (no {@code J.ClassDeclaration}). Before reconciliation, this method
     * maps {@code Outer$1} to {@code Outer}'s source path. Thus {@code Outer$1} is present in
     * the source mapping and is excluded from fabricated-vertex processing entirely.
     * </p>
     */
    @Test
    void reconcileUnattributedVertices_javaAnonymousInnerClass_preservedViaEnclosingSourcePath() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.example.Outer"); // real declaration
        classGraph.addVertex("com.example.Outer$1"); // anonymous inner class (no source mapping initially)
        classGraph.addVertex("com.example.Caller"); // real caller

        classGraph.addEdge("com.example.Caller", "com.example.Outer");
        classGraph.addEdge("com.example.Outer", "com.example.Outer$1"); // Outer depends on its anonymous class

        Map<String, String> sourcePathMapping = new HashMap<>();
        sourcePathMapping.put("com.example.Outer", "/src/main/java/com/example/Outer.java");
        sourcePathMapping.put("com.example.Caller", "/src/main/java/com/example/Caller.java");

        // Apply the anonymous vertex mapping step (normally done in merge())
        CompositeGraphBuilder.mapAnonymousVerticesToEnclosingSourcePath(classGraph, sourcePathMapping);

        CompositeGraphBuilder.reconcileUnattributedVertices(
                classGraph, new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class), sourcePathMapping);

        // Anonymous class should be preserved (mapped to enclosing class's source path)
        assertTrue(
                classGraph.containsVertex("com.example.Outer$1"),
                "Anonymous inner class should be preserved via enclosing class source mapping");

        // Edge from Outer to anonymous should remain
        assertTrue(
                classGraph.containsEdge("com.example.Outer", "com.example.Outer$1"),
                "Edge to anonymous inner class should remain");
    }

    /**
     * Tests that Java synthetic classes (e.g., {@code Outer$}) are mapped to their
     * enclosing class's source path and preserved.
     */
    @Test
    void reconcileUnattributedVertices_javaSyntheticClass_preservedViaEnclosingSourcePath() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.example.Outer");
        classGraph.addVertex("com.example.Outer$"); // synthetic class

        Map<String, String> sourcePathMapping = new HashMap<>();
        sourcePathMapping.put("com.example.Outer", "/src/main/java/com/example/Outer.java");

        CompositeGraphBuilder.mapAnonymousVerticesToEnclosingSourcePath(classGraph, sourcePathMapping);

        CompositeGraphBuilder.reconcileUnattributedVertices(
                classGraph, new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class), sourcePathMapping);

        assertTrue(
                classGraph.containsVertex("com.example.Outer$"),
                "Synthetic class should be preserved via enclosing class source mapping");
    }

    /**
     * Tests that Kotlin anonymous objects (e.g., {@code pkg.Outer.<anonymous>}) are mapped
     * to their enclosing class's source path and preserved.
     */
    @Test
    void reconcileUnattributedVertices_kotlinAnonymousObject_preservedViaEnclosingSourcePath() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("com.example.Outer");
        classGraph.addVertex("com.example.Outer.<anonymous>"); // Kotlin anonymous object

        Map<String, String> sourcePathMapping = new HashMap<>();
        sourcePathMapping.put("com.example.Outer", "/src/main/kotlin/com/example/Outer.kt");

        CompositeGraphBuilder.mapAnonymousVerticesToEnclosingSourcePath(classGraph, sourcePathMapping);

        CompositeGraphBuilder.reconcileUnattributedVertices(
                classGraph, new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class), sourcePathMapping);

        assertTrue(
                classGraph.containsVertex("com.example.Outer.<anonymous>"),
                "Kotlin anonymous object should be preserved via enclosing class source mapping");
    }

    /**
     * Tests that top-level Kotlin anonymous (just {@code "<anonymous>"}) with no enclosing
     * class is NOT preserved (no source mapping possible) and would be treated as external
     * if no matching declaration exists.
     */
    @Test
    void reconcileUnattributedVertices_topLevelKotlinAnonymous_noEnclosingClass_notMapped() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("<anonymous>"); // Top-level Kotlin anonymous, no enclosing class

        Map<String, String> sourcePathMapping = new HashMap<>();
        // No enclosing class in mapping

        CompositeGraphBuilder.mapAnonymousVerticesToEnclosingSourcePath(classGraph, sourcePathMapping);

        CompositeGraphBuilder.reconcileUnattributedVertices(
                classGraph, new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class), sourcePathMapping);

        // Top-level <anonymous> has no enclosing class to map to, so it's not in source mapping
        // and would be removed as external (zero match)
        assertFalse(
                classGraph.containsVertex("<anonymous>"),
                "Top-level Kotlin anonymous with no enclosing class should be removed as external");
    }

    // ===================== Merge Edge Ownership Tests =====================

    /**
     * Tests that mergeClassRelationships creates edges in the merged class graph
     * rather than copying source-graph edges by object identity.
     */
    @Test
    void mergeClassRelationships_usesMergedGraphEdges() {
        // Java DTO: com.java.A -> com.java.B (weight 2), both in pkg com.java
        CodebaseGraphDTO javaDto = createDto("com.java.A", "com.java.B", 2.0, "com.java", "com.java");

        // Kotlin DTO: com.kotlin.C -> com.kotlin.D (weight 3), both in pkg com.kotlin
        CodebaseGraphDTO kotlinDto = createDto("com.kotlin.C", "com.kotlin.D", 3.0, "com.kotlin", "com.kotlin");

        CodebaseGraphDTO merged = CompositeGraphBuilder.merge(javaDto, kotlinDto);

        // Verify merged package graph has both package vertices and the cross-package edge
        Graph<String, DefaultWeightedEdge> mergedPkgGraph = merged.getPackageReferencesGraph();
        assertTrue(mergedPkgGraph.containsVertex("com.java"));
        assertTrue(mergedPkgGraph.containsVertex("com.kotlin"));

        // Verify merged class graph has all four class vertices
        Graph<String, DefaultWeightedEdge> mergedClassGraph = merged.getClassReferencesGraph();
        assertTrue(mergedClassGraph.containsVertex("com.java.A"));
        assertTrue(mergedClassGraph.containsVertex("com.java.B"));
        assertTrue(mergedClassGraph.containsVertex("com.kotlin.C"));
        assertTrue(mergedClassGraph.containsVertex("com.kotlin.D"));

        // Verify class relationship map keys are edges from MERGED package graph
        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> relMap = merged.getClassRelationshipsInPackageRelationship();

        for (DefaultWeightedEdge pkgEdge : relMap.keySet()) {
            assertTrue(
                    mergedPkgGraph.containsEdge(pkgEdge),
                    "Package relationship key must belong to merged package graph");
        }

        // Verify class edges in relationship sets belong to MERGED class graph
        for (Set<DefaultWeightedEdge> classEdges : relMap.values()) {
            for (DefaultWeightedEdge classEdge : classEdges) {
                assertTrue(
                        mergedClassGraph.containsEdge(classEdge),
                        "Class relationship edge must belong to merged class graph");
            }
        }
    }

    /**
     * Tests that source and target endpoints are preserved when merging
     * class relationships.
     */
    @Test
    void mergeClassRelationships_preservesEndpoints() {
        // Java: com.pkg1.Source -> com.pkg2.Target
        CodebaseGraphDTO javaDto = createDto("com.pkg1.Source", "com.pkg2.Target", 1.0, "com.pkg1", "com.pkg2");

        // Kotlin: com.pkg3.Source -> com.pkg4.Target
        CodebaseGraphDTO kotlinDto = createDto("com.pkg3.Source", "com.pkg4.Target", 1.0, "com.pkg3", "com.pkg4");

        CodebaseGraphDTO merged = CompositeGraphBuilder.merge(javaDto, kotlinDto);

        Graph<String, DefaultWeightedEdge> mergedClassGraph = merged.getClassReferencesGraph();
        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> relMap = merged.getClassRelationshipsInPackageRelationship();

        // Verify Java relationship endpoints preserved
        DefaultWeightedEdge javaPkgEdge = merged.getPackageReferencesGraph().getEdge("com.pkg1", "com.pkg2");
        assertNotNull(javaPkgEdge);
        Set<DefaultWeightedEdge> javaClassEdges = relMap.get(javaPkgEdge);
        assertNotNull(javaClassEdges);
        assertEquals(1, javaClassEdges.size());
        DefaultWeightedEdge javaClassEdge = javaClassEdges.iterator().next();
        assertEquals("com.pkg1.Source", mergedClassGraph.getEdgeSource(javaClassEdge));
        assertEquals("com.pkg2.Target", mergedClassGraph.getEdgeTarget(javaClassEdge));

        // Verify Kotlin relationship endpoints preserved
        DefaultWeightedEdge kotlinPkgEdge = merged.getPackageReferencesGraph().getEdge("com.pkg3", "com.pkg4");
        assertNotNull(kotlinPkgEdge);
        Set<DefaultWeightedEdge> kotlinClassEdges = relMap.get(kotlinPkgEdge);
        assertNotNull(kotlinClassEdges);
        assertEquals(1, kotlinClassEdges.size());
        DefaultWeightedEdge kotlinClassEdge = kotlinClassEdges.iterator().next();
        assertEquals("com.pkg3.Source", mergedClassGraph.getEdgeSource(kotlinClassEdge));
        assertEquals("com.pkg4.Target", mergedClassGraph.getEdgeTarget(kotlinClassEdge));
    }

    /**
     * Tests that weights are correctly summed when Java and Kotlin contribute
     * the same class or package relationship.
     */
    @Test
    void mergeClassRelationships_sumsWeightsForSameRelationship() {
        // Java: com.shared.A -> com.shared.B (weight 2)
        CodebaseGraphDTO javaDto = createDto("com.shared.A", "com.shared.B", 2.0, "com.shared", "com.shared");

        // Kotlin: com.shared.A -> com.shared.B (weight 3) - SAME relationship
        CodebaseGraphDTO kotlinDto = createDto("com.shared.A", "com.shared.B", 3.0, "com.shared", "com.shared");

        CodebaseGraphDTO merged = CompositeGraphBuilder.merge(javaDto, kotlinDto);

        Graph<String, DefaultWeightedEdge> mergedClassGraph = merged.getClassReferencesGraph();
        Graph<String, DefaultWeightedEdge> mergedPkgGraph = merged.getPackageReferencesGraph();

        // Class edge weight should be 2 + 3 = 5
        DefaultWeightedEdge mergedClassEdge = mergedClassGraph.getEdge("com.shared.A", "com.shared.B");
        assertNotNull(mergedClassEdge);
        assertEquals(5.0, mergedClassGraph.getEdgeWeight(mergedClassEdge));

        // Package graph has self-edge (intra-package), weight should be 2 + 3 = 5
        // (addPackageEdgeIfCrossPackage only adds for cross-package, so self-edge not added by mergeGraph)
        // The package self-edge comes from the dependency collector, not mergeGraph.
        // In mergeGraph, self-edges from source graphs ARE copied.
        DefaultWeightedEdge mergedPkgEdge = mergedPkgGraph.getEdge("com.shared", "com.shared");
        assertNotNull(mergedPkgEdge, "mergeGraph must copy the intra-package self-edge");
        assertEquals(5.0, mergedPkgGraph.getEdgeWeight(mergedPkgEdge));
    }

    /**
     * Tests that relationships affected by fabricated-vertex reconciliation
     * are correctly rebuilt to reference the final merged edges.
     */
    @Test
    void mergeClassRelationships_reconciliationRebuildsRelationshipMap() {
        // Java DTO with a fabricated cross-language reference:
        // Java class com.java.Caller references Kotlin class com.kotlin.Target
        // but Java parser fabricates com.java.Target (wrong package)
        Graph<String, DefaultWeightedEdge> javaClassGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        javaClassGraph.addVertex("com.java.Caller");
        javaClassGraph.addVertex("com.java.Target"); // fabricated
        DefaultWeightedEdge javaEdge = javaClassGraph.addEdge("com.java.Caller", "com.java.Target");
        javaClassGraph.setEdgeWeight(javaEdge, 2);

        Graph<String, DefaultWeightedEdge> javaPkgGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        javaPkgGraph.addVertex("com.java");
        javaPkgGraph.addEdge("com.java", "com.java"); // self-edge

        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> javaClassRels = new HashMap<>();
        javaClassRels.put(javaPkgGraph.getEdge("com.java", "com.java"), Set.of(javaEdge));

        Map<String, String> javaSourceMapping = new HashMap<>();
        javaSourceMapping.put("com.java.Caller", "/src/main/java/com/java/Caller.java");

        CodebaseGraphDTO javaDto = new CodebaseGraphDTO(
                javaClassGraph, javaPkgGraph, javaClassRels, javaSourceMapping, new ArrayList<>(), new ArrayList<>());

        // Kotlin DTO with the REAL target class
        Graph<String, DefaultWeightedEdge> kotlinClassGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        kotlinClassGraph.addVertex("com.kotlin.Target"); // real canonical
        kotlinClassGraph.addVertex("com.kotlin.Other");
        DefaultWeightedEdge kotlinEdge = kotlinClassGraph.addEdge("com.kotlin.Other", "com.kotlin.Target");
        kotlinClassGraph.setEdgeWeight(kotlinEdge, 3);

        Graph<String, DefaultWeightedEdge> kotlinPkgGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        kotlinPkgGraph.addVertex("com.kotlin");
        kotlinPkgGraph.addEdge("com.kotlin", "com.kotlin");

        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> kotlinClassRels = new HashMap<>();
        kotlinClassRels.put(kotlinPkgGraph.getEdge("com.kotlin", "com.kotlin"), Set.of(kotlinEdge));

        Map<String, String> kotlinSourceMapping = new HashMap<>();
        kotlinSourceMapping.put("com.kotlin.Target", "/src/main/kotlin/com/kotlin/Target.kt");
        kotlinSourceMapping.put("com.kotlin.Other", "/src/main/kotlin/com/kotlin/Other.kt");

        CodebaseGraphDTO kotlinDto = new CodebaseGraphDTO(
                kotlinClassGraph,
                kotlinPkgGraph,
                kotlinClassRels,
                kotlinSourceMapping,
                new ArrayList<>(),
                new ArrayList<>());

        // Merge - reconciliation should contract com.java.Target into com.kotlin.Target
        CodebaseGraphDTO merged = CompositeGraphBuilder.merge(javaDto, kotlinDto);

        Graph<String, DefaultWeightedEdge> mergedClassGraph = merged.getClassReferencesGraph();
        Graph<String, DefaultWeightedEdge> mergedPkgGraph = merged.getPackageReferencesGraph();
        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> relMap = merged.getClassRelationshipsInPackageRelationship();

        // Fabricated vertex should be gone
        assertFalse(
                mergedClassGraph.containsVertex("com.java.Target"),
                "Fabricated vertex should be removed by reconciliation");

        // Canonical vertex should exist
        assertTrue(mergedClassGraph.containsVertex("com.kotlin.Target"), "Canonical vertex should remain");

        // Edge from Caller should now point to canonical
        assertTrue(
                mergedClassGraph.containsEdge("com.java.Caller", "com.kotlin.Target"),
                "Edge should be redirected to canonical after reconciliation");

        // Verify relationship map was rebuilt after reconciliation
        // The package edge com.java -> com.kotlin should exist (cross-package)
        DefaultWeightedEdge crossPkgEdge = mergedPkgGraph.getEdge("com.java", "com.kotlin");
        assertNotNull(crossPkgEdge, "Cross-package edge should be created by reconciliation");

        // The relationship map should have the cross-package edge as key
        // and the redirected class edge as value
        assertTrue(relMap.containsKey(crossPkgEdge), "Relationship map should contain the cross-package edge");

        Set<DefaultWeightedEdge> classEdges = relMap.get(crossPkgEdge);
        assertNotNull(classEdges);
        assertEquals(1, classEdges.size());

        DefaultWeightedEdge redirectedEdge = classEdges.iterator().next();
        assertTrue(mergedClassGraph.containsEdge(redirectedEdge), "Redirected edge must belong to merged class graph");
        assertEquals("com.java.Caller", mergedClassGraph.getEdgeSource(redirectedEdge));
        assertEquals("com.kotlin.Target", mergedClassGraph.getEdgeTarget(redirectedEdge));
    }

    /**
     * End-to-end test: Java DTO has an anonymous inner class (Outer$1) that only exists
     * in the class graph (via NewClass) but NOT in the source mapping. Kotlin DTO has
     * the real enclosing class. Through merge, the anonymous class should be mapped to
     * the enclosing class's source path and preserved (not removed as external).
     */
    @Test
    void merge_javaAnonymousInnerClass_preservedThroughMergePipeline() {
        // Java DTO: Outer class with anonymous inner class Outer$1
        Graph<String, DefaultWeightedEdge> javaClassGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        javaClassGraph.addVertex("com.example.Outer");
        javaClassGraph.addVertex("com.example.Outer$1"); // anonymous inner class
        javaClassGraph.addVertex("com.example.Caller");

        DefaultWeightedEdge e1 = javaClassGraph.addEdge("com.example.Caller", "com.example.Outer");
        javaClassGraph.setEdgeWeight(e1, 1);
        DefaultWeightedEdge e2 = javaClassGraph.addEdge("com.example.Outer", "com.example.Outer$1");
        javaClassGraph.setEdgeWeight(e2, 1);

        Graph<String, DefaultWeightedEdge> javaPkgGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        javaPkgGraph.addVertex("com.example");
        javaPkgGraph.addEdge("com.example", "com.example");

        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> javaClassRels = new HashMap<>();
        javaClassRels.put(javaPkgGraph.getEdge("com.example", "com.example"), Set.of(e1, e2));

        Map<String, String> javaSourceMapping = new HashMap<>();
        javaSourceMapping.put("com.example.Outer", "/src/main/java/com/example/Outer.java");
        javaSourceMapping.put("com.example.Caller", "/src/main/java/com/example/Caller.java");
        // NOTE: Outer$1 is NOT in source mapping (no J.ClassDeclaration for anonymous class)

        CodebaseGraphDTO javaDto = new CodebaseGraphDTO(
                javaClassGraph, javaPkgGraph, javaClassRels, javaSourceMapping, new ArrayList<>(), new ArrayList<>());

        // Kotlin DTO: just another class in a different package
        Graph<String, DefaultWeightedEdge> kotlinClassGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        kotlinClassGraph.addVertex("com.other.KotlinClass");

        Graph<String, DefaultWeightedEdge> kotlinPkgGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        kotlinPkgGraph.addVertex("com.other");

        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> kotlinClassRels = new HashMap<>();

        Map<String, String> kotlinSourceMapping = new HashMap<>();
        kotlinSourceMapping.put("com.other.KotlinClass", "/src/main/kotlin/com/other/KotlinClass.kt");

        CodebaseGraphDTO kotlinDto = new CodebaseGraphDTO(
                kotlinClassGraph,
                kotlinPkgGraph,
                kotlinClassRels,
                kotlinSourceMapping,
                new ArrayList<>(),
                new ArrayList<>());

        // Merge - anonymous class mapping should happen before reconciliation
        CodebaseGraphDTO merged = CompositeGraphBuilder.merge(javaDto, kotlinDto);

        Graph<String, DefaultWeightedEdge> mergedClassGraph = merged.getClassReferencesGraph();

        // Anonymous class should be preserved (mapped to Outer's source path during merge)
        assertTrue(
                mergedClassGraph.containsVertex("com.example.Outer$1"),
                "Anonymous inner class should be preserved through merge pipeline");

        // Edge to anonymous class should remain
        assertTrue(
                mergedClassGraph.containsEdge("com.example.Outer", "com.example.Outer$1"),
                "Edge to anonymous inner class should remain after merge");
    }

    /**
     * Helper to create a simple DTO with one class edge and corresponding package relationship.
     */
    private static CodebaseGraphDTO createDto(
            String classSource, String classTarget, double weight, String pkgSource, String pkgTarget) {

        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex(classSource);
        classGraph.addVertex(classTarget);
        DefaultWeightedEdge classEdge = classGraph.addEdge(classSource, classTarget);
        classGraph.setEdgeWeight(classEdge, weight);

        Graph<String, DefaultWeightedEdge> pkgGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        pkgGraph.addVertex(pkgSource);
        pkgGraph.addVertex(pkgTarget);
        DefaultWeightedEdge pkgEdge = pkgGraph.addEdge(pkgSource, pkgTarget);
        pkgGraph.setEdgeWeight(pkgEdge, weight);

        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> classRels = new HashMap<>();
        classRels.put(pkgEdge, Set.of(classEdge));

        Map<String, String> sourceMapping = new HashMap<>();
        sourceMapping.put(classSource, "/src/main/java/" + classSource.replace(".", "/") + ".java");
        sourceMapping.put(classTarget, "/src/main/java/" + classTarget.replace(".", "/") + ".java");

        return new CodebaseGraphDTO(
                classGraph, pkgGraph, classRels, sourceMapping, new ArrayList<>(), new ArrayList<>());
    }
}
