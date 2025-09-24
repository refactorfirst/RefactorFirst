package org.hjug.feedback.arc.pageRank;



import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PageRankFAS - A PageRank-based algorithm for computing Feedback Arc Set
 * Based on the paper "Computing a Feedback Arc Set Using PageRank" by
 * Geladaris, Lionakis, and Tollis
 */
public class PageRankFAS<V, E> {

    private static final int DEFAULT_PAGERANK_ITERATIONS = 5;
    private static final double CONVERGENCE_THRESHOLD = 1e-6;

    private final Graph<V, E> originalGraph;
    private final int pageRankIterations;

    /**
     * Constructor for PageRankFAS algorithm
     * @param graph The input directed graph
     */
    public PageRankFAS(Graph<V, E> graph) {
        this(graph, DEFAULT_PAGERANK_ITERATIONS);
    }

    /**
     * Constructor with custom PageRank iterations
     * @param graph The input directed graph
     * @param pageRankIterations Number of PageRank iterations
     */
    public PageRankFAS(Graph<V, E> graph, int pageRankIterations) {
        this.originalGraph = graph;
        this.pageRankIterations = pageRankIterations;
    }

    /**
     * Main method to compute the Feedback Arc Set
     * @return Set of edges that form the feedback arc set
     */
    public Set<E> computeFeedbackArcSet() {
        Set<E> feedbackArcSet = ConcurrentHashMap.newKeySet();

        // Create a working copy of the graph
        Graph<V, E> workingGraph = createGraphCopy(originalGraph);

        // Continue until the graph becomes acyclic
        while (hasCycles(workingGraph)) {
            // Find strongly connected components
            List<Set<V>> sccs = findStronglyConnectedComponents(workingGraph);

            // Process each SCC in parallel
            sccs.parallelStream()
                    .filter(scc -> scc.size() > 1) // Only non-trivial SCCs can have cycles
                    .forEach(scc -> {
                        E edgeToRemove = processStronglyConnectedComponent(workingGraph, scc);
                        if (edgeToRemove != null) {
                            synchronized (feedbackArcSet) {
                                feedbackArcSet.add(edgeToRemove);
                                workingGraph.removeEdge(edgeToRemove);
                            }
                        }
                    });
        }

        return feedbackArcSet;
    }

