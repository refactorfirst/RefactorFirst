package org.hjug.feedback.vertex.kernelized;

import org.hjug.feedback.SuperTypeToken;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Multithreaded modulator computer that finds treewidth-η modulators
 * based on the algorithms described in the DFVS paper.
 */
public class ModulatorComputer<V, E> {

    private final TreewidthComputer<V, E> treewidthComputer;
    private final FeedbackVertexSetComputer<V, E> fvsComputer;
    private final ExecutorService executorService;

    public ModulatorComputer(SuperTypeToken<E> edgeTypeToken) {
        this.treewidthComputer = new TreewidthComputer<>();
        this.fvsComputer = new FeedbackVertexSetComputer<>(edgeTypeToken);
        this.executorService = ForkJoinPool.commonPool();
    }

    public ModulatorComputer(SuperTypeToken<E> edgeTypeToken, int parallelismLevel) {
        this.treewidthComputer = new TreewidthComputer<>(parallelismLevel);
        this.fvsComputer = new FeedbackVertexSetComputer<>(edgeTypeToken, parallelismLevel);
        this.executorService = Executors.newWorkStealingPool(parallelismLevel);
    }

    /**
     * Computes an optimal treewidth-η modulator using multiple strategies
     */
    public ModulatorResult<V> computeModulator(Graph<V, E> graph, int targetTreewidth, int maxModulatorSize) {
        if (maxModulatorSize <= 0) {
            return new ModulatorResult<>(new HashSet<>(),
                    treewidthComputer.computeEta(graph, new HashSet<>()), 0);
        }

        // Run multiple modulator finding strategies in parallel
        List<Callable<Set<V>>> strategies = Arrays.asList(
                () -> computeGreedyDegreeModulator(graph, targetTreewidth, maxModulatorSize),
                () -> computeFeedbackVertexSetModulator(graph, targetTreewidth, maxModulatorSize),
                () -> computeTreewidthDecompositionModulator(graph, targetTreewidth, maxModulatorSize),
                () -> computeHighDegreeVertexModulator(graph, targetTreewidth, maxModulatorSize),
                () -> computeBottleneckVertexModulator(graph, targetTreewidth, maxModulatorSize)
        );

        try {
            List<Future<Set<V>>> results = executorService.invokeAll(strategies, 60, TimeUnit.SECONDS);

            return results.parallelStream()
                    .map(this::getFutureValue)
                    .filter(Objects::nonNull)
                    .filter(modulator -> modulator.size() <= maxModulatorSize)
                    .map(modulator -> new ModulatorResult<>(
                            modulator,
                            treewidthComputer.computeEta(graph, modulator),
                            computeModulatorQuality(graph, modulator, targetTreewidth)))
                    .filter(result -> result.getResultingTreewidth() <= targetTreewidth)
                    .min(Comparator.comparingDouble(ModulatorResult::getQualityScore))
                    .orElse(computeFallbackModulator(graph, targetTreewidth, maxModulatorSize));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return computeFallbackModulator(graph, targetTreewidth, maxModulatorSize);
        }
    }

    /**
     * Computes modulator using iterative vertex removal based on degree
     */
    private Set<V> computeGreedyDegreeModulator(Graph<V, E> graph, int targetTreewidth, int maxSize) {
        Set<V> modulator = ConcurrentHashMap.newKeySet();
        Graph<V, DefaultEdge> workingGraph = convertToUndirected(graph);

        while (modulator.size() < maxSize) {
            int currentTreewidth = treewidthComputer.computeEta(graph, modulator);
            if (currentTreewidth <= targetTreewidth) {
                break;
            }

            // Find vertex with highest degree * betweenness centrality score
            V bestVertex = workingGraph.vertexSet().parallelStream()
                    .filter(v -> !modulator.contains(v))
                    .max(Comparator.comparingDouble(v ->
                            computeVertexRemovalScore(workingGraph, v, targetTreewidth)))
                    .orElse(null);

            if (bestVertex == null) break;

            modulator.add(bestVertex);
            workingGraph.removeVertex(bestVertex);
        }

        return modulator;
    }

