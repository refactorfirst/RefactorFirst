package org.hjug.dsm;

import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.alg.cycle.JohnsonSimpleCycles;
import org.jgrapht.graph.AsSubgraph;

import java.util.*;

public class OptimalBackEdgeRemover<V, E> {
    private Graph<V, E> graph;

    /**
     * Constructor initializing with the target graph.
     * @param graph The directed weighted graph to analyze
     */
    public OptimalBackEdgeRemover(Graph<V, E> graph) {
        this.graph = graph;
    }

    /**
     * Finds the optimal back edge(s) to remove to move the graph closer to a DAG.
     * @return A set of edges to remove
     */
    public Set<E> findOptimalBackEdgesToRemove() {
        CycleDetector<V, E> cycleDetector = new CycleDetector<>(graph);

        // If the graph is already acyclic, return empty set
        if (!cycleDetector.detectCycles()) {
            return Collections.emptySet();
        }

        // Find all cycles in the graph
        JohnsonSimpleCycles<V, E> cycleFinder = new JohnsonSimpleCycles<>(graph);
        List<List<V>> originalCycles = cycleFinder.findSimpleCycles();
        int originalCycleCount = originalCycles.size();

        // Identify edges that are part of at least one cycle
        Set<E> edgesInCycles = new HashSet<>();
        for (List<V> cycle : originalCycles) {
            for (int i = 0; i < cycle.size(); i++) {
                V source = cycle.get(i);
                V target = cycle.get((i + 1) % cycle.size());
                E edge = graph.getEdge(source, target);
                edgesInCycles.add(edge);
            }
        }

        // Calculate cycle elimination count for each edge
        Map<E, Integer> edgeCycleEliminationCount = new HashMap<>();
        for (E edge : edgesInCycles) {
            // Create a subgraph without this edge
            Graph<V, E> subgraph = new AsSubgraph<>(graph, graph.vertexSet(), new HashSet<>(graph.edgeSet()));
            subgraph.removeEdge(edge);

            // Calculate how many cycles would be eliminated
            JohnsonSimpleCycles<V, E> subgraphCycleFinder = new JohnsonSimpleCycles<>(subgraph);
            List<List<V>> remainingCycles = subgraphCycleFinder.findSimpleCycles();
            int cyclesEliminated = originalCycleCount - remainingCycles.size();

            edgeCycleEliminationCount.put(edge, cyclesEliminated);
        }

        // Find edges that eliminate the most cycles
        int maxCycleElimination = 0;
        List<E> maxEliminationEdges = new ArrayList<>();

        for (Map.Entry<E, Integer> entry : edgeCycleEliminationCount.entrySet()) {
            if (entry.getValue() > maxCycleElimination) {
                maxCycleElimination = entry.getValue();
                maxEliminationEdges.clear();
                maxEliminationEdges.add(entry.getKey());
            } else if (entry.getValue() == maxCycleElimination) {
                maxEliminationEdges.add(entry.getKey());
            }
        }

        // If no cycles are eliminated (shouldn't happen), return empty set
        if (maxEliminationEdges.isEmpty() || maxCycleElimination == 0) {
            return Collections.emptySet();
        }

        // If multiple edges eliminate the same number of cycles, choose the one with the lowest weight
        if (maxEliminationEdges.size() > 1) {
            double minWeight = Double.MAX_VALUE;
            List<E> minWeightEdges = new ArrayList<>();

            for (E edge : maxEliminationEdges) {
                double weight = graph.getEdgeWeight(edge);
                if (weight < minWeight) {
                    minWeight = weight;
                    minWeightEdges.clear();
                    minWeightEdges.add(edge);
                } else if (weight == minWeight) {
                    minWeightEdges.add(edge);
                }
            }

            return new HashSet<>(minWeightEdges);
        }

        // Return the single edge that eliminates the most cycles
        return new HashSet<>(maxEliminationEdges);
    }
}
