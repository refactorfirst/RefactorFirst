package org.hjug.dsm;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.alg.util.Triple;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.opt.graph.sparse.SparseIntDirectedWeightedGraph;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// TODO: Delete
class SparseIntDWGEdgeRemovalCalculator {
    private final Graph<String, DefaultWeightedEdge> graph;
    SparseIntDirectedWeightedGraph sparseGraph;
    List<Triple<Integer, Integer, Double>> sparseEdges;
    List<Integer> sparseEdgesAboveDiagonal;
    private final double sumOfEdgeWeightsAboveDiagonal;
    int vertexCount;
    Map<String, Integer> vertexToInt;
    Map<Integer, String> intToVertex;


    SparseIntDWGEdgeRemovalCalculator(
            Graph<String, DefaultWeightedEdge> graph,
            SparseIntDirectedWeightedGraph sparseGraph,
            List<Triple<Integer, Integer, Double>> sparseEdges,
            List<Integer> sparseEdgesAboveDiagonal,
            double sumOfEdgeWeightsAboveDiagonal,
            int vertexCount,
            Map<String, Integer> vertexToInt,
            Map<Integer, String> intToVertex) {
        this.graph = graph;
        this.sparseGraph = sparseGraph;
        this.sparseEdges = new CopyOnWriteArrayList<>(sparseEdges);
        this.sparseEdgesAboveDiagonal = new CopyOnWriteArrayList<>(sparseEdgesAboveDiagonal);
        this.sumOfEdgeWeightsAboveDiagonal = sumOfEdgeWeightsAboveDiagonal;
        this.vertexCount = vertexCount;
        this.vertexToInt = new ConcurrentHashMap<>(vertexToInt);
        this.intToVertex = new ConcurrentHashMap<>(intToVertex);

    }

    public List<EdgeToRemoveInfo> getImpactOfSparseEdgesAboveDiagonalIfRemoved() {
        return sparseEdgesAboveDiagonal.parallelStream()
                .map(this::calculateSparseEdgeToRemoveInfo)
                .sorted(Comparator.comparing(EdgeToRemoveInfo::getPayoff).thenComparing(EdgeToRemoveInfo::getRemovedEdgeWeight))
                .collect(Collectors.toList());
    }

    private EdgeToRemoveInfo calculateSparseEdgeToRemoveInfo(Integer edgeToRemove) {
        //clone graph and remove edge
        int source = sparseGraph.getEdgeSource(edgeToRemove);
        int target = sparseGraph.getEdgeTarget(edgeToRemove);
        double weight = sparseGraph.getEdgeWeight(edgeToRemove);
        Triple<Integer, Integer, Double> removedEdge = Triple.of(source, target, weight);

        List<Triple<Integer, Integer, Double>> tempUpdatedEdgeList = new ArrayList<>(sparseEdges);
        tempUpdatedEdgeList.remove(removedEdge);
        List<Triple<Integer, Integer, Double>> updatedEdgeList = new CopyOnWriteArrayList<>(tempUpdatedEdgeList);

        SparseIntDirectedWeightedGraph improvedGraph = new SparseIntDirectedWeightedGraph(vertexCount, updatedEdgeList);

        // find edges above diagonal
        List<Integer> sortedSparseVertices = orderVertices(improvedGraph);
        List<Integer> updatedEdges = getSparseEdgesAboveDiagonal(improvedGraph, sortedSparseVertices);

        // calculate new graph statistics
        int newEdgeCount = updatedEdges.size();
        double newEdgeWeightSum = updatedEdges.stream()
                .mapToDouble(improvedGraph::getEdgeWeight).sum();
        DefaultWeightedEdge defaultWeightedEdge =
                graph.getEdge(intToVertex.get(source), intToVertex.get(target));
        double payoff = (sumOfEdgeWeightsAboveDiagonal - newEdgeWeightSum) / weight;
        return new EdgeToRemoveInfo(defaultWeightedEdge, (int) weight, newEdgeCount, payoff);
    }

    private List<Integer> orderVertices(SparseIntDirectedWeightedGraph sparseGraph) {
        List<Set<Integer>> sccs = new CopyOnWriteArrayList<>(findStronglyConnectedSparseGraphComponents(sparseGraph));
//        List<Integer> sparseIntSortedActivities = topologicalSortSparseGraph(sccs, sparseGraph);
        List<Integer> sparseIntSortedActivities = topologicalParallelSortSparseGraph(sccs, sparseGraph);
        // reversing corrects rendering of the DSM
        // with sources as rows and targets as columns
        // was needed after AI solution was generated and iterated
        Collections.reverse(sparseIntSortedActivities);

        return new CopyOnWriteArrayList<>(sparseIntSortedActivities);
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

        sccs.parallelStream()
                .flatMap(Set::parallelStream)
                .filter(activity -> !visited.contains(activity))
                .forEach(activity -> topologicalSortUtilSparseGraph(activity, visited, sortedActivities, graph));


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

    private List<Integer> getSparseEdgesAboveDiagonal(SparseIntDirectedWeightedGraph sparseGraph, List<Integer> sortedActivities) {
        ConcurrentLinkedQueue<Integer> sparseEdgesAboveDiagonal = new ConcurrentLinkedQueue<>();

        int size = sortedActivities.size();
        IntStream.range(0, size).parallel().forEach(i -> {
            for (int j = i + 1; j < size; j++) {
                Integer edge = sparseGraph.getEdge(
                        sortedActivities.get(i),
                        sortedActivities.get(j)
                );
                if (edge != null) {
                    sparseEdgesAboveDiagonal.add(edge);
                }
            }
        });

        return new ArrayList<>(sparseEdgesAboveDiagonal);
    }

    private List<Integer> topologicalParallelSortSparseGraph(List<Set<Integer>> sccs, Graph<Integer, Integer> graph) {
        ConcurrentLinkedQueue<Integer> sortedActivities = new ConcurrentLinkedQueue<>();
        Set<Integer> visited = new ConcurrentSkipListSet<>();

        sccs.parallelStream()
                .flatMap(Set::parallelStream)
                .filter(activity -> !visited.contains(activity))
                .forEach(activity -> topologicalSortUtilSparseGraph(activity, visited, sortedActivities, graph));

        ArrayList<Integer> sortedActivitiesList = new ArrayList<>(sortedActivities);
        Collections.reverse(sortedActivitiesList);
        return sortedActivitiesList;
    }

    private void topologicalSortUtilSparseGraph(
            Integer activity, Set<Integer> visited, ConcurrentLinkedQueue<Integer> sortedActivities, Graph<Integer, Integer> graph) {
        visited.add(activity);

        Graphs.successorListOf(graph, activity).parallelStream()
                .filter(neighbor -> !visited.contains(neighbor))
                .forEach(neighbor -> topologicalSortUtilSparseGraph(neighbor, visited, sortedActivities, graph));

        sortedActivities.add(activity);
    }

}
