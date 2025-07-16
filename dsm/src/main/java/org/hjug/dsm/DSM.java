package org.hjug.dsm;

import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.alg.util.Triple;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.opt.graph.sparse.SparseIntDirectedWeightedGraph;

/*
Generated with Generative AI using a prompt similar to the following and iterated on:
Provide a complete implementation of a Numerical DSM with integer weighted edges in Java.
Include as many comments as possible in the implementation to make it easy to understand.
Use JGraphT classes and methods to the greatest extent possible.
Construction of the DSM should take place as follows.
First, Place nodes with empty rows at the top of the DSM.
Second, Place nodes with empty columns on the right of the DSM.
Third, identify strongly connected nodes and treat them as a single node using JGraphT's TarjanSimpleCycles class.
Fourth, order all edges in the DSM with a topological sort that permits cycles in the graph after identifying strongly connected components.
Fifth, Print the DSM in a method that performs no sorting or ordering - it should only print rows and columns.
When the DSM is printed, label the columns and rows.
Place dashes on the diagonal when printing.
include a method tht returns all edges above the diagonal.
include another method that returns the optimal edge above the diagonal to remove,
include a third method that identifies all minimum weight edges to remove above the diagonal.

Used https://sookocheff.com/post/dsm/improving-software-architecture-using-design-structure-matrix/#optimizing-processes
as a starting point.
 */

public class DSM<V, E> {
    private final Graph<V, E> graph;
    private List<V> sortedActivities;
    boolean activitiesSorted = false;
    private final List<E> edgesAboveDiagonal = new ArrayList<>();

    List<Integer> sparseIntSortedActivities;
    SparseIntDirectedWeightedGraph sparseGraph;

    @Getter
    double sumOfEdgeWeightsAboveDiagonal;

    Map<V, Integer> vertexToInt = new HashMap<>();
    Map<Integer, V> intToVertex = new HashMap<>();
    List<Triple<Integer, Integer, Double>> sparseEdges = new ArrayList<>();
    int vertexCount = 0;

    public DSM(Graph<V, E> graph) {
        this.graph = graph;
        sortedActivities = new ArrayList<>();

    }

    public void addActivity(V activity) {
        graph.addVertex(activity);
    }

    public void addDependency(V from, V to, int weight) {
        E edge = graph.addEdge(from, to);
        if (edge != null) {
            graph.setEdgeWeight(edge, weight);
        }
    }

    private void orderVertices() {
        sparseGraph = getSparseIntDirectedWeightedGraph();
        List<Set<Integer>> sccs = this.findStronglyConnectedSparseGraphComponents(sparseGraph);
        sparseIntSortedActivities = topologicalSortSparseGraph(sccs, sparseGraph);
        // reversing corrects rendering of the DSM
        // with sources as rows and targets as columns
        // was needed after AI solution was generated and iterated
        Collections.reverse(sparseIntSortedActivities);
        sortedActivities = convertIntToStringVertices(sparseIntSortedActivities);
        activitiesSorted = true;
    }

    private SparseIntDirectedWeightedGraph getSparseIntDirectedWeightedGraph() {
        for (V vertex : graph.vertexSet()) {
            vertexToInt.put(vertex, vertexCount);
            intToVertex.put(vertexCount, vertex);
            vertexCount++;
        }

        // Create the list of sparseEdges for the SparseIntDirectedWeightedGraph
        for (E edge : graph.edgeSet()) {
            int source = vertexToInt.get(graph.getEdgeSource(edge));
            int target = vertexToInt.get(graph.getEdgeTarget(edge));
            double weight = graph.getEdgeWeight(edge);
            sparseEdges.add(Triple.of(source, target, weight));
        }

        // Create the SparseIntDirectedWeightedGraph
        return new SparseIntDirectedWeightedGraph(vertexCount, sparseEdges);
    }

    List<V> convertIntToStringVertices(List<Integer> intVertices) {
        return intVertices.stream().map(intToVertex::get).collect(Collectors.toList());
    }

    /**
     * Kosaraju SCC detector avoids stack overflow.
     * It is used by JGraphT's CycleDetector, and makes sense to use it here as well for consistency
     *
     * @param graph
     * @return
     */
    private List<Set<Integer>> findStronglyConnectedSparseGraphComponents(Graph<Integer, Integer> graph) {
        KosarajuStrongConnectivityInspector<Integer, Integer> kosaraju =
                new KosarajuStrongConnectivityInspector<>(graph);
        return kosaraju.stronglyConnectedSets();
    }

