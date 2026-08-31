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
 * <p>Anonymous/synthetic classes (Java {@code Outer$1}/{@code Outer$2}/{@code Outer$}, the literal
 * Kotlin {@code "<anonymous>"} string, Kotlin synthetic {@code $N} classes) are <em>first-class
 * graph members</em>: they can contain antipatterns and so are rendered with {@code $} as the
 * enclosing-class separator. The collector therefore no longer sieves them out — only a self-edge
 * ({@code from == to}) is suppressed. Render-time sink filtering (vertices with no outgoing edges)
 * lives in {@link org.hjug.refactorfirst.report.HtmlReport}.
 *
 * <p>Pure-unit: in-memory JGraphT graphs, no OpenRewrite parser, no visitors.
 */
class GraphDependencyCollectorTest {

    private static Graph<String, DefaultWeightedEdge> newClassGraph() {
        return new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    }

    @DisplayName("addClassDependency creates vertex + edge for a Java anonymous source")
    @Test
    void addClassDependency_javaAnonymousSource_addsVertex() {
        Graph<String, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<String, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.addClassDependency("com.foo.Outer$1", "com.foo.Real");

        assertTrue(classGraph.containsVertex("com.foo.Outer$1"), "Java anonymous source must be a vertex");
        assertTrue(classGraph.containsVertex("com.foo.Real"));
        assertTrue(classGraph.containsEdge("com.foo.Outer$1", "com.foo.Real"));
        // same package -> no package edge, but the real package vertex is not required here either
        assertTrue(pkgGraph.edgeSet().isEmpty());
    }

    @DisplayName("addClassDependency creates vertex + edge for a Java anonymous target across packages")
    @Test
    void addClassDependency_javaAnonymousTarget_addsVertex() {
        Graph<String, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<String, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.addClassDependency("com.foo.Real", "com.bar.Outer$1");

        assertTrue(classGraph.containsVertex("com.foo.Real"));
        assertTrue(classGraph.containsVertex("com.bar.Outer$1"), "Java anonymous target must be a vertex");
        assertTrue(classGraph.containsEdge("com.foo.Real", "com.bar.Outer$1"));
        // Java anonymous classes have a real package; cross-package must populate package graph
        assertTrue(pkgGraph.containsVertex("com.foo"));
        assertTrue(pkgGraph.containsVertex("com.bar"));
        assertEquals(1, pkgGraph.edgeSet().size());
    }

    @DisplayName("addClassDependency makes the Kotlin literal <anonymous> a vertex and edge")
    @Test
    void addClassDependency_kotlinAnonymousSource_addsVertex() {
        Graph<String, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<String, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.addClassDependency("<anonymous>", "com.bar.X");

        assertTrue(classGraph.containsVertex("<anonymous>"), "Kotlin <anonymous> must be a vertex");
        assertTrue(classGraph.containsVertex("com.bar.X"));
        assertTrue(classGraph.containsEdge("<anonymous>", "com.bar.X"));
        // package graph: the <anonymous> source has no package, so no package edge is created
        // and neither an "" vertex nor the cross-package target vertex should appear.
        assertFalse(
                pkgGraph.containsVertex(""),
                "<anonymous> must not pollute the package graph with an empty package vertex");
        assertTrue(pkgGraph.edgeSet().isEmpty(), "no package edge should be created when the source package is empty");
    }

    @DisplayName("addPackageDependency with a Kotlin <anonymous> source does not create an empty package vertex")
    @Test
    void addPackageDependency_kotlinAnonymousSource_doesNotPollutePackageGraph() {
        Graph<String, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<String, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        DefaultWeightedEdge edge = collector.addPackageDependency("<anonymous>", "com.bar.Other");

        // getPackageFromFqn("<anonymous>") returns ""; "".equals("com.bar") is false, so a
        // package edge "" -> "com.bar" would otherwise be created. Assert it is not.
        assertFalse(pkgGraph.containsVertex(""), "must not register the empty string as a package vertex");
        // No edge should be created because the source package ("") is degenerate; collector must
        // treat an empty source package as a no-op.
        assertNull(edge, "addPackageDependency must signal a no-op when the source package is empty");
        assertTrue(pkgGraph.edgeSet().isEmpty());
    }

    @DisplayName("registerClassVertex registers Java anonymous and Kotlin <anonymous> FQNs")
    @Test
    void registerClassVertex_syntheticFqn_registered() {
        Graph<String, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<String, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.registerClassVertex("<anonymous>");
        collector.registerClassVertex("com.foo.Outer$1");
        collector.registerClassVertex("com.foo.Outer$2");

        assertTrue(classGraph.containsVertex("<anonymous>"));
        assertTrue(classGraph.containsVertex("com.foo.Outer$1"));
        assertTrue(classGraph.containsVertex("com.foo.Outer$2"));
        assertEquals(3, classGraph.vertexSet().size());
    }

    @DisplayName("registerClassVertex registers real FQNs (control)")
    @Test
    void registerClassVertex_realFqn_registered() {
        Graph<String, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<String, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.registerClassVertex("com.foo.Real");

        assertTrue(classGraph.containsVertex("com.foo.Real"));
        assertEquals(1, classGraph.vertexSet().size());
    }

    @DisplayName("addClassDependency happy path still populates class + package graphs")
    @Test
    void addClassDependency_realCrossPackageDeps_populatesBothGraphs() {
        Graph<String, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<String, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.addClassDependency("com.foo.A", "com.bar.B");

        assertTrue(classGraph.containsVertex("com.foo.A"));
        assertTrue(classGraph.containsVertex("com.bar.B"));
        assertTrue(classGraph.containsEdge("com.foo.A", "com.bar.B"));
        assertEquals(1, classGraph.edgeSet().size());

        assertTrue(pkgGraph.containsVertex("com.foo"));
        assertTrue(pkgGraph.containsVertex("com.bar"));
        assertEquals(1, pkgGraph.edgeSet().size());

        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> relationships =
                collector.getClassRelationshipsInPackageRelationship();
        assertEquals(1, relationships.size(), "cross-package dependency must be tracked");
    }

    @DisplayName("addClassDependency within the same package still records a class edge and no package edge")
    @Test
    void addClassDependency_realSamePackageDep_recordsClassEdgeOnly() {
        Graph<String, DefaultWeightedEdge> classGraph = newClassGraph();
        Graph<String, DefaultWeightedEdge> pkgGraph = newClassGraph();
        GraphDependencyCollector collector = new GraphDependencyCollector(classGraph, pkgGraph);

        collector.addClassDependency("com.foo.A", "com.foo.B");

        assertTrue(classGraph.containsEdge("com.foo.A", "com.foo.B"));
        assertTrue(pkgGraph.edgeSet().isEmpty(), "same-package dependency must not create a package edge");
        assertTrue(collector.getClassRelationshipsInPackageRelationship().isEmpty());
    }
}
