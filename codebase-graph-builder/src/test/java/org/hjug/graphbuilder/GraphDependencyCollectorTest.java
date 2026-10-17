package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GraphDependencyCollector}.
 *
 * <p>Vertices are {@link VertexInfo} instances: the collector captures the
 * information needed to populate a VertexInfo instead of using only the class
 * or package name as the vertex.
 *
 * <p>Anonymous/synthetic classes (Java {@code Outer$1}/{code Outer$2}/{@code Outer$}, the literal
 * Kotlin {@code "<anonymous>"} string, Kotlin synthetic {@code $N} classes) are <em>first-class
 * graph members</em>: they can contain antipatterns and so are rendered with {@code $} as the
 * enclosing-class separator. The collector therefore does not sieve them out — only a self-edge
 * ({@code from == to}) is suppressed. Render-time sink filtering (vertices with no outgoing edges)
 * lives in {@link org.hjug.refactorfirst.report.HtmlReport}.
 *
 * <p>Pure-unit: in-memory JGraphT graphs, no OpenRewrite parser, no visitors.
 */
class GraphDependencyCollectorTest {

    /** Creates an empty directed weighted graph for dependency collection tests. */
    private static Graph<VertexInfo, DefaultWeightedEdge> newClassGraph() {
        return new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    }

    /** Asserts that a graph contains a vertex matching the supplied class or package name. */
    private static void assertHasVertex(Graph<VertexInfo, DefaultWeightedEdge> graph, String name) {
        VertexInfo vertex = VertexInfo.forName(name);
        assertTrue(graph.containsVertex(vertex), "expected vertex " + name);
    }

    /** Verifies that a Java anonymous source class participates in class dependencies. */
    @DisplayName("addClassDependency creates vertex + edge for a Java anonymous source")
    @Test
    void addClassDependency_javaAnonymousSource_addsVertex() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<VertexInfo, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.addClassDependency("com.foo.Outer$1", "com.foo.Real");