    private List<Integer> topologicalSortSparseGraph(List<Set<Integer>> sccs, Graph<Integer, Integer> graph) {
        List<Integer> sortedActivities = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();

        for (Set<Integer> scc : sccs) {
            for (Integer activity : scc) {
                if (!visited.contains(activity)) {
                    topologicalSortUtilSparseGraph(activity, visited, sortedActivities, graph);
                }
            }
        }

        Collections.reverse(sortedActivities);
        return sortedActivities;
    }

    private void topologicalSortUtilSparseGraph(
            Integer activity, Set<Integer> visited, List<Integer> sortedActivities, Graph<Integer, Integer> graph) {
        visited.add(activity);

        for (Integer neighbor : Graphs.successorListOf(graph, activity)) {
            if (!visited.contains(neighbor)) {
                topologicalSortUtilSparseGraph(neighbor, visited, sortedActivities, graph);
            }
        }

        sortedActivities.add(activity);
    }

    public List<E> getEdgesAboveDiagonal() {
        if (!activitiesSorted) {
            orderVertices();
        }

        if (edgesAboveDiagonal.isEmpty()) {
            for (int i = 0; i < sortedActivities.size(); i++) {
                for (int j = i + 1; j < sortedActivities.size(); j++) {
                    // source / destination vertex was flipped after solution generation
                    // to correctly identify the vertex above the diagonal to remove
                    E edge = graph.getEdge(sortedActivities.get(i), sortedActivities.get(j));
                    if (edge != null) {
                        edgesAboveDiagonal.add(edge);
                    }
                }
            }

            sumOfEdgeWeightsAboveDiagonal = edgesAboveDiagonal.stream()
                    .mapToInt(edge -> (int) graph.getEdgeWeight(edge))
                    .sum();
        }

        return edgesAboveDiagonal;
    }

    private List<Integer> getSparseEdgesAboveDiagonal() {
        if (!activitiesSorted) {
            orderVertices();
        }

        List<Integer> sparseEdgesAboveDiagonal = new ArrayList<>();

        for (int i = 0; i < sparseIntSortedActivities.size(); i++) {
            for (int j = i + 1; j < sparseIntSortedActivities.size(); j++) {
                // source / destination vertex was flipped after solution generation
                // to correctly identify the vertex above the diagonal to remove
                Integer edge = sparseGraph.getEdge(sparseIntSortedActivities.get(i), sparseIntSortedActivities.get(j));

                if (edge != null) {
                    sparseEdgesAboveDiagonal.add(edge);
                }
            }
        }

        return sparseEdgesAboveDiagonal;
    }

    public E getFirstLowestWeightEdgeAboveDiagonalToRemove() {
        if (!activitiesSorted) {
            orderVertices();
        }

        List<E> edgesAboveDiagonal = getEdgesAboveDiagonal();
        E optimalEdge = null;
        int minWeight = Integer.MAX_VALUE;

        for (E edge : edgesAboveDiagonal) {
            int weight = (int) graph.getEdgeWeight(edge);
            if (weight < minWeight) {
                minWeight = weight;
                optimalEdge = edge;
                if (minWeight == 1) {
                    break;
                }
            }
        }

        return optimalEdge;
    }

    public List<E> getMinimumWeightEdgesAboveDiagonal() {
        if (!activitiesSorted) {
            orderVertices();
        }

        List<E> edgesAboveDiagonal = getEdgesAboveDiagonal();
        List<E> minWeightEdges = new ArrayList<>();
        double minWeight = Double.MAX_VALUE;

        for (E edge : edgesAboveDiagonal) {
            double weight = graph.getEdgeWeight(edge);
            if (weight < minWeight) {
                minWeight = weight;
                minWeightEdges.clear();
                minWeightEdges.add(edge);
            } else if (weight == minWeight) {
                minWeightEdges.add(edge);
            }
        }

        return minWeightEdges;
    }

    public void printDSM() {
        if (!activitiesSorted) {
            orderVertices();
        }

        printDSM(graph, sortedActivities);
    }

    void printDSM(Graph<V, E> graph, List<V> sortedActivities) {

        System.out.println("Design Structure Matrix:");
        System.out.print("  ");
        for (V col : sortedActivities) {
            System.out.print(col + " ");
        }
        System.out.println();
        for (V row : sortedActivities) {
            System.out.print(row + " ");
            for (V col : sortedActivities) {
                if (col.equals(row)) {
                    System.out.print("- ");
                } else {
                    E edge = graph.getEdge(row, col);
                    if (edge != null) {
                        System.out.print((int) graph.getEdgeWeight(edge) + " ");
                    } else {
                        System.out.print("0 ");
                    }
                }
            }
            System.out.println();
        }
    }
}
