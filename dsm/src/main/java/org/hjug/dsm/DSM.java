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
    private Graph<String, DefaultWeightedEdge> graph;
    private List<String> sortedActivities;
    boolean activitiesSorted = false;

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
        SparseIntDirectedWeightedGraph sparseGraph = getSparseIntDirectedWeightedGraph();
        List<Set<Integer>> sccs = findStronglyConnectedComponents(sparseGraph);
        sortedActivities = convertIntToStringVertices(topologicalSort(sccs, sparseGraph));
        // reversing corrects rendering of the DSM
        // with sources as rows and targets as columns
        // was needed after AI solution was generated and iterated
        Collections.reverse(sortedActivities);
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
     * @param graph
     * @return
     */
    private List<Set<Integer>> findStronglyConnectedComponents(Graph<Integer, Integer> graph) {
        KosarajuStrongConnectivityInspector<Integer, Integer> kosaraju =
                new KosarajuStrongConnectivityInspector<>(graph);
        return kosaraju.stronglyConnectedSets();
    }

    private List<Integer> topologicalSort(List<Set<Integer>> sccs, Graph<Integer, Integer> graph) {
        List<Integer> sortedActivities = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();

        for (Set<Integer> scc : sccs) {
            for (Integer activity : scc) {
                if (!visited.contains(activity)) {
                    topologicalSortUtil(activity, visited, sortedActivities, graph);
                }
            }
        }

        Collections.reverse(sortedActivities);
        return sortedActivities;
    }

    private void topologicalSortUtil(
            Integer activity, Set<Integer> visited, List<Integer> sortedActivities, Graph<Integer, Integer> graph) {
        visited.add(activity);

        for (Integer neighbor : Graphs.successorListOf(graph, activity)) {
            if (!visited.contains(neighbor)) {
                topologicalSortUtil(neighbor, visited, sortedActivities, graph);
            }
        }

        sortedActivities.add(activity);
    }

    public List<DefaultWeightedEdge> getEdgesAboveDiagonal() {
        if (!activitiesSorted) {
            orderVertices();
        }

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

    /**
     * Captures the impact of the removal of each edge above the diagonal.
     * Limited to 50 by default
     *
     * @return List<EdgeToRemoveInfo> Impact of each edge if removed.
     */
    public List<EdgeToRemoveInfo> getImpactOfEdgesAboveDiagonalIfRemoved(int limit) {
        // get edges above diagonal for DSM graph
        List<DefaultWeightedEdge> edgesAboveDiagonal;
        List<DefaultWeightedEdge> allEdgesAboveDiagonal = getEdgesAboveDiagonal();

        if (limit == 0 || allEdgesAboveDiagonal.size() <= 50) {
            edgesAboveDiagonal = allEdgesAboveDiagonal;
        } else {
            // get first 50 values of min weight
            List<DefaultWeightedEdge> minimumWeightEdgesAboveDiagonal = getMinimumWeightEdgesAboveDiagonal();
            int max = Math.min(minimumWeightEdgesAboveDiagonal.size(), 50);
            edgesAboveDiagonal = minimumWeightEdgesAboveDiagonal.subList(0, max);
        }

        double avgCycleNodeCount = getAverageCycleNodeCount();

        // build the cloned graph
        Graph<String, DefaultWeightedEdge> clonedGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        graph.vertexSet().forEach(clonedGraph::addVertex);
        for (DefaultWeightedEdge weightedEdge : graph.edgeSet()) {
            clonedGraph.addEdge(graph.getEdgeSource(weightedEdge), graph.getEdgeTarget(weightedEdge), weightedEdge);
        }

        List<EdgeToRemoveInfo> edgesToRemove = new ArrayList<>();
        // capture impact of each edge on graph when removed
        for (DefaultWeightedEdge edge : edgesAboveDiagonal) {
            int edgeInCyclesCount = 0;
            for (AsSubgraph<String, DefaultWeightedEdge> cycle : cycles.values()) {
                if (cycle.containsEdge(edge)) {
                    edgeInCyclesCount++;
                }
            }

            // remove the edge
            clonedGraph.removeEdge(edge);

            // identify updated cycles and calculate updated graph information
            edgesToRemove.add(getEdgeToRemoveInfo(
                    edge, edgeInCyclesCount, avgCycleNodeCount, new CircularReferenceChecker().getCycles(clonedGraph)));

            // add the edge back for next iteration
            clonedGraph.addEdge(graph.getEdgeSource(edge), graph.getEdgeTarget(edge), edge);
            clonedGraph.setEdgeWeight(edge, graph.getEdgeWeight(edge));
        }

        edgesToRemove.sort(Comparator.comparing(EdgeToRemoveInfo::getPayoff));
        Collections.reverse(edgesToRemove);
        return edgesToRemove;
    }

    private EdgeToRemoveInfo getEdgeToRemoveInfo(
            DefaultWeightedEdge edge,
            int edgeInCyclesCount,
            double currentAvgCycleNodeCount,
            Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles) {
        // get the new number of cycles
        int newCycleCount = cycles.size();

        // calculate the average cycle node count
        double newAverageCycleNodeCount = getAverageCycleNodeCount(cycles);

        // capture the what-if values
        double edgeWeight = graph.getEdgeWeight(edge);

        double impact = (currentAvgCycleNodeCount - newAverageCycleNodeCount) / edgeWeight;
        return new EdgeToRemoveInfo(
                edge, edgeWeight, edgeInCyclesCount, newCycleCount, newAverageCycleNodeCount, impact);
    }

    public static double getAverageCycleNodeCount(Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles) {
        return cycles.values().stream()
                .mapToInt(cycle -> cycle.vertexSet().size())
                .average()
                .orElse(0.0);
    }

    public double getAverageCycleNodeCount() {
        return cycles.values().stream()
                .mapToInt(cycle -> cycle.vertexSet().size())
                .average()
                .orElse(0.0);
    }
}
