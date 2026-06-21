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

    public CycleRemovalResult computeCycleRemovalInformation(Graph<String, DefaultWeightedEdge> graph) {
        Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles =
                new CircularReferenceChecker<String, DefaultWeightedEdge>().getCycles(graph);
        Map<DefaultWeightedEdge, Integer> edgeCycleCounts = new HashMap<>();
        Set<String> vertexesToRemove = new HashSet<>();
        Set<DefaultWeightedEdge> edgesToRemove = new HashSet<>();

        // Skip vertex and edge removal analysis if there are no cycles
        if (!cycles.isEmpty()) {
            // Identify vertexes to remove
            log.info("Identifying vertexes to remove");
            EnhancedParameterComputer<String, DefaultWeightedEdge> enhancedParameterComputer =
                    new EnhancedParameterComputer<>(new SuperTypeToken<>() {});
            EnhancedParameterComputer.EnhancedParameters<String> parameters =
                    enhancedParameterComputer.computeOptimalParameters(graph, 4);
            DirectedFeedbackVertexSetSolver<String, DefaultWeightedEdge> vertexSolver =
                    new DirectedFeedbackVertexSetSolver<>(
                            graph, parameters.getModulator(), null, parameters.getEta(), new SuperTypeToken<>() {});
            DirectedFeedbackVertexSetResult<String> vertexSetResult = vertexSolver.solve(parameters.getK());
            vertexesToRemove.addAll(vertexSetResult.getFeedbackVertices());

            // Identify edges to remove
            log.info("Identifying edges to remove");
            PageRankFAS<String, DefaultWeightedEdge> pageRankFAS = new PageRankFAS<>(graph, new SuperTypeToken<>() {});
            edgesToRemove.addAll(pageRankFAS.computeFeedbackArcSet());

            // capture the number of cycles each edge to remove is in
            for (DefaultWeightedEdge edgeToRemove : edgesToRemove) {
                int cycleCount = 0;
                for (AsSubgraph<String, DefaultWeightedEdge> cycle : cycles.values()) {
                    if (cycle.containsEdge(edgeToRemove)) {
                        cycleCount++;
                    }
                }
                edgeCycleCounts.put(edgeToRemove, cycleCount);
            }
        }

        return new CycleRemovalResult(cycles, edgesToRemove, vertexesToRemove, edgeCycleCounts);
    }
}
