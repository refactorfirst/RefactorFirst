package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.*;
import org.hjug.cbc.RankedDisharmony;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.VertexInfo;
import org.hjug.refactorfirst.report.model.ClassRelationshipDTO;
import org.hjug.refactorfirst.report.model.PackageRelationshipDTO;
import org.hjug.refactorfirst.report.model.PackageRelationshipsToRemoveDTO;
import org.hjug.refactorfirst.report.model.RefactorFirstReportDTO;
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
 * different orders. These tests pin a deterministic, source-ordered list shared by both reports:
 * the JSON generator and the report templates both render the DTO's
 * {@code classRelationshipsToBreakPackage} in list order. (The pre-P4 suite asserted the deleted
 * string-building HTML cell; the template now renders the same ordered DTO list — §10.3 mapping.)
 */
class PackageRelationshipClassEdgeOrderTest {

    private static final String PKG_A = "org.example.pkgA";
    private static final String PKG_B = "org.example.pkgB";

    /** Number of class edges crossing the package boundary. */
    private static final int EDGE_COUNT = 12;

    /**
     * Builds the class graph with {@link #EDGE_COUNT} edges from {@link #PKG_A} to
     * {@link #PKG_B}, plus two edges sharing one source whose targets' natural order differs
     * from their insertion order ({@code TargetZ} is inserted before {@code TargetB}).
     */
    private Graph<VertexInfo, DefaultWeightedEdge> createClassGraph() {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        for (int i = 0; i < EDGE_COUNT; i++) {
            String source = PKG_A + ".Class" + (char) ('A' + i);
            String target = PKG_B + ".Target" + (char) ('A' + i);
            classGraph.addVertex(VertexInfo.forName(source));
            classGraph.addVertex(VertexInfo.forName(target));
            DefaultWeightedEdge edge = classGraph.addEdge(VertexInfo.forName(source), VertexInfo.forName(target));
            classGraph.setEdgeWeight(edge, i + 1);
        }

        // Same-source edges whose target order differs from insertion order: TargetZ is added
        // first, but TargetB must be rendered first because it sorts first
        String multiSource = PKG_A + ".Multi";
        String targetZ = PKG_B + ".TargetZ";
        String targetB = PKG_B + ".TargetB";
        classGraph.addVertex(VertexInfo.forName(multiSource));
        classGraph.addVertex(VertexInfo.forName(targetZ));
        classGraph.addVertex(VertexInfo.forName(targetB));
        DefaultWeightedEdge multiToTargetZ =
                classGraph.addEdge(VertexInfo.forName(multiSource), VertexInfo.forName(targetZ));
        classGraph.setEdgeWeight(multiToTargetZ, EDGE_COUNT + 1);
        DefaultWeightedEdge multiToTargetB =
                classGraph.addEdge(VertexInfo.forName(multiSource), VertexInfo.forName(targetB));
        classGraph.setEdgeWeight(multiToTargetB, EDGE_COUNT + 2);

        return classGraph;
    }

    /** Builds a package graph with a single A -> B edge. */
    private Graph<VertexInfo, DefaultWeightedEdge> createPackageGraph() {
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        packageGraph.addVertex(VertexInfo.forName(PKG_A));
        packageGraph.addVertex(VertexInfo.forName(PKG_B));
        packageGraph.addEdge(VertexInfo.forName(PKG_A), VertexInfo.forName(PKG_B));
        return packageGraph;
    }

    /** Inserts the given edges into a fresh HashSet in the given order. */
    private Set<DefaultWeightedEdge> newSetInOrder(Collection<DefaultWeightedEdge> edges, int rotation) {
        List<DefaultWeightedEdge> rotated = new ArrayList<>(edges);
        Collections.rotate(rotated, rotation);
        return new HashSet<>(rotated);
    }

    /** Injects a graph fixture into a field declared anywhere in the target's class hierarchy. */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                // keep walking up the hierarchy
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    /** Maps the package edge to its class edges in a DTO mock with no source file paths. */
    private CodebaseGraphDTO mockDto(DefaultWeightedEdge packageEdge, Set<DefaultWeightedEdge> classEdges) {
        CodebaseGraphDTO dto = Mockito.mock(CodebaseGraphDTO.class);
        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> relationships = new HashMap<>();
        relationships.put(packageEdge, classEdges);
        Mockito.when(dto.getClassRelationshipsInPackageRelationship()).thenReturn(relationships);
        Mockito.when(dto.getClassToSourceFilePathMapping()).thenReturn(new HashMap<>());
        return dto;
    }

    /** Creates a ranked package-edge fixture with priority, cycle count and effort rank set to one. */
    private RankedDisharmony mockRankedDisharmony(DefaultWeightedEdge packageEdge) {
        RankedDisharmony edgeInfo = Mockito.mock(RankedDisharmony.class);
        Mockito.when(edgeInfo.getEdge()).thenReturn(packageEdge);
        Mockito.when(edgeInfo.getPriority()).thenReturn(1);
        Mockito.when(edgeInfo.getCycleCount()).thenReturn(1);
        Mockito.when(edgeInfo.getEffortRank()).thenReturn(1);
        return edgeInfo;
    }

    /** Builds the JSON DTO list of class relationships behind the package edge for the given DTO. */
    private List<ClassRelationshipDTO> buildJsonList(JsonGenerator generator, CodebaseGraphDTO dto) {
        DefaultWeightedEdge packageEdge = dto.getClassRelationshipsInPackageRelationship()
                .keySet()
                .iterator()
                .next();
        return generator.buildClassRelationshipsToBreakPackage(
                dto.getClassRelationshipsInPackageRelationship().get(packageEdge), dto);
    }

