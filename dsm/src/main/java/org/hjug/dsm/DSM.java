package org.hjug.dsm;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.alg.cycle.TarjanSimpleCycles;

import java.util.*;

/*
 * Generated with Generative AI using a prompt similar to the following:
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
 */


//This is looking good, but is still a work in progress.  This may not be needed at all.
public class DSM {
    private Graph<String, DefaultWeightedEdge> graph;
    private List<String> sortedActivities;

    public DSM() {
        graph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
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

    public void orderVertices() {
        List<Set<String>> sccs = findStronglyConnectedComponents();
        sortedActivities = topologicalSort(sccs);
    }

    public void printDSM() {
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
                    DefaultWeightedEdge edge = graph.getEdge(col, row); // Flipped matrix
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

    private List<Set<String>> findStronglyConnectedComponents() {
        TarjanSimpleCycles<String, DefaultWeightedEdge> tarjan = new TarjanSimpleCycles<>(graph);
        List<List<String>> cycles = tarjan.findSimpleCycles();
        List<Set<String>> sccs = new ArrayList<>();
        for (List<String> cycle : cycles) {
            sccs.add(new HashSet<>(cycle));
        }
        return sccs;
    }

    private List<String> topologicalSort(List<Set<String>> sccs) {
        List<String> sortedActivities = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (Set<String> scc : sccs) {
            for (String activity : scc) {
                if (!visited.contains(activity)) {
                    topologicalSortUtil(activity, visited, sortedActivities);
                }
            }
        }

        Collections.reverse(sortedActivities);
        return sortedActivities;
    }

    private void topologicalSortUtil(String activity, Set<String> visited, List<String> sortedActivities) {
        visited.add(activity);

        for (String neighbor : Graphs.successorListOf(graph, activity)) {
            if (!visited.contains(neighbor)) {
                topologicalSortUtil(neighbor, visited, sortedActivities);
            }
        }

        sortedActivities.add(activity);
    }

    public List<DefaultWeightedEdge> getEdgesAboveDiagonal() {
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

    public DefaultWeightedEdge getOptimalEdgeAboveDiagonalToRemove() {
        List<DefaultWeightedEdge> edgesAboveDiagonal = getEdgesAboveDiagonal();
        DefaultWeightedEdge optimalEdge = null;
        double minWeight = Double.MAX_VALUE;

        for (DefaultWeightedEdge edge : edgesAboveDiagonal) {
            double weight = graph.getEdgeWeight(edge);
            if (weight < minWeight) {
                minWeight = weight;
                optimalEdge = edge;
            }
        }

        return optimalEdge;
    }

    public List<DefaultWeightedEdge> getMinimumWeightEdgesToRemove() {
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

    public static void main(String[] args) {
        DSM dsm = new DSM();
        dsm.addActivity("A");
        dsm.addActivity("B");
        dsm.addActivity("C");
        dsm.addActivity("D");
        dsm.addActivity("E");
        dsm.addActivity("F");
        dsm.addActivity("G");
        dsm.addActivity("H");

        dsm.addDependency("A", "B", 1);
//        dsm.addDependency("A", "C", 6);
        dsm.addDependency("B", "C", 2);
//        dsm.addDependency("B", "D", 3);
        dsm.addDependency("C", "D", 2);
        dsm.addDependency("D", "A", 2); // Adding a cycle

        dsm.addDependency("C", "A", 7); // Adding a cycle
        dsm.addDependency("G", "E", 2); // Adding a cycle
        dsm.addDependency("E", "H", 2); // Adding a cycle
        dsm.addDependency("H", "G", 5); // Adding a cycle
        dsm.addDependency("A", "H", 7); // Adding a cycle
        dsm.addDependency("H", "D", 9); // Adding a cycle
        dsm.addDependency("C", "E", 9); // Adding a cycle


        dsm.orderVertices();
        dsm.printDSM();

        System.out.println("Optimal edge above diagonal to remove: " + dsm.getOptimalEdgeAboveDiagonalToRemove());
    }
}