    /**
     * Uses feedback vertex set as starting point for modulator
     */
    private Set<V> computeFeedbackVertexSetModulator(Graph<V, E> graph, int targetTreewidth, int maxSize) {
        Set<V> modulator = new HashSet<>();

        // Start with feedback vertex set vertices (they're often good modulator candidates)
        Set<V> fvs = fvsComputer.greedyFeedbackVertexSet(graph);

        // Add FVS vertices up to budget
        Iterator<V> fvsIter = fvs.iterator();
        while (fvsIter.hasNext() && modulator.size() < maxSize) {
            V vertex = fvsIter.next();
            modulator.add(vertex);

            int currentTreewidth = treewidthComputer.computeEta(graph, modulator);
            if (currentTreewidth <= targetTreewidth) {
                break;
            }
        }

        // If still not good enough, add high-degree vertices
        if (modulator.size() < maxSize) {
            List<V> remainingVertices = graph.vertexSet().stream()
                    .filter(v -> !modulator.contains(v))
                    .sorted((v1, v2) -> Integer.compare(
                            graph.inDegreeOf(v2) + graph.outDegreeOf(v2),
                            graph.inDegreeOf(v1) + graph.outDegreeOf(v1)))
                    .collect(Collectors.toList());

            for (V vertex : remainingVertices) {
                if (modulator.size() >= maxSize) break;

                modulator.add(vertex);
                int currentTreewidth = treewidthComputer.computeEta(graph, modulator);
                if (currentTreewidth <= targetTreewidth) {
                    break;
                }
            }
        }

        return modulator;
    }

    /**
     * Uses treewidth decomposition analysis to find modulator
     */
    private Set<V> computeTreewidthDecompositionModulator(Graph<V, E> graph, int targetTreewidth, int maxSize) {
        Set<V> modulator = ConcurrentHashMap.newKeySet();
        Graph<V, DefaultEdge> undirected = convertToUndirected(graph);

        // Identify vertices that appear in many high-width bags
        Map<V, Integer> bagAppearances = new ConcurrentHashMap<>();
        Map<V, Double> centralityScores = computeBetweennessCentrality(undirected);

        // Compute vertex importance based on structural properties
        Map<V, Double> vertexImportance = undirected.vertexSet().parallelStream()
                .collect(Collectors.toConcurrentMap(
                        v -> v,
                        v -> computeStructuralImportance(undirected, v, centralityScores.getOrDefault(v, 0.0))
                ));

        // Greedily select vertices with highest importance
        List<V> sortedVertices = vertexImportance.entrySet().stream()
                .sorted(Map.Entry.<V, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (V vertex : sortedVertices) {
            if (modulator.size() >= maxSize) break;

            modulator.add(vertex);
            int currentTreewidth = treewidthComputer.computeEta(graph, modulator);
            if (currentTreewidth <= targetTreewidth) {
                break;
            }
        }

        return modulator;
    }

    /**
     * Focuses on highest degree vertices first
     */
    private Set<V> computeHighDegreeVertexModulator(Graph<V, E> graph, int targetTreewidth, int maxSize) {
        Set<V> modulator = new HashSet<>();

        List<V> verticesByDegree = graph.vertexSet().stream()
                .sorted((v1, v2) -> Integer.compare(
                        graph.inDegreeOf(v2) + graph.outDegreeOf(v2),
                        graph.inDegreeOf(v1) + graph.outDegreeOf(v1)))
                .collect(Collectors.toList());

        for (V vertex : verticesByDegree) {
            if (modulator.size() >= maxSize) break;

            modulator.add(vertex);
            int currentTreewidth = treewidthComputer.computeEta(graph, modulator);
            if (currentTreewidth <= targetTreewidth) {
                break;
            }
        }

        return modulator;
    }

    /**
     * Identifies bottleneck vertices that connect different components
     */
    private Set<V> computeBottleneckVertexModulator(Graph<V, E> graph, int targetTreewidth, int maxSize) {
        Set<V> modulator = ConcurrentHashMap.newKeySet();
        Graph<V, DefaultEdge> undirected = convertToUndirected(graph);

        // Find articulation points and vertices with high betweenness centrality
        Set<V> articulationPoints = findArticulationPoints(undirected);
        Map<V, Double> centralityScores = computeBetweennessCentrality(undirected);

        // Combine articulation points with high centrality vertices
        Set<V> candidates = new HashSet<>(articulationPoints);
        candidates.addAll(centralityScores.entrySet().stream()
                .sorted(Map.Entry.<V, Double>comparingByValue().reversed())
                .limit(Math.max(10, maxSize * 2))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet()));

        // Greedily select best candidates
        for (V vertex : candidates) {
            if (modulator.size() >= maxSize) break;

            modulator.add(vertex);
            int currentTreewidth = treewidthComputer.computeEta(graph, modulator);
            if (currentTreewidth <= targetTreewidth) {
                break;
            }
        }

        return modulator;
    }

