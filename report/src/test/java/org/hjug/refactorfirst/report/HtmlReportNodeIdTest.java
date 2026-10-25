package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.VertexInfo;
import org.hjug.refactorfirst.report.model.ClassNodeDTO;
import org.hjug.refactorfirst.report.model.HierarchyDTO;
import org.hjug.refactorfirst.report.model.PackageNodeDTO;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Test;

class HtmlReportNodeIdTest {

    @Test
    void allOverloadsProduceDistinctAsciiIdsOutsideDotKeywords() {
        // Given: Unicode, keywords, and names that collide under the old transformations,
        // including anonymous vertices mapped to identically named source files.
        List<String> names = List.of(
                "p.漢字",
                "p.𐐀",
                "node",
                "NoDe",
                "strict",
                "graph",
                "digraph",
                "edge",
                "subgraph",
                "p.Outer$Inner",
                "p.Outer_Inner",
                "p.a.B",
                "p_a.B",
                "p.a_B",
                "p.A<B>",
                "p.Alt_B_gt",
                "<anonymous>",
                "lt_anonymous_gt",
                "p.Aa.<anonymous>",
                "p.BB.<anonymous>",
                "p.Owner_anonymous",
                "p.9Lives",
                "_",
                "_005f");
        Graph<VertexInfo, DefaultWeightedEdge> graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        names.forEach(name -> graph.addVertex(VertexInfo.forName(name)));
        CodebaseGraphDTO dto = new CodebaseGraphDTO(
                null,
                null,
                Map.of(),
                Map.of("p.Aa.<anonymous>", "src/漢字.kt", "p.BB.<anonymous>", "src/漢字.kt"),
                List.of(),
                List.of());
        HtmlReport report = new HtmlReport();
        List<Function<String, String>> encoders = List.of(
                HtmlReport::renderSafeNodeId,
                name -> report.renderSafeNodeId(name, dto),
                name -> report.renderSafeNodeId(name, graph),
                name -> HtmlReport.renderSafeNodeId(name, graph, dto));

        // When: every overload encodes these distinct class names.
        for (Function<String, String> encoder : encoders) {
            Set<String> ids = new HashSet<>();
            for (String name : names) {
                String id = encoder.apply(name);

                // Then: the viewer accepts each id, and no two vertices share an id.
                assertTrue(id.matches("[A-Za-z_][A-Za-z0-9_]*"), name + " -> " + id);
                assertFalse(id.matches("(?i:strict|graph|digraph|node|edge|subgraph)"), id);
                assertTrue(ids.add(id), "Duplicate id for " + name + ": " + id);
                assertEquals(HtmlReport.renderSafeNodeId(name), id, "Overloads must agree on class identity");
            }
        }
    }

    @Test
    void hierarchyKeepsOriginalLabelsAndMapsEveryEdgeToEncodedNodes() {
        // Given: a connected cycle with a non-Latin-1 name, a keyword, inner-class
        // transformation collisions, and two source-mapped anonymous vertices.
        List<String> names =
                List.of("p.漢字", "node", "p.Outer$Inner", "p.Outer_Inner", "p.Aa.<anonymous>", "p.BB.<anonymous>");
        Graph<VertexInfo, DefaultWeightedEdge> graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        names.forEach(name -> graph.addVertex(VertexInfo.forName(name)));
        for (int i = 0; i < names.size(); i++) {
            graph.addEdge(VertexInfo.forName(names.get(i)), VertexInfo.forName(names.get((i + 1) % names.size())));
        }
        CodebaseGraphDTO dto = new CodebaseGraphDTO(
                null,
                null,
                Map.of(),
                Map.of("p.Aa.<anonymous>", "src/漢字.kt", "p.BB.<anonymous>", "src/漢字.kt"),
                List.of(),
                List.of());

        // When: the producer resolves class ids and edges into the same hierarchy DTO.
        HierarchyDTO hierarchy = HierarchyBuilder.buildClassHierarchy(graph, dto, Set.of(), Set.of(), Set.of());
        Map<String, ClassNodeDTO> byName = flatten(hierarchy.getPackages())
                .collect(java.util.stream.Collectors.toMap(ClassNodeDTO::getFqn, Function.identity()));
        String dot = NestedDotBuilder.buildNestedDot(hierarchy, "", false);

        // Then: labels retain original names while nodes and edges use distinct, safe ids.
        assertEquals(names.size(), byName.size());
        assertEquals(
                names.size(),
                byName.values().stream().map(ClassNodeDTO::getId).distinct().count());
        assertEquals("class_p_002e_6f22_5b57", byName.get("p.漢字").getId());
        assertEquals("class_node", byName.get("node").getId());
        assertEquals("漢字", byName.get("p.漢字").getLabel());
        assertEquals("node", byName.get("node").getLabel());
        assertEquals("Outer$Inner", byName.get("p.Outer$Inner").getLabel());
        assertEquals("Outer_Inner", byName.get("p.Outer_Inner").getLabel());
        assertEquals("漢字$anonymous", byName.get("p.Aa.<anonymous>").getLabel());
        assertEquals(names.size(), hierarchy.getEdges().size());
        for (int i = 0; i < names.size(); i++) {
            String source = byName.get(names.get(i)).getId();
            String target = byName.get(names.get((i + 1) % names.size())).getId();
            assertTrue(source.matches("[A-Za-z_][A-Za-z0-9_]*"), source);
            assertFalse(source.matches("(?i:strict|graph|digraph|node|edge|subgraph)"), source);
            assertTrue(dot.contains(source + " ["));
            assertTrue(dot.contains(source + " -> " + target + " ["));
            assertTrue(hierarchy.getEdges().stream()
                    .anyMatch(edge ->
                            edge.getSource().equals(source) && edge.getTarget().equals(target)));
        }
        assertTrue(dot.contains("label=\"漢字\""));
        assertTrue(dot.contains("label=\"node\""));
    }

    private static Stream<ClassNodeDTO> flatten(List<PackageNodeDTO> packages) {
        return packages.stream().flatMap(pkg -> Stream.concat(pkg.getClasses().stream(), flatten(pkg.getChildren())));
    }
}
