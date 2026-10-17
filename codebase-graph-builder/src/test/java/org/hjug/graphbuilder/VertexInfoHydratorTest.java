package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Given-When-Then unit tests for {@link VertexInfoHydrator}, which populates
 * {@link VertexInfo#getLocationOnDisk()} on class- and package-graph vertices
 * from the canonical repository-relative class-to-source-file mapping after
 * the visitor walk.
 */
class VertexInfoHydratorTest {

    /** Creates an empty graph for testing class and package location hydration. */
    private static Graph<VertexInfo, DefaultWeightedEdge> newGraph() {
        return new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    }

    /** Returns the stored graph vertex with the supplied name so its hydrated metadata can be inspected. */
    private static VertexInfo vertex(Graph<VertexInfo, DefaultWeightedEdge> graph, String name) {
        return graph.vertexSet().stream()
                .filter(v -> v.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    /** Verifies that a directly mapped class receives its repository-relative source path. */
    @DisplayName("Hydration sets the repo-relative source path for mapped classes")
    @Test
    void hydrate_mappedClass_getsLocationOnDisk() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        classGraph.addVertex(VertexInfo.forName("com.foo.Bar"));
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        Map<String, String> mapping = new HashMap<>();
        mapping.put("com.foo.Bar", "src/main/java/com/foo/Bar.java");

        VertexInfoHydrator.hydrate(classGraph, packageGraph, mapping);

        assertEquals(
                "src/main/java/com/foo/Bar.java",
                vertex(classGraph, "com.foo.Bar").getLocationOnDisk());
    }

    /** Verifies that a nested class inherits its outer class's mapped source location. */
    @DisplayName("Hydration resolves a nested class through its enclosing class's mapping")
    @Test
    void hydrate_nestedClass_fallsBackToEnclosingClassMapping() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        classGraph.addVertex(VertexInfo.forName("com.foo.Outer$Inner"));
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        Map<String, String> mapping = new HashMap<>();
        mapping.put("com.foo.Outer", "src/main/java/com/foo/Outer.java");

        VertexInfoHydrator.hydrate(classGraph, packageGraph, mapping);

        assertEquals(
                "src/main/java/com/foo/Outer.java",
                vertex(classGraph, "com.foo.Outer$Inner").getLocationOnDisk());
    }

    /** Verifies that a Kotlin anonymous vertex uses its enclosing class's source location. */
    @DisplayName("Hydration resolves a Kotlin <anonymous> vertex through its enclosing mapping")
    @Test
    void hydrate_kotlinAnonymous_fallsBackToEnclosingMapping() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        classGraph.addVertex(VertexInfo.forName("dev.Control.<anonymous>"));
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        Map<String, String> mapping = new HashMap<>();
        mapping.put("dev.Control", "src/main/kotlin/dev/Control.kt");

        VertexInfoHydrator.hydrate(classGraph, packageGraph, mapping);

        assertEquals(
                "src/main/kotlin/dev/Control.kt",
                vertex(classGraph, "dev.Control.<anonymous>").getLocationOnDisk());
    }

    /** Verifies that an unmapped class keeps its parent namespace and an unknown disk location. */
    @DisplayName("Hydration leaves an unmapped class location null but keeps the derived parent namespace")
    @Test
    void hydrate_unmappedClass_locationStaysNullAndParentNamespaceIsKept() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        classGraph.addVertex(VertexInfo.forName("com.foo.Unmapped"));
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        Map<String, String> mapping = new HashMap<>();

        VertexInfoHydrator.hydrate(classGraph, packageGraph, mapping);

        assertNull(vertex(classGraph, "com.foo.Unmapped").getLocationOnDisk());
        assertEquals("com.foo", vertex(classGraph, "com.foo.Unmapped").getParentNamespace());
    }

    /** Verifies that a package location is its direct member's directory and its parent namespace is retained. */
    @DisplayName("Hydration sets a package vertex's location to a member file's directory")
    @Test
    void hydrate_packageVertex_getsMemberFileDirectory() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        packageGraph.addVertex(VertexInfo.forName("com.foo"));
        Map<String, String> mapping = new HashMap<>();
        mapping.put("com.foo.Bar", "src/main/java/com/foo/Bar.java");
        mapping.put("com.foo.Baz", "src/main/java/com/foo/Baz.java");

        VertexInfoHydrator.hydrate(classGraph, packageGraph, mapping);

        assertEquals("src/main/java/com/foo", vertex(packageGraph, "com.foo").getLocationOnDisk());
        assertEquals("com", vertex(packageGraph, "com.foo").getParentNamespace());
    }

    /** Verifies that a package without mapped members has no disk location. */
    @DisplayName("Hydration leaves a package without mapped members location null")
    @Test
    void hydrate_packageWithoutMembers_locationStaysNull() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        packageGraph.addVertex(VertexInfo.forName("com.bare"));
        Map<String, String> mapping = new HashMap<>();

        VertexInfoHydrator.hydrate(classGraph, packageGraph, mapping);

        assertNull(vertex(packageGraph, "com.bare").getLocationOnDisk());
    }

    /** Verifies that subpackage members cannot supply a parent package location. */
    @DisplayName("A package whose only mapped members live in subpackages keeps a null location")
    @Test
    void hydrate_packageWithOnlySubpackageMembers_locationStaysNull() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        packageGraph.addVertex(VertexInfo.forName("com.foo"));
        Map<String, String> mapping = new HashMap<>();
        mapping.put("com.foo.bar.Baz", "src/main/java/com/foo/bar/Baz.java");

        VertexInfoHydrator.hydrate(classGraph, packageGraph, mapping);

        assertNull(
                vertex(packageGraph, "com.foo").getLocationOnDisk(),
                "a subpackage member's directory must not be used as the parent package's location");
    }

    /** Verifies that a nested Java class mapping can locate its enclosing package's directory. */
    @DisplayName("A nested $ class is a direct member of its outer package for location resolution")
    @Test
    void hydrate_packageVertex_nestedDollarClassCountsAsDirectMember() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        packageGraph.addVertex(VertexInfo.forName("com.foo"));
        Map<String, String> mapping = new HashMap<>();
        mapping.put("com.foo.Outer$Inner", "src/main/java/com/foo/Outer.java");

        VertexInfoHydrator.hydrate(classGraph, packageGraph, mapping);

        assertEquals("src/main/java/com/foo", vertex(packageGraph, "com.foo").getLocationOnDisk());
    }

    /** Verifies that a Kotlin anonymous class mapping contributes to its enclosing package location. */
    @DisplayName("A Kotlin <anonymous> member is a direct member of its package for location resolution")
    @Test
    void hydrate_packageVertex_kotlinAnonymousCountsAsDirectMember() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        packageGraph.addVertex(VertexInfo.forName("com.foo"));
        Map<String, String> mapping = new HashMap<>();
        mapping.put("com.foo.Outer.<anonymous>", "src/main/kotlin/com/foo/Outer.kt");

        VertexInfoHydrator.hydrate(classGraph, packageGraph, mapping);

        assertEquals("src/main/kotlin/com/foo", vertex(packageGraph, "com.foo").getLocationOnDisk());
    }

    /** Verifies that package hydration selects the shortest direct-member path and ignores subpackage paths. */
    @DisplayName("A package with multiple direct members resolves to the shortest mapped member path")
    @Test
    void hydrate_packageVertex_selectsShortestDirectMemberPath() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        packageGraph.addVertex(VertexInfo.forName("com.foo"));
        Map<String, String> mapping = new HashMap<>();
        mapping.put("com.foo.Bar", "module-a/src/main/java/com/foo/Bar.java");
        mapping.put("com.foo.Baz", "src/com/foo/Baz.java");
        mapping.put("com.foo.qux.Quux", "src/com/foo/qux/Quux.java");

        VertexInfoHydrator.hydrate(classGraph, packageGraph, mapping);

        assertEquals("src/com/foo", vertex(packageGraph, "com.foo").getLocationOnDisk());
    }

    /** Verifies that repeated hydration with the same mapping preserves class and package locations. */
    @DisplayName("Hydration is idempotent: re-hydrating does not change populated locations")
    @Test
    void hydrate_twice_isIdempotent() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newGraph();
        classGraph.addVertex(VertexInfo.forName("com.foo.Bar"));
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = newGraph();
        packageGraph.addVertex(VertexInfo.forName("com.foo"));
        Map<String, String> mapping = new HashMap<>();
        mapping.put("com.foo.Bar", "src/main/java/com/foo/Bar.java");

        VertexInfoHydrator.hydrate(classGraph, packageGraph, mapping);
        VertexInfoHydrator.hydrate(classGraph, packageGraph, mapping);

        assertEquals(
                "src/main/java/com/foo/Bar.java",
                vertex(classGraph, "com.foo.Bar").getLocationOnDisk());
        assertEquals("src/main/java/com/foo", vertex(packageGraph, "com.foo").getLocationOnDisk());
    }
}
