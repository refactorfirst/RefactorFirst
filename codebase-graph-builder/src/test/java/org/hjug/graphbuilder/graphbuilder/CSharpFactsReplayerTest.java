package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.hjug.graphbuilder.GraphDependencyCollector;
import org.hjug.graphbuilder.VertexInfo;
import org.hjug.graphbuilder.metrics.ClassMetrics;
import org.hjug.graphbuilder.metrics.GraphMetricsCollector;
import org.hjug.graphbuilder.metrics.MethodMetrics;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Test;

/**
 * Reads the facts file that csharp-fact-extractor produces for its TinyGreeter fixture.
 * The extractor's own ContractTests keep that file in step with the C# side.
 */
class CSharpFactsReplayerTest {

    @Test
    void givenTinyGreeterFacts_whenReplayed_thenGraphHasTheCycleAndMetrics() throws Exception {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        GraphDependencyCollector dependencies = new GraphDependencyCollector(classGraph, packageGraph);
        GraphMetricsCollector metrics = new GraphMetricsCollector(classGraph, packageGraph);
        Map<String, String> classToFile = new HashMap<>();

        try (InputStream in = getClass().getResourceAsStream("/csharp/tiny-greeter-facts.json")) {
            CSharpFactsReplayer.replay(CSharpFacts.read(in), dependencies, metrics, classToFile);
        }

        assertEquals(3, classGraph.vertexSet().size());
        assertEquals(4.0, weight(classGraph, "TinyGreeter.Greeter", "TinyGreeter.MessageStore"));
        assertEquals(2.0, weight(classGraph, "TinyGreeter.MessageStore", "TinyGreeter.Greeter"));
        assertEquals(3.0, weight(classGraph, "TinyGreeter.Program", "TinyGreeter.Greeter"));
        assertEquals("Greeter.cs", classToFile.get("TinyGreeter.Greeter"));
        assertTrue(dependencies.getPackagesInCodebase().contains("TinyGreeter"));

        ClassMetrics greeter = metrics.getClassMetrics("TinyGreeter.Greeter");
        assertEquals(2, greeter.getAttributes().size());
        assertEquals(0, greeter.getNumberOfPublicAttributes());
        assertEquals(4, greeter.getNumberOfMethods());
        assertEquals(2, greeter.getNumberOfAccessorMethods());
        assertEquals(5, greeter.getWeightedMethodCount());

        MethodMetrics greet = greeter.getMethods().get("Greet(string)");
        assertEquals(2, greet.getCouplingIntensity());
        assertEquals(2, greet.getNumberOfAccessedVariables());
        assertEquals(1, greet.getMaxNestingDepth());
    }

    /**
     * End to end through the real .NET extractor: methods are kept one per signature, so overloads
     * that differ only by a type parameter or by ref/out, and a static constructor beside an instance
     * one, must each arrive under their own signature. Runs only when the extractor is configured.
     */
    @Test
    void givenRealExtractor_whenOverloadsAreReplayed_thenEveryMethodIsKept() throws Exception {
        String extractor = System.getenv(CSharpExtractorRunner.EXTRACTOR_ENV);
        assumeTrue(extractor != null && Files.exists(Path.of(extractor)), "extractor not configured");
        Path fixture = Path.of("..", "csharp-fact-extractor", "fixtures", "Members")
                .toAbsolutePath()
                .normalize();
        assumeTrue(Files.isDirectory(fixture), "fixture not found");

        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        GraphDependencyCollector dependencies = new GraphDependencyCollector(classGraph, packageGraph);
        GraphMetricsCollector metrics = new GraphMetricsCollector(classGraph, packageGraph);

        Path facts = new CSharpExtractorRunner().run(CSharpExtractorRunner.findTargets(fixture), fixture);
        try (InputStream in = Files.newInputStream(facts)) {
            CSharpFactsReplayer.replay(CSharpFacts.read(in), dependencies, metrics, new HashMap<>());
        } finally {
            Files.deleteIfExists(facts);
        }

        assertEquals(
                Set.of("Add(int)", "Add(ref int)", "Read(out int)", "Save()", "Save<T>()"),
                metrics.getClassMetrics("Members.Overloads").getMethods().keySet());
        assertEquals(
                Set.of("Cache()", "static Cache()", "Size()"),
                metrics.getClassMetrics("Members.Cache").getMethods().keySet());
    }

    @Test
    void givenASubclassInTheFacts_whenReplayed_thenParentOverridesAndUsedMembersReachTheMetrics() throws Exception {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        GraphDependencyCollector dependencies = new GraphDependencyCollector(classGraph, packageGraph);
        GraphMetricsCollector metrics = new GraphMetricsCollector(classGraph, packageGraph);

        try (InputStream in = getClass().getResourceAsStream("/csharp/two-projects-facts.json")) {
            CSharpFactsReplayer.replay(CSharpFacts.read(in), dependencies, metrics, new HashMap<>());
        }

        ClassMetrics parent = metrics.getClassMetrics("Beta.BaseStore");
        ClassMetrics child = metrics.getClassMetrics("Beta.BetaStore");
        assertEquals(3, parent.getNumberOfProtectedMembers());
        assertEquals("Beta.BaseStore", child.getParentClass());
        assertEquals(1, child.getNumberOfOverriddenMethods());
        assertEquals(2, child.getNumberOfUsedParentMembers());
    }

    @Test
    void givenUnknownSchemaVersion_whenRead_thenRejectedWithAClearMessage() {
        String json = "{\"schemaVersion\": 99, \"root\": \".\", \"namespaces\": [], \"types\": []}";

        IOException e = assertThrows(
                IOException.class,
                () -> CSharpFacts.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));

        assertTrue(e.getMessage().contains("99"));
    }

    @Test
    void givenUnknownFields_whenRead_thenTheyAreIgnored() throws Exception {
        String json =
                "{\"schemaVersion\": 1, \"root\": \".\", \"somethingNew\": true, \"namespaces\": [], \"types\": []}";

        CSharpFacts facts = CSharpFacts.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals(0, facts.types().size());
    }

    private static double weight(Graph<VertexInfo, DefaultWeightedEdge> graph, String from, String to) {
        VertexInfo source = graph.vertexSet().stream()
                .filter(v -> v.getName().equals(from))
                .findFirst()
                .orElseThrow();
        VertexInfo target = graph.vertexSet().stream()
                .filter(v -> v.getName().equals(to))
                .findFirst()
                .orElseThrow();
        return graph.getEdgeWeight(graph.getEdge(source, target));
    }
}
