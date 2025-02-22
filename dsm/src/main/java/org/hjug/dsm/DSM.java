package org.hjug.dsm;

import java.util.*;
import lombok.Getter;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.cycle.TarjanSimpleCycles;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

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

    @Getter
    int stronglyConnectedComponentCount = 0;

    @Getter
    double averageStronglyConnectedComponentSize = 0.0;

    public DSM() {
        graph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        sortedActivities = new ArrayList<>();
    }

    public DSM(Graph<String, DefaultWeightedEdge> graph) {
        this.graph = graph;
        sortedActivities = new ArrayList<>();
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
        List<Set<String>> sccs = findStronglyConnectedComponents(graph);

        // capture the number of cycles
        stronglyConnectedComponentCount = sccs.size();

        // capture the average size of the cycles
        averageStronglyConnectedComponentSize =
                sccs.stream().mapToInt(Set::size).average().orElse(0.0);

        sortedActivities = topologicalSort(sccs, graph);
        // reversing corrects rendering of the DSM
        // with sources as rows and targets as columns
        // was needed after AI solution was generated and iterated
        Collections.reverse(sortedActivities);
        activitiesSorted = true;
    }

    private List<Set<String>> findStronglyConnectedComponents(Graph<String, DefaultWeightedEdge> graph) {
        TarjanSimpleCycles<String, DefaultWeightedEdge> tarjan = new TarjanSimpleCycles<>(graph);
        List<List<String>> cycles = tarjan.findSimpleCycles();
        List<Set<String>> sccs = new ArrayList<>();
        for (List<String> cycle : cycles) {
            sccs.add(new HashSet<>(cycle));
        }
        return sccs;
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
            String activity,
            Set<String> visited,
            List<String> sortedActivities,
            Graph<String, DefaultWeightedEdge> graph) {
        visited.add(activity);

        for (String neighbor : Graphs.successorListOf(graph, activity)) {
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
     *
     * @return List<EdgeToRemoveInfo> Impact of each edge if removed.
     */
    public List<EdgeToRemoveInfo> getImpactOfEdgesAboveDiagonalIfRemoved() {
        // get edges above diagonal for DSM graph
        List<DefaultWeightedEdge> edgesAboveDiagonal = getEdgesAboveDiagonal();

        // build the cloned graph
        Graph<String, DefaultWeightedEdge> clonedGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        graph.vertexSet().forEach(clonedGraph::addVertex);
        for (DefaultWeightedEdge weightedEdge : graph.edgeSet()) {
            clonedGraph.addEdge(graph.getEdgeSource(weightedEdge), graph.getEdgeTarget(weightedEdge), weightedEdge);
        }

        List<EdgeToRemoveInfo> edgesToRemove = new ArrayList<>();
        // capture impact of each edge on graph when removed
        for (DefaultWeightedEdge edge : edgesAboveDiagonal) {
            // remove the edge
            clonedGraph.removeEdge(edge);

            // identify updated cycles and calculate updated graph information
            edgesToRemove.add(getEdgeToRemoveInfo(edge, new CircularReferenceChecker().getCycles(clonedGraph)));

            // add the edge back for next iteration
            clonedGraph.addEdge(graph.getEdgeSource(edge), graph.getEdgeTarget(edge), edge);
            clonedGraph.setEdgeWeight(edge, graph.getEdgeWeight(edge));
        }

        return edgesToRemove;
    }

    private EdgeToRemoveInfo getEdgeToRemoveInfo(
            DefaultWeightedEdge edge, Map<String, AsSubgraph<String, DefaultWeightedEdge>> cycles) {
        // get the new number of cycles
        int cycleCount = cycles.size();

        // calculate the average cycle node count
        double averageCycleNodeCount = cycles.values().stream()
                .mapToInt(cycle -> cycle.vertexSet().size())
                .average()
                .orElse(0.0);

        // capture the what-if values
        return new EdgeToRemoveInfo(edge, graph.getEdgeWeight(edge), cycleCount, averageCycleNodeCount);
    }
}