    /**
     * Process a single strongly connected component
     * @param graph The working graph
     * @param scc The strongly connected component vertices
     * @return The edge with the highest PageRank score to remove
     */
    private E processStronglyConnectedComponent(Graph<V, E> graph, Set<V> scc) {
        // Create subgraph for this SCC
        Graph<V, E> sccGraph = createSubgraph(graph, scc);

        // Create line digraph using the new custom implementation
        LineDigraph<V, E> lineDigraph = createLineDigraph(sccGraph);

        // Run PageRank on line digraph
        Map<LineVertex<V, E>, Double> pageRankScores = computePageRank(lineDigraph);

        // Find the edge (line vertex) with highest PageRank score
        return pageRankScores.entrySet().parallelStream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey().getOriginalEdge())
                .orElse(null);
    }

    /**
     * Create line digraph from the input graph using custom LineDigraph implementation
     * @param graph Input graph
     * @return LineDigraph representation
     */
    private LineDigraph<V, E> createLineDigraph(Graph<V, E> graph) {
        LineDigraph<V, E> lineDigraph = new LineDigraph<>();

        // Create nodes in line digraph (one for each edge in original graph)
        Map<E, LineVertex<V, E>> edgeToLineVertex = new ConcurrentHashMap<>();

        graph.edgeSet().parallelStream().forEach(edge -> {
            V source = graph.getEdgeSource(edge);
            V target = graph.getEdgeTarget(edge);
            LineVertex<V, E> lineVertex = new LineVertex<>(source, target, edge);
            edgeToLineVertex.put(edge, lineVertex);
            lineDigraph.addVertex(lineVertex);
        });

        // Create edges in line digraph using DFS-based approach from the paper
        createLineDigraphEdges(graph, lineDigraph, edgeToLineVertex);

        return lineDigraph;
    }

    /**
     * Create edges in line digraph based on Algorithm 3 from the paper
     * Updated to use custom LineDigraph methods
     */
    private void createLineDigraphEdges(Graph<V, E> graph, LineDigraph<V, E> lineDigraph,
                                        Map<E, LineVertex<V, E>> edgeToLineVertex) {
        Set<V> visited = ConcurrentHashMap.newKeySet();

        // Start DFS from a random vertex if graph is not empty
        if (!graph.vertexSet().isEmpty()) {
            V startVertex = graph.vertexSet().iterator().next();
            createLineDigraphEdgesDFS(graph, lineDigraph, edgeToLineVertex,
                    startVertex, null, visited);
        }
    }

    /**
     * DFS-based creation of line digraph edges (Algorithm 3 implementation)
     * Updated to use custom LineDigraph.addEdge method
     */
    private void createLineDigraphEdgesDFS(Graph<V, E> graph, LineDigraph<V, E> lineDigraph,
                                           Map<E, LineVertex<V, E>> edgeToLineVertex,
                                           V vertex, LineVertex<V, E> prevLineVertex,
                                           Set<V> visited) {
        visited.add(vertex);

        // Get outgoing edges from current vertex
        Set<E> outgoingEdges = graph.outgoingEdgesOf(vertex);

        for (E edge : outgoingEdges) {
            V target = graph.getEdgeTarget(edge);
            LineVertex<V, E> currentLineVertex = edgeToLineVertex.get(edge);

            // Add edge from previous line vertex to current (if prev exists)
            if (prevLineVertex != null) {
                lineDigraph.addEdge(prevLineVertex, currentLineVertex);
            }

            if (!visited.contains(target)) {
                // Continue DFS
                createLineDigraphEdgesDFS(graph, lineDigraph, edgeToLineVertex,
                        target, currentLineVertex, visited);
            } else {
                // Target is already visited - add edges to all line vertices originating from target
                graph.outgoingEdgesOf(target).stream()
                        .map(edgeToLineVertex::get)
                        .forEach(targetLineVertex ->
                                lineDigraph.addEdge(currentLineVertex, targetLineVertex));
            }
        }
    }

    /**
     * Compute PageRank scores on the line digraph (Algorithm 4 implementation)
     * Updated to use custom LineDigraph methods
     * @param lineDigraph The line digraph
     * @return Map of line vertices to their PageRank scores
     */
    private Map<LineVertex<V, E>, Double> computePageRank(LineDigraph<V, E> lineDigraph) {
        Set<LineVertex<V, E>> vertices = lineDigraph.vertexSet();
        int numVertices = vertices.size();

        if (numVertices == 0) return new HashMap<>();

        // Initialize PageRank scores
        Map<LineVertex<V, E>, Double> currentScores = new ConcurrentHashMap<>();
        Map<LineVertex<V, E>, Double> newScores = new ConcurrentHashMap<>();

        double initialScore = 1.0 / numVertices;
        vertices.parallelStream().forEach(vertex -> {
            currentScores.put(vertex, initialScore);
            newScores.put(vertex, 0.0);
        });

        // Run PageRank iterations
        for (int iteration = 0; iteration < pageRankIterations; iteration++) {
            // Reset new scores
            vertices.parallelStream().forEach(vertex -> newScores.put(vertex, 0.0));

            // Compute new scores in parallel
            vertices.parallelStream().forEach(vertex -> {
                double score = currentScores.get(vertex);
                Set<LineVertex<V, E>> outgoingNeighbors = lineDigraph.getOutgoingNeighbors(vertex);

                if (outgoingNeighbors.isEmpty()) {
                    // No outgoing edges - keep score to self (sink behavior)
                    synchronized (newScores) {
                        newScores.put(vertex, newScores.get(vertex) + score);
                    }
                } else {
                    // Distribute score equally among outgoing edges
                    double scorePerEdge = score / outgoingNeighbors.size();
                    outgoingNeighbors.parallelStream().forEach(target -> {
                        synchronized (newScores) {
                            newScores.put(target, newScores.get(target) + scorePerEdge);
                        }
                    });
                }
            });

            // Swap score maps
            Map<LineVertex<V, E>, Double> temp = currentScores;
            currentScores = newScores;
            newScores = temp;
        }

        return currentScores;
    }

    /**
     * Find strongly connected components using Kosaraju's algorithm
     */
    private List<Set<V>> findStronglyConnectedComponents(Graph<V, E> graph) {
        KosarajuStrongConnectivityInspector<V, E> inspector =
                new KosarajuStrongConnectivityInspector<>(graph);
        return inspector.stronglyConnectedSets();
    }

    /**
     * Check if graph has cycles
     */
    private boolean hasCycles(Graph<V, E> graph) {
        CycleDetector<V, E> detector = new CycleDetector<>(graph);
        return detector.detectCycles();
    }

    /**
     * Create a copy of the graph
     */
    private Graph<V, E> createGraphCopy(Graph<V, E> original) {
        Graph<V, E> copy = new DefaultDirectedGraph<>(original.getEdgeSupplier());

        // Add vertices
        original.vertexSet().forEach(copy::addVertex);

        // Add edges
        original.edgeSet().forEach(edge -> {
            V source = original.getEdgeSource(edge);
            V target = original.getEdgeTarget(edge);
            copy.addEdge(source, target, edge);
        });

        return copy;
    }

    /**
     * Create subgraph containing only specified vertices and their edges
     */
    private Graph<V, E> createSubgraph(Graph<V, E> graph, Set<V> vertices) {
        Graph<V, E> subgraph = new DefaultDirectedGraph<>(graph.getEdgeSupplier());

        // Add vertices
        vertices.forEach(subgraph::addVertex);

        // Add edges between vertices in the set
        graph.edgeSet().parallelStream()
                .filter(edge -> vertices.contains(graph.getEdgeSource(edge)) &&
                        vertices.contains(graph.getEdgeTarget(edge)))
                .forEach(edge -> {
                    V source = graph.getEdgeSource(edge);
                    V target = graph.getEdgeTarget(edge);
                    subgraph.addEdge(source, target, edge);
                });

        return subgraph;
    }

    /**
     * Get detailed statistics about the algorithm execution
     * @return Map containing execution statistics
     */
    public Map<String, Object> getExecutionStatistics(Graph<V, E> graph) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("originalVertices", graph.vertexSet().size());
        stats.put("originalEdges", graph.edgeSet().size());
        stats.put("pageRankIterations", pageRankIterations);

        // Analyze SCCs
        List<Set<V>> sccs = findStronglyConnectedComponents(graph);
        stats.put("sccCount", sccs.size());
        stats.put("trivialSCCs", sccs.stream().mapToInt(scc -> scc.size() == 1 ? 1 : 0).sum());
        stats.put("nonTrivialSCCs", sccs.stream().mapToInt(scc -> scc.size() > 1 ? 1 : 0).sum());

        // Find largest SCC
        int maxSCCSize = sccs.stream().mapToInt(Set::size).max().orElse(0);
        stats.put("largestSCCSize", maxSCCSize);

        return stats;
    }
}

/**
 * Represents a vertex in the line digraph (corresponds to an edge in original graph)
 */
class LineVertex<V, E> {
    private final V source;
    private final V target;
    private final E originalEdge;

    public LineVertex(V source, V target, E originalEdge) {
        this.source = source;
        this.target = target;
        this.originalEdge = originalEdge;
    }

    public V getSource() { return source; }
    public V getTarget() { return target; }
    public E getOriginalEdge() { return originalEdge; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LineVertex)) return false;
        LineVertex<?, ?> other = (LineVertex<?, ?>) obj;
        return Objects.equals(originalEdge, other.originalEdge);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalEdge);
    }

    @Override
    public String toString() {
        return String.format("LineVertex(%s->%s)", source, target);
    }
}