        assertHasVertex(classGraph, "com.foo.Outer$1");
        assertHasVertex(classGraph, "com.foo.Real");
        assertTrue(classGraph.containsEdge(VertexInfo.forName("com.foo.Outer$1"), VertexInfo.forName("com.foo.Real")));
        // same package -> no package edge, but the real package vertex is not required here either
        assertTrue(pkgGraph.edgeSet().isEmpty());
    }

    /** Verifies that an anonymous target class contributes both class and cross-package dependencies. */
    @DisplayName("addClassDependency creates vertex + edge for a Java anonymous target across packages")
    @Test
    void addClassDependency_javaAnonymousTarget_addsVertex() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<VertexInfo, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.addClassDependency("com.foo.Real", "com.bar.Outer$1");

        assertHasVertex(classGraph, "com.foo.Real");
        assertHasVertex(classGraph, "com.bar.Outer$1");
        assertTrue(classGraph.containsEdge(VertexInfo.forName("com.foo.Real"), VertexInfo.forName("com.bar.Outer$1")));
        // Java anonymous classes have a real package; cross-package must populate package graph
        assertHasVertex(pkgGraph, "com.foo");
        assertHasVertex(pkgGraph, "com.bar");
        assertEquals(1, pkgGraph.edgeSet().size());
    }

    /** Verifies that dependency endpoints retain their names and derived enclosing namespaces. */
    @DisplayName("addClassDependency captures VertexInfo with parent namespace instead of a bare name")
    @Test
    void addClassDependency_capturesVertexInfoWithParentNamespace() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<VertexInfo, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.addClassDependency("com.foo.Outer$Inner", "com.bar.Baz");

        VertexInfo source = classGraph.getEdgeSource(
                classGraph.getEdge(VertexInfo.forName("com.foo.Outer$Inner"), VertexInfo.forName("com.bar.Baz")));
        VertexInfo target = classGraph.getEdgeTarget(
                classGraph.getEdge(VertexInfo.forName("com.foo.Outer$Inner"), VertexInfo.forName("com.bar.Baz")));
        assertEquals("com.foo.Outer$Inner", source.getName());
        assertEquals("com.foo.Outer", source.getParentNamespace());
        assertEquals("com.bar.Baz", target.getName());
        assertEquals("com.bar", target.getParentNamespace());
    }

    /** Verifies that package dependency vertices identify their parent packages. */
    @DisplayName("package vertices carry their parent package as the parent namespace")
    @Test
    void addClassDependency_packageVerticesHaveParentPackageNamespace() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<VertexInfo, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.addClassDependency("com.foo.A", "com.bar.B");

        VertexInfo fromPackage =
                pkgGraph.getEdgeSource(pkgGraph.edgeSet().iterator().next());
        VertexInfo toPackage =
                pkgGraph.getEdgeTarget(pkgGraph.edgeSet().iterator().next());
        assertEquals("com.foo", fromPackage.getName());
        assertEquals("com", fromPackage.getParentNamespace());
        assertEquals("com.bar", toPackage.getName());
        assertEquals("com", toPackage.getParentNamespace());
    }

    /** Verifies that a packageless Kotlin anonymous source creates a class edge without a package edge. */
    @DisplayName("addClassDependency makes the Kotlin literal <anonymous> a vertex and edge")
    @Test
    void addClassDependency_kotlinAnonymousSource_addsVertex() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<VertexInfo, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.addClassDependency("<anonymous>", "com.bar.X");

        assertHasVertex(classGraph, "<anonymous>");
        assertHasVertex(classGraph, "com.bar.X");
        assertTrue(classGraph.containsEdge(VertexInfo.forName("<anonymous>"), VertexInfo.forName("com.bar.X")));
        // package graph: the <anonymous> source has no package, so no package edge is created
        // and neither an "" vertex nor the cross-package target vertex should appear.
        assertFalse(
                pkgGraph.vertexSet().stream().anyMatch(v -> v.getName().isEmpty()),
                "<anonymous> must not pollute the package graph with an empty package vertex");
        assertTrue(pkgGraph.edgeSet().isEmpty(), "no package edge should be created when the source package is empty");
    }

    /** Verifies that a packageless anonymous source creates neither an empty package vertex nor an edge. */
    @DisplayName("addPackageDependency with a Kotlin <anonymous> source does not create an empty package vertex")
    @Test
    void addPackageDependency_kotlinAnonymousSource_doesNotPollutePackageGraph() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<VertexInfo, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        DefaultWeightedEdge edge = collector.addPackageDependency("<anonymous>", "com.bar.Other");

        // getPackageFromFqn("<anonymous>") returns ""; "".equals("com.bar") is false, so a
        // package edge "" -> "com.bar" would otherwise be created. Assert it is not.
        assertFalse(
                pkgGraph.vertexSet().stream().anyMatch(v -> v.getName().isEmpty()),
                "must not register the empty string as a package vertex");
        // No edge should be created because the source package ("") is degenerate; collector must
        // treat an empty source package as a no-op.
        assertNull(edge, "addPackageDependency must signal a no-op when the source package is empty");
        assertTrue(pkgGraph.edgeSet().isEmpty());
    }

    /** Verifies that explicit registration retains anonymous and synthetic class vertices. */
    @DisplayName("registerClassVertex registers Java anonymous and Kotlin <anonymous> FQNs")
    @Test
    void registerClassVertex_syntheticFqn_registered() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<VertexInfo, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.registerClassVertex("<anonymous>");
        collector.registerClassVertex("com.foo.Outer$1");
        collector.registerClassVertex("com.foo.Outer$2");

        assertHasVertex(classGraph, "<anonymous>");
        assertHasVertex(classGraph, "com.foo.Outer$1");
        assertHasVertex(classGraph, "com.foo.Outer$2");
        assertEquals(3, classGraph.vertexSet().size());
    }

    /** Verifies that explicit registration adds a named class without requiring dependencies. */
    @DisplayName("registerClassVertex registers real FQNs (control)")
    @Test
    void registerClassVertex_realFqn_registered() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<VertexInfo, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.registerClassVertex("com.foo.Real");

        assertHasVertex(classGraph, "com.foo.Real");
        assertEquals(1, classGraph.vertexSet().size());
    }

    /** Verifies that the collector preserves the raw source location reported for a class. */
    @DisplayName("recordClassLocation captures the location reported during collection")
    @Test
    void recordClassLocation_capturesLocation() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<VertexInfo, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.registerClassVertex("com.foo.Real");
        collector.recordClassLocation("com.foo.Real", "file:///repo/src/main/java/com/foo/Real.java");

        assertEquals(
                "file:///repo/src/main/java/com/foo/Real.java",
                collector.getRecordedClassLocations().get("com.foo.Real"));
    }

    /** Verifies that a cross-package class dependency populates both graphs and their relationship mapping. */
    @DisplayName("addClassDependency happy path still populates class + package graphs")
    @Test
    void addClassDependency_realCrossPackageDeps_populatesBothGraphs() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<VertexInfo, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.addClassDependency("com.foo.A", "com.bar.B");

        assertHasVertex(classGraph, "com.foo.A");
        assertHasVertex(classGraph, "com.bar.B");
        assertTrue(classGraph.containsEdge(VertexInfo.forName("com.foo.A"), VertexInfo.forName("com.bar.B")));
        assertEquals(1, classGraph.edgeSet().size());

        assertHasVertex(pkgGraph, "com.foo");
        assertHasVertex(pkgGraph, "com.bar");
        assertEquals(1, pkgGraph.edgeSet().size());

        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> relationships =
                collector.getClassRelationshipsInPackageRelationship();
        assertEquals(1, relationships.size(), "cross-package dependency must be tracked");
    }

    /** Verifies that a dependency within one package creates only the class relationship. */
    @DisplayName("addClassDependency within the same package still records a class edge and no package edge")
    @Test
    void addClassDependency_realSamePackageDep_recordsClassEdgeOnly() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<VertexInfo, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.addClassDependency("com.foo.A", "com.foo.B");

        assertTrue(classGraph.containsEdge(VertexInfo.forName("com.foo.A"), VertexInfo.forName("com.foo.B")));
        assertTrue(pkgGraph.edgeSet().isEmpty(), "same-package dependency must not create a package edge");
        assertTrue(collector.getClassRelationshipsInPackageRelationship().isEmpty());
    }
}
