package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.refactorfirst.report.model.ClassRelationshipDTO;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The htmlReport and the jsonReport both render the class relationships that must be removed to
 * break a package relationship. Those class edges are stored in a
 * {@code HashSet<DefaultWeightedEdge>} whose iteration order is identity-hash based, so the two
 * report types (and repeated runs of the same report) could list the same relationships in
 * different orders. These tests pin a deterministic, source-ordered rendering shared by both
 * reports.
 */
class PackageRelationshipClassEdgeOrderTest {

    private static final String PKG_A = "org.example.pkgA";
    private static final String PKG_B = "org.example.pkgB";
    private static final String REPO_URL = "https://github.com/example/repo/blob/";

    /** Number of class edges crossing the package boundary. */
    private static final int EDGE_COUNT = 12;

    /** Builds the class graph with {@link #EDGE_COUNT} edges from {@link #PKG_A} to {@link #PKG_B}. */
    private Graph<String, DefaultWeightedEdge> createClassGraph() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < EDGE_COUNT; i++) {
            String source = PKG_A + ".Class" + (char) ('A' + i);
            String target = PKG_B + ".Target" + (char) ('A' + i);
            classGraph.addVertex(source);
            classGraph.addVertex(target);
            DefaultWeightedEdge edge = classGraph.addEdge(source, target);
            classGraph.setEdgeWeight(edge, i + 1);
        }
        return classGraph;
    }

    /** Builds a package graph with a single A -> B edge. */
    private Graph<String, DefaultWeightedEdge> createPackageGraph() {
        Graph<String, DefaultWeightedEdge> packageGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        packageGraph.addVertex(PKG_A);
        packageGraph.addVertex(PKG_B);
        packageGraph.addEdge(PKG_A, PKG_B);
        return packageGraph;
    }

    /** Inserts the given edges into a fresh HashSet in the given order. */
    private Set<DefaultWeightedEdge> newSetInOrder(Collection<DefaultWeightedEdge> edges, int rotation) {
        List<DefaultWeightedEdge> rotated = new ArrayList<>(edges);
        Collections.rotate(rotated, rotation);
        return new HashSet<>(rotated);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = SimpleHtmlReport.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private CodebaseGraphDTO mockDto(DefaultWeightedEdge packageEdge, Set<DefaultWeightedEdge> classEdges) {
        CodebaseGraphDTO dto = Mockito.mock(CodebaseGraphDTO.class);
        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> relationships = new HashMap<>();
        relationships.put(packageEdge, classEdges);
        Mockito.when(dto.getClassRelationshipsInPackageRelationship()).thenReturn(relationships);
        Mockito.when(dto.getClassToSourceFilePathMapping()).thenReturn(new HashMap<>());
        return dto;
    }

    private RankedDisharmony mockRankedDisharmony(DefaultWeightedEdge packageEdge) {
        RankedDisharmony edgeInfo = Mockito.mock(RankedDisharmony.class);
        Mockito.when(edgeInfo.getEdge()).thenReturn(packageEdge);
        Mockito.when(edgeInfo.getPriority()).thenReturn(1);
        Mockito.when(edgeInfo.getCycleCount()).thenReturn(1);
        Mockito.when(edgeInfo.getEffortRank()).thenReturn(1);
        return edgeInfo;
    }

    /** Renders the class-relationship cell of the package-relationship table for the given DTO. */
    private String renderHtmlCell(SimpleHtmlReport report, RankedDisharmony edgeInfo, CodebaseGraphDTO dto)
            throws Exception {
        Method method = SimpleHtmlReport.class.getDeclaredMethod(
                "getPackageRelationshipDisharmony", RankedDisharmony.class, String.class, CodebaseGraphDTO.class);
        method.setAccessible(true);
        String[] cells = (String[]) method.invoke(report, edgeInfo, REPO_URL, dto);
        return cells[4];
    }

    /** Builds the JSON DTO list of class relationships behind the package edge for the given DTO. */
    @SuppressWarnings("unchecked")
    private List<ClassRelationshipDTO> buildJsonList(JsonGenerator generator, CodebaseGraphDTO dto) throws Exception {
        DefaultWeightedEdge packageEdge = dto.getClassRelationshipsInPackageRelationship()
                .keySet()
                .iterator()
                .next();
        Method method = JsonGenerator.class.getDeclaredMethod(
                "buildClassRelationshipsToBreakPackage", Set.class, String.class, CodebaseGraphDTO.class, Map.class);
        method.setAccessible(true);
        return (List<ClassRelationshipDTO>) method.invoke(
                generator,
                dto.getClassRelationshipsInPackageRelationship().get(packageEdge),
                REPO_URL,
                dto,
                new HashMap<>());
    }

    @DisplayName(
            "class relationships behind a package edge render in the same deterministic order regardless of set insertion order")
    @Test
    void classRelationshipsToBreakPackage_areOrderedDeterministically() throws Exception {
        // Given: a class graph with edges crossing a package boundary, and DTOs whose
        // class-relationship sets are populated in many different insertion orders
        Graph<String, DefaultWeightedEdge> classGraph = createClassGraph();
        Graph<String, DefaultWeightedEdge> packageGraph = createPackageGraph();
        DefaultWeightedEdge packageEdge = packageGraph.edgeSet().iterator().next();
        List<DefaultWeightedEdge> edges = new ArrayList<>(classGraph.edgeSet());
        RankedDisharmony edgeInfo = mockRankedDisharmony(packageEdge);

        String firstHtmlCell = null;
        List<String> firstJsonSources = null;
        for (int rotation = 0; rotation < 24; rotation++) {
            Set<DefaultWeightedEdge> classEdges = newSetInOrder(edges, rotation);
            CodebaseGraphDTO dto = mockDto(packageEdge, classEdges);

            SimpleHtmlReport htmlReport = new SimpleHtmlReport();
            setField(htmlReport, "classGraph", classGraph);
            setField(htmlReport, "packageGraph", packageGraph);
            String htmlCell = renderHtmlCell(htmlReport, edgeInfo, dto);

            JsonGenerator jsonGenerator = new JsonGenerator();
            setField(jsonGenerator, "classGraph", classGraph);
            setField(jsonGenerator, "packageGraph", packageGraph);
            List<ClassRelationshipDTO> jsonList = buildJsonList(jsonGenerator, dto);
            List<String> jsonSources =
                    jsonList.stream().map(ClassRelationshipDTO::getSourceClass).toList();

            // When: comparing each rotated insertion order against the first one
            if (firstHtmlCell == null) {
                firstHtmlCell = htmlCell;
                firstJsonSources = jsonSources;
            } else {
                assertEquals(
                        firstHtmlCell,
                        htmlCell,
                        "HTML class-relationship cell must not depend on set insertion order (rotation " + rotation
                                + ")");
                assertEquals(
                        firstJsonSources,
                        jsonSources,
                        "JSON class-relationship order must not depend on set insertion order (rotation " + rotation
                                + ")");
            }
        }

        // Then: the order is the deterministic source-class order, identical in both reports
        List<DefaultWeightedEdge> expectedOrder = edges.stream()
                .sorted((e1, e2) -> {
                    int bySource = classGraph.getEdgeSource(e1).compareTo(classGraph.getEdgeSource(e2));
                    return bySource != 0
                            ? bySource
                            : classGraph.getEdgeTarget(e1).compareTo(classGraph.getEdgeTarget(e2));
                })
                .toList();
        assertEquals(
                expectedOrder.stream().map(classGraph::getEdgeSource).toList(),
                firstJsonSources,
                "JSON class relationships must be ordered by source class FQN");
        String expectedHtmlCell = String.join(
                "<br>",
                expectedOrder.stream()
                        .map(edge -> simpleName(classGraph.getEdgeSource(edge)) + " &#8594; "
                                + simpleName(classGraph.getEdgeTarget(edge)) + " : "
                                + (int) classGraph.getEdgeWeight(edge))
                        .toList());
        assertEquals(
                expectedHtmlCell,
                firstHtmlCell,
                "HTML class-relationship cell must list the same edges in the same order as the JSON report");
    }

    private static String simpleName(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }
}
