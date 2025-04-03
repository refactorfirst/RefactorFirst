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

public class DSM {
    private final Graph<String, DefaultWeightedEdge> graph;
    private List<String> sortedActivities;
    boolean activitiesSorted = false;
    private final List<DefaultWeightedEdge> edgesAboveDiagonal = new ArrayList<>();

    List<Integer> sparseIntSortedActivities;
    SparseIntDirectedWeightedGraph sparseGraph;

    @Getter
    double sumOfEdgeWeightsAboveDiagonal;

    Map<String, Integer> vertexToInt = new HashMap<>();
    Map<Integer, String> intToVertex = new HashMap<>();
    List<Triple<Integer, Integer, Double>> sparseEdges = new ArrayList<>();
    int vertexCount = 0;

    @Getter
    Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles;

    public DSM() {
        this(new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class));
    }

    public DSM(Graph<String, DefaultWeightedEdge> graph) {
        this.graph = graph;
        sortedActivities = new ArrayList<>();
        cycles = new CircularReferenceChecker().getCycles(graph);
    }

    public void addActivity(String activity) {
        graph.addVertex(activity);
    }

    public void addDependency(String from, String to, int weight) {
        DefaultWeightedEdge edge = graph.addEdge(from, to);
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
        for (String vertex : graph.vertexSet()) {
            vertexToInt.put(vertex, vertexCount);
            intToVertex.put(vertexCount, vertex);
            vertexCount++;
        }

        // Create the list of sparseEdges for the SparseIntDirectedWeightedGraph
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            int source = vertexToInt.get(graph.getEdgeSource(edge));
            int target = vertexToInt.get(graph.getEdgeTarget(edge));
            double weight = graph.getEdgeWeight(edge);
            sparseEdges.add(Triple.of(source, target, weight));
        }

        // Create the SparseIntDirectedWeightedGraph
        return new SparseIntDirectedWeightedGraph(vertexCount, sparseEdges);
    }

    List<String> convertIntToStringVertices(List<Integer> intVertices) {
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

    public List<DefaultWeightedEdge> getEdgesAboveDiagonal() {
        if (!activitiesSorted) {
            orderVertices();
        }

        if (edgesAboveDiagonal.isEmpty()) {
            for (int i = 0; i < sortedActivities.size(); i++) {
                for (int j = i + 1; j < sortedActivities.size(); j++) {
                    // source / destination vertex was flipped after solution generation
                    // to correctly identify the vertex above the diagonal to remove
                    DefaultWeightedEdge edge = graph.getEdge(sortedActivities.get(i), sortedActivities.get(j));
                    if (edge != null) {
                        edgesAboveDiagonal.add(edge);
                    }
                }
            }

            sumOfEdgeWeightsAboveDiagonal = edgesAboveDiagonal.stream()
                    .mapToInt(edge -> (int) graph.getEdgeWeight(edge)).sum();

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

    public DefaultWeightedEdge getFirstLowestWeightEdgeAboveDiagonalToRemove() {
        if (!activitiesSorted) {
            orderVertices();
        }

        List<DefaultWeightedEdge> edgesAboveDiagonal = getEdgesAboveDiagonal();
        DefaultWeightedEdge optimalEdge = null;
        int minWeight = Integer.MAX_VALUE;

        for (DefaultWeightedEdge edge : edgesAboveDiagonal) {
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

    public List<DefaultWeightedEdge> getMinimumWeightEdgesAboveDiagonal() {
        if (!activitiesSorted) {
            orderVertices();
        }

        List<DefaultWeightedEdge> edgesAboveDiagonal = getEdgesAboveDiagonal();
        List<DefaultWeightedEdge> minWeightEdges = new ArrayList<>();
        double minWeight = Double.MAX_VALUE;

        for (DefaultWeightedEdge edge : edgesAboveDiagonal) {
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

    void printDSM(Graph<String, DefaultWeightedEdge> graph, List<String> sortedActivities) {

        System.out.println("Design Structure Matrix:");
        System.out.print("  ");
        for (String col : sortedActivities) {
            System.out.print(col + " ");
        }
        System.out.println();
        for (String row : sortedActivities) {
            System.out.print(row + " ");
            for (String col : sortedActivities) {
                if (col.equals(row)) {
                    System.out.print("- ");
                } else {
                    DefaultWeightedEdge edge = graph.getEdge(row, col);
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

    /////////////////////////////////////////////////////////
    // "Standard" Graph implementation to find edge to remove
    /////////////////////////////////////////////////////////

    /**
     * Captures the impact of the removal of each edge above the diagonal.
     */
    public List<EdgeToRemoveInfo> getImpactOfEdgesAboveDiagonalIfRemoved() {
        return getEdgesAboveDiagonal().stream()
                .map(this::calculateEdgeToRemoveInfo)
                .sorted(Comparator.comparing(EdgeToRemoveInfo::getPayoff).thenComparing(EdgeToRemoveInfo::getEdgeWeight))
                .collect(Collectors.toList());
    }

    private EdgeToRemoveInfo calculateEdgeToRemoveInfo(DefaultWeightedEdge edgeToRemove) {
        //clone graph and remove edge
        Graph<String, DefaultWeightedEdge> improvedGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        graph.vertexSet().forEach(improvedGraph::addVertex);
        for (DefaultWeightedEdge weightedEdge : graph.edgeSet()) {
            improvedGraph.addEdge(graph.getEdgeSource(weightedEdge), graph.getEdgeTarget(weightedEdge), weightedEdge);
        }

        improvedGraph.removeEdge(edgeToRemove);

        //find edges above diagonal
        List<String> sortedActivities = orderVertices(improvedGraph);
        List<DefaultWeightedEdge> updatedEdges = getEdgesAboveDiagonal(improvedGraph, sortedActivities);

        //calculate new graph statistics
        int newEdgeCount = updatedEdges.size();
        double newEdgeWeightSum = updatedEdges.stream()
                .mapToDouble(graph::getEdgeWeight).sum();
        double weight = graph.getEdgeWeight(edgeToRemove);
        double payoff = (sumOfEdgeWeightsAboveDiagonal - newEdgeWeightSum) / weight;
        return new EdgeToRemoveInfo(edgeToRemove, (int) weight, newEdgeCount, newEdgeWeightSum, payoff);
    }



    public List<DefaultWeightedEdge> getEdgesAboveDiagonal(Graph<String, DefaultWeightedEdge> graph, List<String> sortedActivities) {
        List<DefaultWeightedEdge> edgesAboveDiagonal = new ArrayList<>();
        for (int i = 0; i < sortedActivities.size(); i++) {
            for (int j = i + 1; j < sortedActivities.size(); j++) {
                // source / destination vertex was flipped after solution generation
                // to correctly identify the vertex above the diagonal to remove
                DefaultWeightedEdge edge = graph.getEdge(sortedActivities.get(i), sortedActivities.get(j));
                if (edge != null) {
                    edgesAboveDiagonal.add(edge);
                }
            }
        }

        return edgesAboveDiagonal;
    }

    private List<String> orderVertices(Graph<String, DefaultWeightedEdge> graph) {
        List<Set<String>> sccs = findStronglyConnectedComponents(graph);
        List<String> sparseIntSortedActivities = topologicalSort(sccs, graph);
        // reversing corrects rendering of the DSM
        // with sources as rows and targets as columns
        // was needed after AI solution was generated and iterated
        Collections.reverse(sparseIntSortedActivities);

        return sparseIntSortedActivities;
    }

    private List<String> topologicalSort(List<Set<String>> sccs, Graph<String, DefaultWeightedEdge> graph) {
        List<String> sortedActivities = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (Set<String> scc : sccs) {
            for (String activity : scc) {
                if (!visited.contains(activity)) {
                    topologicalSortUtil(activity, visited, sortedActivities, graph);
                }
            }
        }

        Collections.reverse(sortedActivities);
        return sortedActivities;
    }

    private void topologicalSortUtil(
            String activity, Set<String> visited, List<String> sortedActivities, Graph<String, DefaultWeightedEdge> graph) {
        visited.add(activity);

        for (String neighbor : Graphs.successorListOf(graph, activity)) {
            if (!visited.contains(neighbor)) {
                topologicalSortUtil(neighbor, visited, sortedActivities, graph);
            }
        }

        sortedActivities.add(activity);
    }

    private List<Set<String>> findStronglyConnectedComponents(Graph<String, DefaultWeightedEdge> graph) {
        KosarajuStrongConnectivityInspector<String, DefaultWeightedEdge> kosaraju =
                new KosarajuStrongConnectivityInspector<>(graph);
        return kosaraju.stronglyConnectedSets();
    }

    /////////////////////////////////////////////////////////
    // Sparse Int Graph implementation to find edge to remove
    /////////////////////////////////////////////////////////

    public List<EdgeToRemoveInfo> getImpactOfSparseEdgesAboveDiagonalIfRemoved() {
        return getSparseEdgesAboveDiagonal().stream()
                .map(this::calculateSparseEdgeToRemoveInfo)
                .sorted(Comparator.comparing(EdgeToRemoveInfo::getPayoff).thenComparing(EdgeToRemoveInfo::getEdgeWeight))
                .collect(Collectors.toList());
    }

    private EdgeToRemoveInfo calculateSparseEdgeToRemoveInfo(Integer edgeToRemove) {
        //clone graph and remove edge
        int source = sparseGraph.getEdgeSource(edgeToRemove);
        int target = sparseGraph.getEdgeTarget(edgeToRemove);
        double weight = sparseGraph.getEdgeWeight(edgeToRemove);
        Triple<Integer, Integer, Double> removedEdge = Triple.of(source, target, weight);

        List<Triple<Integer, Integer, Double>> updatedEdgeList = new ArrayList<>(sparseEdges);
        updatedEdgeList.remove(removedEdge);

        SparseIntDirectedWeightedGraph improvedGraph = new SparseIntDirectedWeightedGraph(vertexCount, updatedEdgeList);

        // find edges above diagonal
        List<Integer> sortedSparseActivities = orderVertices(improvedGraph);
        List<Integer> updatedEdges = getSparseEdgesAboveDiagonal(improvedGraph, sortedSparseActivities);

        // calculate new graph statistics
        int newEdgeCount = updatedEdges.size();
        double newEdgeWeightSum = updatedEdges.stream()
                .mapToDouble(improvedGraph::getEdgeWeight).sum();
        DefaultWeightedEdge defaultWeightedEdge =
                graph.getEdge(intToVertex.get(source), intToVertex.get(target));
        double payoff = (sumOfEdgeWeightsAboveDiagonal - newEdgeWeightSum) / weight;
        return new EdgeToRemoveInfo(defaultWeightedEdge, (int) weight, newEdgeCount, newEdgeWeightSum, payoff);
    }

    private List<Integer> orderVertices(SparseIntDirectedWeightedGraph sparseGraph) {
        List<Set<Integer>> sccs = this.findStronglyConnectedSparseGraphComponents(sparseGraph);
        List<Integer> sparseIntSortedActivities = topologicalSortSparseGraph(sccs, sparseGraph);
        // reversing corrects rendering of the DSM
        // with sources as rows and targets as columns
        // was needed after AI solution was generated and iterated
        Collections.reverse(sparseIntSortedActivities);

        return sparseIntSortedActivities;
    }

    private List<Integer> getSparseEdgesAboveDiagonal(SparseIntDirectedWeightedGraph sparseGraph, List<Integer> sparseIntSortedActivities) {
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
}
