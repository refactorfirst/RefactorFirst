package org.hjug.dsm;

import java.util.*;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultWeightedEdge;

/*
Based on https://sookocheff.com/post/dsm/improving-software-architecture-using-design-structure-matrix/#optimizing-processes
and modified slightly
Used the following prompt to generate this class with generative AI:

Provide a complete java implementation of a dsm using jgrapht, labeling the rows and columns,
with the node values representing the edge weight between nodes.
Edge weights should be cast to integers.
Identify which edge above the diagonal in the set of cycles contained in the graph that will be the easiest to remove first.
Order nodes using the following logic:
Order nodes with empty rows at the top of the DSM.
Nodes with empty columns should be placed on the right of the DSM.
Find any cycles in the remaining elements through a depth-first search.
Group together all activities in a cycle as a single activity, and treat the group as its own step in the sequence.
*/
public class DSM {

    static List<String> orderVertices(Graph<String, DefaultWeightedEdge> graph) {
        List<String> orderedVertices = new ArrayList<>();
        Set<String> vertices = graph.vertexSet();

        // Find nodes with empty rows (no outgoing edges)
        for (String vertex : vertices) {
            if (graph.outgoingEdgesOf(vertex).isEmpty()) {
                orderedVertices.add(vertex);
            }
        }

        // Find nodes with empty columns (no incoming edges)
        for (String vertex : vertices) {
            if (graph.incomingEdgesOf(vertex).isEmpty() && !orderedVertices.contains(vertex)) {
                orderedVertices.add(vertex);
            }
        }

        // Find cycles in the remaining elements
        CycleDetector<String, DefaultWeightedEdge> cycleDetector = new CycleDetector<>(graph);
        Set<String> remainingVertices = new HashSet<>(vertices);
        orderedVertices.forEach(remainingVertices::remove);

        while (!remainingVertices.isEmpty()) {
            String startVertex = remainingVertices.iterator().next();
            Set<String> cycle = cycleDetector.findCyclesContainingVertex(startVertex);

            if (!cycle.isEmpty()) {
                orderedVertices.addAll(cycle);
                remainingVertices.removeAll(cycle);
            } else {
                orderedVertices.add(startVertex);
                remainingVertices.remove(startVertex);
            }
        }

        return orderedVertices;
    }

    public static DefaultWeightedEdge identifyOptimalBakcwardEdgeToRemove(
            Graph<String, DefaultWeightedEdge> graph, List<String> orderedVertices) {
        CycleDetector<String, DefaultWeightedEdge> cycleDetector = new CycleDetector<>(graph);
        Set<String> cycleVertices = cycleDetector.findCycles();

        if (!cycleVertices.isEmpty()) {
            DefaultWeightedEdge minEdge = null;
            double minWeight = Double.MAX_VALUE;

            for (String vertex : cycleVertices) {
                for (DefaultWeightedEdge edge : graph.edgesOf(vertex)) {
                    if (cycleVertices.contains(graph.getEdgeTarget(edge))) {
                        int rowIndex = orderedVertices.indexOf(graph.getEdgeSource(edge));
                        int colIndex = orderedVertices.indexOf(graph.getEdgeTarget(edge));
                        // Check if the edge is above the diagonal
                        if (rowIndex < colIndex) {
                            double weight = graph.getEdgeWeight(edge);
                            if (weight < minWeight) {
                                minWeight = weight;
                                minEdge = edge;
                            }
                            // return immediately b/c we shouldn't have edge weight less than 1
                            if (weight == 1) {
                                return edge;
                            }
                        }
                    }
                }
            }
            return minEdge;
        }
        return null;
    }

    static List<DefaultWeightedEdge> identifyAllBackwardMinWeightEdgesToRemove(
            Graph<String, DefaultWeightedEdge> graph, List<String> orderedVertices) {
        CycleDetector<String, DefaultWeightedEdge> cycleDetector = new CycleDetector<>(graph);
        Set<String> cycleVertices = cycleDetector.findCycles();

        if (!cycleVertices.isEmpty()) {
            List<DefaultWeightedEdge> edgesToRemove = new ArrayList<>();
            double minWeight = Double.MAX_VALUE;

            for (String vertex : cycleVertices) {
                for (DefaultWeightedEdge edge : graph.edgesOf(vertex)) {
                    if (cycleVertices.contains(graph.getEdgeTarget(edge))) {
                        int rowIndex = orderedVertices.indexOf(graph.getEdgeSource(edge));
                        int colIndex = orderedVertices.indexOf(graph.getEdgeTarget(edge));
                        // Check if the edge is above the diagonal
                        if (rowIndex < colIndex) {
                            double weight = graph.getEdgeWeight(edge);
                            if (weight < minWeight) {
                                edgesToRemove.clear();
                                minWeight = weight;
                                edgesToRemove.add(edge);
                            } else if (weight == minWeight && !edgesToRemove.contains(edge)) {
                                edgesToRemove.add(edge);
                            }
                        }
                    }
                }
            }
            return edgesToRemove;
        }
        return new ArrayList<>();
    }

    static List<DefaultWeightedEdge> getAllBackwardEdges(
            Graph<String, DefaultWeightedEdge> graph, List<String> orderedVertices) {
        CycleDetector<String, DefaultWeightedEdge> cycleDetector = new CycleDetector<>(graph);
        Set<String> cycleVertices = cycleDetector.findCycles();

        if (!cycleVertices.isEmpty()) {
            List<DefaultWeightedEdge> edgesToRemove = new ArrayList<>();

            for (String vertex : cycleVertices) {
                for (DefaultWeightedEdge edge : graph.edgesOf(vertex)) {
                    if (cycleVertices.contains(graph.getEdgeTarget(edge))) {
                        int rowIndex = orderedVertices.indexOf(graph.getEdgeSource(edge));
                        int colIndex = orderedVertices.indexOf(graph.getEdgeTarget(edge));
                        // Check if the edge is above the diagonal
                        // and has not been added yet
                        if (rowIndex < colIndex && !edgesToRemove.contains(edge)) {
                            edgesToRemove.add(edge);
                        }
                    }
                }
            }
            return edgesToRemove;
        }
        return new ArrayList<>();
    }

    public static void printDSM(Graph<String, DefaultWeightedEdge> graph, List<String> orderedVertices) {
        // Print column labels
        System.out.print("   ");
        for (String vertex : orderedVertices) {
            System.out.print(vertex + " ");
        }
        System.out.println();

        // Print rows with edge weights
        for (String rowVertex : orderedVertices) {
            System.out.print(rowVertex + " ");
            for (String colVertex : orderedVertices) {
                DefaultWeightedEdge edge = graph.getEdge(rowVertex, colVertex);
                int weight = (edge != null) ? (int) graph.getEdgeWeight(edge) : 0;
                System.out.print(weight + " ");
            }
            System.out.println();
        }
    }
}