    /** Verifies the JSON order is stable across class-edge set insertions, and the view model
     * (which both the HTML and JSON templates render in list order) preserves it exactly. */
    @DisplayName(
            "class relationships behind a package edge render in the same deterministic order regardless of set insertion order")
    @Test
    void classRelationshipsToBreakPackage_areOrderedDeterministically() throws Exception {
        // Given: a class graph with edges crossing a package boundary, and DTOs whose
        // class-relationship sets are populated in many different insertion orders
        Graph<VertexInfo, DefaultWeightedEdge> classGraph = createClassGraph();
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = createPackageGraph();
        DefaultWeightedEdge packageEdge = packageGraph.edgeSet().iterator().next();
        List<DefaultWeightedEdge> edges = new ArrayList<>(classGraph.edgeSet());

        List<ClassRelationshipDTO> firstJsonList = null;
        for (int rotation = 0; rotation < 24; rotation++) {
            Set<DefaultWeightedEdge> classEdges = newSetInOrder(edges, rotation);
            CodebaseGraphDTO dto = mockDto(packageEdge, classEdges);

            JsonGenerator jsonGenerator = new JsonGenerator();
            setField(jsonGenerator, "classGraph", classGraph);
            setField(jsonGenerator, "packageGraph", packageGraph);
            List<ClassRelationshipDTO> jsonList = buildJsonList(jsonGenerator, dto);

            // When: comparing each rotated insertion order against the first one
            if (firstJsonList == null) {
                firstJsonList = jsonList;
            } else {
                assertEquals(
                        firstJsonList.stream()
                                .map(ClassRelationshipDTO::getSourceClass)
                                .toList(),
                        jsonList.stream()
                                .map(ClassRelationshipDTO::getSourceClass)
                                .toList(),
                        "JSON class-relationship order must not depend on set insertion order (rotation " + rotation
                                + ")");
            }
        }

        // Then: the order is the deterministic source-class order
        List<DefaultWeightedEdge> expectedOrder = edges.stream()
                .sorted(Comparator.comparing((DefaultWeightedEdge e) ->
                                classGraph.getEdgeSource(e).getName())
                        .thenComparing(e -> classGraph.getEdgeTarget(e).getName()))
                .toList();
        assertEquals(
                expectedOrder.stream()
                        .map(edge -> classGraph.getEdgeSource(edge).getName())
                        .toList(),
                firstJsonList.stream().map(ClassRelationshipDTO::getSourceClass).toList(),
                "JSON class relationships must be ordered by source class FQN");

        // And: edges sharing a source order their targets naturally — not by insertion
        // order (TargetZ was inserted first, but TargetB sorts first)
        List<String> multiSourceTargets = firstJsonList.stream()
                .filter(r -> r.getSourceClass().equals(PKG_A + ".Multi"))
                .map(ClassRelationshipDTO::getTargetClass)
                .toList();
        assertEquals(
                List.of(PKG_B + ".TargetB", PKG_B + ".TargetZ"),
                multiSourceTargets,
                "JSON must order same-source class relationships by target class FQN, not insertion order");
    }

    /** The template view model carries the ordered class-relationship list unchanged, so the
     * HTML table renders the same deterministic order the JSON report renders. */
    @DisplayName("the view model preserves the ordered classRelationshipsToBreakPackage list")
    @Test
    void viewModelPreservesTheOrderedClassRelationshipList() {
        // Given: a package relationship whose break-up list is ordered source-class first,
        // with same-source targets in natural order (TargetB before TargetZ)
        ClassRelationshipDTO first = ClassRelationshipDTO.builder()
                .sourceClass(PKG_A + ".Multi")
                .targetClass(PKG_B + ".TargetB")
                .simpleSourceClassName("Multi")
                .simpleTargetClassName("TargetB")
                .weight(13)
                .build();
        ClassRelationshipDTO second = ClassRelationshipDTO.builder()
                .sourceClass(PKG_A + ".Multi")
                .targetClass(PKG_B + ".TargetZ")
                .simpleSourceClassName("Multi")
                .simpleTargetClassName("TargetZ")
                .weight(13)
                .build();
        PackageRelationshipDTO relationship = PackageRelationshipDTO.builder()
                .sourcePackage(PKG_A)
                .targetPackage(PKG_B)
                .priority(1)
                .cycleCount(1)
                .effortRank(1)
                .classRelationshipsToBreakPackage(List.of(first, second))
                .build();
        RefactorFirstReportDTO report = RefactorFirstReportDTO.builder()
                .packageRelationshipsToRemove(PackageRelationshipsToRemoveDTO.builder()
                        .hasRelationships(true)
                        .relationships(List.of(relationship))
                        .build())
                .build();

        // When: the view model is built
        Map<String, Object> viewModel = new ViewModelFactory().createViewModel(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> packageRelationships = (Map<String, Object>) viewModel.get("packageRelationshipsToRemove");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> packageRows = (List<Map<String, Object>>) packageRelationships.get("relationships");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> brokenRelationships =
                (List<Map<String, Object>>) packageRows.get(0).get("classRelationshipsToBreakPackage");

        // Then: the order survives into the view model the template renders
        assertEquals(2, brokenRelationships.size());
        assertEquals(PKG_B + ".TargetB", brokenRelationships.get(0).get("targetClass"));
        assertEquals(PKG_B + ".TargetZ", brokenRelationships.get(1).get("targetClass"));
    }
}
