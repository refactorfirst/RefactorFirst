package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.hjug.dsm.CircularReferenceChecker;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.refactorfirst.report.model.ClassRelationshipDTO;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The nested class relationships inside a package relationship (the "class relationships to
 * remove to break the package relationship") are generally not part of the class feedback arc
 * set, so {@code classEdgeCycleCounts} — which {@code CycleRemovalComputer} populates only for
 * feedback-arc-set edges — never contains them. Reporting {@code 0} for such edges falsely
 * claims membership in zero class cycles. These tests pin that the nested DTOs carry the real
 * class-cycle membership, calculated from the already-available {@code classCycles}.
 */
class PackageRelationshipClassEdgeCycleCountTest {

    private static final String PKG_A = "org.example.pkgA";
    private static final String PKG_B = "org.example.pkgB";
    private static final String ALPHA = PKG_A + ".Alpha";
    private static final String BETA = PKG_B + ".Beta";
    private static final String DELTA = PKG_A + ".Delta";
    private static final String REPO_URL = "https://github.com/example/repo/blob/";

    /** Alpha and Beta form a 2-cycle; Delta -> Beta hangs off the cycle and is in no cycle. */
    private Graph<String, DefaultWeightedEdge> createClassGraph() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex(ALPHA);
        classGraph.addVertex(BETA);
        classGraph.addVertex(DELTA);

        DefaultWeightedEdge alphaToBeta = classGraph.addEdge(ALPHA, BETA);
        classGraph.setEdgeWeight(alphaToBeta, 5);
        DefaultWeightedEdge betaToAlpha = classGraph.addEdge(BETA, ALPHA);
        classGraph.setEdgeWeight(betaToAlpha, 2);
        DefaultWeightedEdge deltaToBeta = classGraph.addEdge(DELTA, BETA);
        classGraph.setEdgeWeight(deltaToBeta, 1);

        return classGraph;
    }

    private CodebaseGraphDTO mockDto() {
        CodebaseGraphDTO dto = Mockito.mock(CodebaseGraphDTO.class);
        Mockito.when(dto.getClassToSourceFilePathMapping()).thenReturn(new HashMap<>());
        return dto;
    }

    @DisplayName("nested class relationships report their real class-cycle membership, not a defaulted zero")
    @Test
    void buildClassRelationshipsToBreakPackage_reportsActualCycleMembership() throws Exception {
        // Given: a class graph whose Alpha -> Beta and Beta -> Alpha edges participate in the
        // Alpha/Beta class cycle, and a Delta -> Beta edge that participates in no cycle
        Graph<String, DefaultWeightedEdge> classGraph = createClassGraph();
        DefaultWeightedEdge alphaToBeta = classGraph.getEdge(ALPHA, BETA);
        DefaultWeightedEdge betaToAlpha = classGraph.getEdge(BETA, ALPHA);
        DefaultWeightedEdge deltaToBeta = classGraph.getEdge(DELTA, BETA);

        JsonGenerator generator = new JsonGenerator();
        generator.classGraph = classGraph;
        // The class cycles, built the same way CycleRemovalComputer builds them
        Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles =
                new CircularReferenceChecker<String, DefaultWeightedEdge>().getCycles(classGraph);
        generator.classCycles = cycles;
        assertEquals(1, cycles.size(), "Test graph must contain exactly one class cycle");
        assertEquals(
                1,
                cycles.values().stream()
                        .filter(c -> c.containsEdge(alphaToBeta))
                        .count(),
                "The Alpha -> Beta edge must be a member of the class cycle");

        // The nested edges are absent from classEdgeCycleCounts (which only covers
        // feedback-arc-set edges), so their membership must be calculated from classCycles
        Set<DefaultWeightedEdge> classEdgesInPackageRelationship = new LinkedHashSet<>();
        classEdgesInPackageRelationship.add(alphaToBeta);
        classEdgesInPackageRelationship.add(betaToAlpha);
        classEdgesInPackageRelationship.add(deltaToBeta);

        // When: the nested class-relationship DTOs are built for the package edge
        var relationships =
                generator.buildClassRelationshipsToBreakPackage(classEdgesInPackageRelationship, REPO_URL, mockDto());

        // Then: each nested DTO reports its real class-cycle membership
        ClassRelationshipDTO alphaToBetaDto = relationships.stream()
                .filter(r -> r.getSourceClass().equals(ALPHA))
                .findFirst()
                .orElseThrow();
        ClassRelationshipDTO betaToAlphaDto = relationships.stream()
                .filter(r -> r.getSourceClass().equals(BETA))
                .findFirst()
                .orElseThrow();
        ClassRelationshipDTO deltaToBetaDto = relationships.stream()
                .filter(r -> r.getSourceClass().equals(DELTA))
                .findFirst()
                .orElseThrow();

        assertEquals(1, alphaToBetaDto.getCycleCount(), "Alpha -> Beta is in the class cycle, so its count must be 1");
        assertEquals(1, betaToAlphaDto.getCycleCount(), "Beta -> Alpha is in the class cycle, so its count must be 1");
        assertEquals(0, deltaToBetaDto.getCycleCount(), "Delta -> Beta is in no class cycle, so its count must be 0");
    }
}
