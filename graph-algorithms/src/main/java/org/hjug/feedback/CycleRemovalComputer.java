package org.hjug.feedback;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hjug.dsm.CircularReferenceChecker;
import org.hjug.feedback.arc.pageRank.PageRankFAS;
import org.hjug.feedback.vertex.kernelized.DirectedFeedbackVertexSetResult;
import org.hjug.feedback.vertex.kernelized.DirectedFeedbackVertexSetSolver;
import org.hjug.feedback.vertex.kernelized.EnhancedParameterComputer;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Slf4j
public class CycleRemovalComputer {

    /**
     * Identifies cyclic subgraphs, feedback vertices, and feedback edges for a dependency graph.
     * Acyclic graphs yield empty removal sets; selected edges include their cycle participation counts.
     *
     * @param graph the dependency graph to analyze
     * @param <V> the graph vertex type
     * @return the cycles and proposed vertex and edge removals
     */
    public <V> CycleRemovalResult<V> computeCycleRemovalInformation(Graph<V, DefaultWeightedEdge> graph) {
        Map<V, AsSubgraph<V, DefaultWeightedEdge>> cycles =
                new CircularReferenceChecker<V, DefaultWeightedEdge>().getCycles(graph);
        Map<DefaultWeightedEdge, Integer> edgeCycleCounts = new HashMap<>();
        Map<V, Integer> vertexCycleCounts = new HashMap<>();
        Set<V> vertexesToRemove = new HashSet<>();
        Set<DefaultWeightedEdge> edgesToRemove = new HashSet<>();

        // Skip vertex and edge removal analysis if there are no cycles
        if (!cycles.isEmpty()) {
            // Identify vertexes to remove
            log.info("Identifying vertexes to remove");
            EnhancedParameterComputer<V, DefaultWeightedEdge> enhancedParameterComputer =
                    new EnhancedParameterComputer<>(new SuperTypeToken<>() {});
            EnhancedParameterComputer.EnhancedParameters<V> parameters =
                    enhancedParameterComputer.computeOptimalParameters(graph, 4);
            DirectedFeedbackVertexSetSolver<V, DefaultWeightedEdge> vertexSolver =
                    new DirectedFeedbackVertexSetSolver<>(
                            graph, parameters.getModulator(), null, parameters.getEta(), new SuperTypeToken<>() {});
            DirectedFeedbackVertexSetResult<V> vertexSetResult = vertexSolver.solve(parameters.getK());
            vertexesToRemove.addAll(vertexSetResult.getFeedbackVertices());

            // capture the number of cycles each vertex to remove is in
            for (V vertexToRemove : vertexesToRemove) {
                int cycleCount = 0;
                for (AsSubgraph<V, DefaultWeightedEdge> cycle : cycles.values()) {
                    if (cycle.containsVertex(vertexToRemove)) {
                        cycleCount++;
                    }
                }
                vertexCycleCounts.put(vertexToRemove, cycleCount);
            }

            // Identify edges to remove
            log.info("Identifying edges to remove");
            PageRankFAS<V, DefaultWeightedEdge> pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {});
            edgesToRemove.addAll(pageRankFAS.computeFeedbackArcSet());

            // capture the number of cycles each edge to remove is in
            for (DefaultWeightedEdge edgeToRemove : edgesToRemove) {
                int cycleCount = 0;
                for (AsSubgraph<V, DefaultWeightedEdge> cycle : cycles.values()) {
                    if (cycle.containsEdge(edgeToRemove)) {
                        cycleCount++;
                    }
                }
                edgeCycleCounts.put(edgeToRemove, cycleCount);
            }
        }

        return new CycleRemovalResult(cycles, edgesToRemove, vertexesToRemove, edgeCycleCounts, vertexCycleCounts);
    }
}