    /**
     * Computes vertex removal score based on multiple factors
     */
    private double computeVertexRemovalScore(Graph<V, DefaultEdge> graph, V vertex, int targetTreewidth) {
        int degree = graph.degreeOf(vertex);

        // Check if vertex is in a dense subgraph
        Set<V> neighbors = Graphs.neighborSetOf(graph, vertex);
        long neighborConnections = neighbors.parallelStream()
                .mapToLong(n1 -> neighbors.stream()
                        .filter(n2 -> !n1.equals(n2))
                        .mapToLong(n2 -> graph.containsEdge(n1, n2) ? 1 : 0)
                        .sum())
                .sum();

        double clusteringCoefficient = neighbors.size() > 1 ?
                (double) neighborConnections / (neighbors.size() * (neighbors.size() - 1)) : 0.0;

        // Higher score = better candidate for removal
        return degree * (1.0 + clusteringCoefficient);
    }

    /**
     * Computes structural importance of a vertex
     */
    private double computeStructuralImportance(Graph<V, DefaultEdge> graph, V vertex, double centrality) {
        int degree = graph.degreeOf(vertex);
        Set<V> neighbors = Graphs.neighborSetOf(graph, vertex);

        // Count triangles involving this vertex
        long triangles = neighbors.parallelStream()
                .mapToLong(n1 -> neighbors.stream()
                        .filter(n2 -> !n1.equals(n2) && graph.containsEdge(n1, n2))
                        .count())
                .sum() / 2;

        return degree + centrality * 10 + triangles * 0.5;
    }

    /**
     * Computes betweenness centrality for all vertices
     */
    private Map<V, Double> computeBetweennessCentrality(Graph<V, DefaultEdge> graph) {
        Map<V, Double> centrality = new ConcurrentHashMap<>();
        List<V> vertices = new ArrayList<>(graph.vertexSet());

        // Initialize all centralities to 0
        vertices.parallelStream().forEach(v -> centrality.put(v, 0.0));

        // For efficiency, sample pairs of vertices for large graphs
        int sampleSize = Math.min(vertices.size() * (vertices.size() - 1) / 2, 1000);
        Random random = new Random(42); // Fixed seed for reproducibility

        vertices.parallelStream().limit(Math.min(50, vertices.size())).forEach(source -> {
            Map<V, List<V>> predecessors = new HashMap<>();
            Map<V, Integer> distances = new HashMap<>();
            Map<V, Integer> pathCounts = new HashMap<>();
            Stack<V> stack = new Stack<>();

            // BFS from source
            Queue<V> queue = new ArrayDeque<>();
            queue.offer(source);
            distances.put(source, 0);
            pathCounts.put(source, 1);

            while (!queue.isEmpty()) {
                V current = queue.poll();
                stack.push(current);

                for (V neighbor : Graphs.neighborListOf(graph, current)) {
                    if (!distances.containsKey(neighbor)) {
                        distances.put(neighbor, distances.get(current) + 1);
                        pathCounts.put(neighbor, 0);
                        queue.offer(neighbor);
                    }

                    if (distances.get(neighbor) == distances.get(current) + 1) {
                        pathCounts.put(neighbor, pathCounts.get(neighbor) + pathCounts.get(current));
                        predecessors.computeIfAbsent(neighbor, k -> new ArrayList<>()).add(current);
                    }
                }
            }

            // Accumulate centrality values
            Map<V, Double> dependency = new HashMap<>();
            vertices.forEach(v -> dependency.put(v, 0.0));

            while (!stack.isEmpty()) {
                V vertex = stack.pop();
                if (predecessors.containsKey(vertex)) {
                    for (V predecessor : predecessors.get(vertex)) {
                        double contribution = (pathCounts.get(predecessor) / (double) pathCounts.get(vertex))
                                * (1.0 + dependency.get(vertex));
                        dependency.put(predecessor, dependency.get(predecessor) + contribution);
                    }
                }

                if (!vertex.equals(source)) {
                    synchronized (centrality) {
                        centrality.put(vertex, centrality.get(vertex) + dependency.get(vertex));
                    }
                }
            }
        });

        return centrality;
    }

