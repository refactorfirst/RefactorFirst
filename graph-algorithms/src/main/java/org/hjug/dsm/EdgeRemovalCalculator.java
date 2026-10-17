package org.hjug.dsm;

import java.util.*;
import java.util.stream.Collectors;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

/**
 * Calculates the cycle-removal impact of removing edges above the DSM diagonal.
 *
 * @param <V> the graph vertex type
 */
public class EdgeRemovalCalculator<V> {

    private final Graph<V, DefaultWeightedEdge> graph;
    private DSM<V, DefaultWeightedEdge> dsm;
    private final Map<V, AsSubgraph<V, DefaultWeightedEdge>> cycles;
    private Set<DefaultWeightedEdge> edgesToRemove;

    public EdgeRemovalCalculator(Graph<V, DefaultWeightedEdge> graph, DSM<V, DefaultWeightedEdge> dsm) {
        this.graph = graph;
        this.dsm = dsm;
        this.cycles = new CircularReferenceChecker<V, DefaultWeightedEdge>().getCycles(graph);
    }

    public EdgeRemovalCalculator(Graph<V, DefaultWeightedEdge> graph, Set<DefaultWeightedEdge> edgesToRemove) {
        this.graph = graph;
        this.edgesToRemove = edgesToRemove;
        this.cycles = new CircularReferenceChecker<V, DefaultWeightedEdge>().getCycles(graph);
    }

    /**
     * Captures the impact of the removal of each edge above the diagonal.
     */
    public List<EdgeToRemoveInfo> getImpactOfEdgesAboveDiagonalIfRemoved(int limit) {
        // get edges above diagonal for DSM graph
        List<DefaultWeightedEdge> edgesAboveDiagonal;
        List<DefaultWeightedEdge> allEdgesAboveDiagonal = dsm.getEdgesAboveDiagonal();

        if (limit == 0 || allEdgesAboveDiagonal.size() <= limit) {
            edgesAboveDiagonal = allEdgesAboveDiagonal;
        } else {
            // get first 50 values of min weight
            List<DefaultWeightedEdge> minimumWeightEdgesAboveDiagonal = dsm.getMinimumWeightEdgesAboveDiagonal();
            int max = Math.min(minimumWeightEdgesAboveDiagonal.size(), limit);
            edgesAboveDiagonal = minimumWeightEdgesAboveDiagonal.subList(0, max);
        }

        int currentCycleCount = cycles.size();

        return edgesAboveDiagonal.stream()
                .map(this::calculateEdgeToRemoveInfo)
                .sorted(
                        Comparator.comparing((EdgeToRemoveInfo edgeToRemoveInfo) ->
                                currentCycleCount - edgeToRemoveInfo.getNewCycleCount())
                        /*.thenComparing(EdgeToRemoveInfo::getEdgeWeight)*/ )
                .collect(Collectors.toList());
    }

    public List<EdgeToRemoveInfo> getImpactOfEdges() {
        int currentCycleCount = cycles.size();

        return edgesToRemove.stream()
                .map(this::calculateEdgeToRemoveInfo)
                .sorted(
                        Comparator.comparing((EdgeToRemoveInfo edgeToRemoveInfo) ->
                                currentCycleCount - edgeToRemoveInfo.getNewCycleCount())
                        /*.thenComparing(EdgeToRemoveInfo::getEdgeWeight)*/ )
                .collect(Collectors.toList());
    }

    public EdgeToRemoveInfo calculateEdgeToRemoveInfo(DefaultWeightedEdge edgeToRemove) {
        // clone graph and remove edge
        Graph<V, DefaultWeightedEdge> improvedGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        graph.vertexSet().forEach(improvedGraph::addVertex);
        for (DefaultWeightedEdge weightedEdge : graph.edgeSet()) {
            improvedGraph.addEdge(graph.getEdgeSource(weightedEdge), graph.getEdgeTarget(weightedEdge), weightedEdge);
        }

        improvedGraph.removeEdge(edgeToRemove);

        // Calculate new cycle count
        int newCycleCount = new CircularReferenceChecker<V, DefaultWeightedEdge>()
                .getCycles(improvedGraph)
                .size();

        // calculate new graph statistics
        double removedEdgeWeight = graph.getEdgeWeight(edgeToRemove);
        double payoff = newCycleCount / removedEdgeWeight;
        return new EdgeToRemoveInfo(edgeToRemove, (int) removedEdgeWeight, newCycleCount, payoff);
    }
}