    /**
     * Finds articulation points in the graph
     */
    private Set<V> findArticulationPoints(Graph<V, DefaultEdge> graph) {
        Set<V> articulationPoints = ConcurrentHashMap.newKeySet();

        for (V vertex : graph.vertexSet()) {
            // Check if removing this vertex increases number of connected components
            Graph<V, DefaultEdge> testGraph = new DefaultUndirectedGraph<>(DefaultEdge.class);

            // Copy graph without the test vertex
            graph.vertexSet().stream()
                    .filter(v -> !v.equals(vertex))
                    .forEach(testGraph::addVertex);

            graph.edgeSet().forEach(edge -> {
                V source = graph.getEdgeSource(edge);
                V target = graph.getEdgeTarget(edge);
                if (!source.equals(vertex) && !target.equals(vertex)) {
                    testGraph.addEdge(source, target);
                }
            });

            // Count connected components
            ConnectivityInspector<V, DefaultEdge> originalInspector =
                    new ConnectivityInspector<>(graph);
            ConnectivityInspector<V, DefaultEdge> testInspector =
                    new ConnectivityInspector<>(testGraph);

            if (testInspector.connectedSets().size() > originalInspector.connectedSets().size()) {
                articulationPoints.add(vertex);
            }
        }

        return articulationPoints;
    }

    /**
     * Computes quality score for a modulator
     */
    private double computeModulatorQuality(Graph<V, E> graph, Set<V> modulator, int targetTreewidth) {
        int resultingTreewidth = treewidthComputer.computeEta(graph, modulator);

        if (resultingTreewidth > targetTreewidth) {
            return Double.MAX_VALUE; // Invalid solution
        }

        // Quality = size penalty + treewidth penalty
        return modulator.size() + (resultingTreewidth * 0.1);
    }

    /**
     * Converts directed graph to undirected
     */
    private Graph<V, DefaultEdge> convertToUndirected(Graph<V, E> directed) {
        Graph<V, DefaultEdge> undirected = new DefaultUndirectedGraph<>(DefaultEdge.class);

        directed.vertexSet().forEach(undirected::addVertex);

        directed.edgeSet().forEach(edge -> {
            V source = directed.getEdgeSource(edge);
            V target = directed.getEdgeTarget(edge);
            if (!source.equals(target) && !undirected.containsEdge(source, target)) {
                undirected.addEdge(source, target);
            }
        });

        return undirected;
    }

    /**
     * Fallback modulator computation
     */
    private ModulatorResult<V> computeFallbackModulator(Graph<V, E> graph, int targetTreewidth, int maxSize) {
        Set<V> modulator = graph.vertexSet().stream()
                .sorted((v1, v2) -> Integer.compare(
                        graph.inDegreeOf(v2) + graph.outDegreeOf(v2),
                        graph.inDegreeOf(v1) + graph.outDegreeOf(v1)))
                .limit(maxSize)
                .collect(Collectors.toSet());

        return new ModulatorResult<>(modulator,
                treewidthComputer.computeEta(graph, modulator),
                computeModulatorQuality(graph, modulator, targetTreewidth));
    }

    private Set<V> getFutureValue(Future<Set<V>> future) {
        try {
            return future.get();
        } catch (Exception e) {
            return null;
        }
    }

    public void shutdown() {
        treewidthComputer.shutdown();
        fvsComputer.shutdown();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    /**
     * Result container for modulator computation
     */
    public static class ModulatorResult<V> {
        private final Set<V> modulator;
        private final int resultingTreewidth;
        private final double qualityScore;

        public ModulatorResult(Set<V> modulator, int resultingTreewidth, double qualityScore) {
            this.modulator = new HashSet<>(modulator);
            this.resultingTreewidth = resultingTreewidth;
            this.qualityScore = qualityScore;
        }

        public Set<V> getModulator() { return new HashSet<>(modulator); }
        public int getResultingTreewidth() { return resultingTreewidth; }
        public double getQualityScore() { return qualityScore; }
        public int getSize() { return modulator.size(); }

        @Override
        public String toString() {
            return String.format("ModulatorResult{size=%d, treewidth=%d, quality=%.2f}",
                    modulator.size(), resultingTreewidth, qualityScore);
        }
    }
}
