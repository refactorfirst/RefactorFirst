package org.hjug.feedback.arc.pageRank;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Custom LineDigraph implementation that doesn't extend DefaultDirectedGraph.
 * Represents a directed graph where vertices are LineVertex objects representing
 * edges from the original graph, and edges represent adjacency relationships.
 */
class LineDigraph<V, E> {

    // Internal storage for vertices and adjacency relationships
    private final Set<LineVertex<V, E>> vertices;
    private final Map<LineVertex<V, E>, Set<LineVertex<V, E>>> adjacencyMap;
    private final Map<LineVertex<V, E>, Set<LineVertex<V, E>>> incomingMap;

    /**
     * Constructor for LineDigraph
     */
    public LineDigraph() {
        this.vertices = ConcurrentHashMap.newKeySet();
        this.adjacencyMap = new ConcurrentHashMap<>();
        this.incomingMap = new ConcurrentHashMap<>();
    }

    /**
     * Add a vertex to the line digraph
     * @param vertex The LineVertex to add
     * @return true if the vertex was added, false if it already existed
     */
    public boolean addVertex(LineVertex<V, E> vertex) {
        if (vertices.add(vertex)) {
            adjacencyMap.putIfAbsent(vertex, ConcurrentHashMap.newKeySet());
            incomingMap.putIfAbsent(vertex, ConcurrentHashMap.newKeySet());
            return true;
        }
        return false;
    }

    /**
     * Remove a vertex from the line digraph
     * @param vertex The LineVertex to remove
     * @return true if the vertex was removed, false if it didn't exist
     */
    public boolean removeVertex(LineVertex<V, E> vertex) {
        if (vertices.remove(vertex)) {
            // Remove all outgoing edges
            Set<LineVertex<V, E>> outgoing = adjacencyMap.remove(vertex);
            if (outgoing != null) {
                outgoing.forEach(target -> incomingMap.get(target).remove(vertex));
            }

            // Remove all incoming edges
            Set<LineVertex<V, E>> incoming = incomingMap.remove(vertex);
            if (incoming != null) {
                incoming.forEach(source -> adjacencyMap.get(source).remove(vertex));
            }

            return true;
        }
        return false;
    }

    /**
     * Add an edge between two vertices in the line digraph
     * @param source The source LineVertex
     * @param target The target LineVertex
     * @return true if the edge was added, false if it already existed
     */
    public boolean addEdge(LineVertex<V, E> source, LineVertex<V, E> target) {
        // Ensure both vertices exist
        addVertex(source);
        addVertex(target);

        // Add edge if it doesn't exist
        if (adjacencyMap.get(source).add(target)) {
            incomingMap.get(target).add(source);
            return true;
        }
        return false;
    }

    /**
     * Remove an edge between two vertices
     * @param source The source LineVertex
     * @param target The target LineVertex
     * @return true if the edge was removed, false if it didn't exist
     */
    public boolean removeEdge(LineVertex<V, E> source, LineVertex<V, E> target) {
        if (containsVertex(source) && containsVertex(target)) {
            if (adjacencyMap.get(source).remove(target)) {
                incomingMap.get(target).remove(source);
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the digraph contains a specific vertex
     * @param vertex The LineVertex to check
     * @return true if the vertex exists, false otherwise
     */
    public boolean containsVertex(LineVertex<V, E> vertex) {
        return vertices.contains(vertex);
    }

    /**
     * Check if there's an edge between two vertices
     * @param source The source LineVertex
     * @param target The target LineVertex
     * @return true if the edge exists, false otherwise
     */
    public boolean containsEdge(LineVertex<V, E> source, LineVertex<V, E> target) {
        return containsVertex(source) && adjacencyMap.get(source).contains(target);
    }

    /**
     * Get all vertices in the line digraph
     * @return Set of all LineVertex objects
     */
    public Set<LineVertex<V, E>> vertexSet() {
        return new HashSet<>(vertices);
    }

    /**
     * Get the number of vertices
     * @return Number of vertices in the digraph
     */
    public int vertexCount() {
        return vertices.size();
    }

    /**
     * Get the number of edges
     * @return Total number of edges in the digraph
     */
    public int edgeCount() {
        return adjacencyMap.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Get all outgoing neighbors of a vertex
     * @param vertex The source LineVertex
     * @return Set of target LineVertex objects
     */
    public Set<LineVertex<V, E>> getOutgoingNeighbors(LineVertex<V, E> vertex) {
        return adjacencyMap.getOrDefault(vertex, Collections.emptySet()).stream()
                .collect(Collectors.toSet());
    }

    /**
     * Get all incoming neighbors of a vertex
     * @param vertex The target LineVertex
     * @return Set of source LineVertex objects
     */
    public Set<LineVertex<V, E>> getIncomingNeighbors(LineVertex<V, E> vertex) {
        return incomingMap.getOrDefault(vertex, Collections.emptySet()).stream().collect(Collectors.toSet());
    }

    /**
     * Get all neighbors (both incoming and outgoing) of a vertex
     * @param vertex The LineVertex
     * @return Set of all neighboring LineVertex objects
     */
    public Set<LineVertex<V, E>> getAllNeighbors(LineVertex<V, E> vertex) {
        Set<LineVertex<V, E>> neighbors = new HashSet<>();
        neighbors.addAll(getOutgoingNeighbors(vertex));
        neighbors.addAll(getIncomingNeighbors(vertex));
        return neighbors;
    }

    /**
     * Get the out-degree of a vertex
     * @param vertex The LineVertex
     * @return Number of outgoing edges
     */
    public int getOutDegree(LineVertex<V, E> vertex) {
        return adjacencyMap.getOrDefault(vertex, Collections.emptySet()).size();
    }

    /**
     * Get the in-degree of a vertex
     * @param vertex The LineVertex
     * @return Number of incoming edges
     */
    public int getInDegree(LineVertex<V, E> vertex) {
        return incomingMap.getOrDefault(vertex, Collections.emptySet()).size();
    }

    /**
     * Get the total degree (in + out) of a vertex
     * @param vertex The LineVertex
     * @return Total degree of the vertex
     */
    public int getTotalDegree(LineVertex<V, E> vertex) {
        return getInDegree(vertex) + getOutDegree(vertex);
    }

    /**
     * Check if the digraph is empty
     * @return true if no vertices exist, false otherwise
     */
    public boolean isEmpty() {
        return vertices.isEmpty();
    }

    /**
     * Clear all vertices and edges from the digraph
     */
    public void clear() {
        vertices.clear();
        adjacencyMap.clear();
        incomingMap.clear();
    }

    /**
     * Get all vertices with no incoming edges (sources)
     * @return Set of source LineVertex objects
     */
    public Set<LineVertex<V, E>> getSources() {
        return vertices.stream().filter(vertex -> getInDegree(vertex) == 0).collect(Collectors.toSet());
    }

    /**
     * Get all vertices with no outgoing edges (sinks)
     * @return Set of sink LineVertex objects
     */
    public Set<LineVertex<V, E>> getSinks() {
        return vertices.stream().filter(vertex -> getOutDegree(vertex) == 0).collect(Collectors.toSet());
    }

    /**
     * Get vertices reachable from a given vertex (BFS traversal)
     * @param startVertex The starting LineVertex
     * @return Set of reachable LineVertex objects
     */
    public Set<LineVertex<V, E>> getReachableVertices(LineVertex<V, E> startVertex) {
        Set<LineVertex<V, E>> reachable = new HashSet<>();
        Queue<LineVertex<V, E>> queue = new LinkedList<>();

        if (containsVertex(startVertex)) {
            queue.offer(startVertex);
            reachable.add(startVertex);

            while (!queue.isEmpty()) {
                LineVertex<V, E> current = queue.poll();
                for (LineVertex<V, E> neighbor : getOutgoingNeighbors(current)) {
                    if (reachable.add(neighbor)) {
                        queue.offer(neighbor);
                    }
                }
            }
        }

        return reachable;
    }

    /**
     * Check if there's a path from source to target
     * @param source The source LineVertex
     * @param target The target LineVertex
     * @return true if a path exists, false otherwise
     */
    public boolean hasPath(LineVertex<V, E> source, LineVertex<V, E> target) {
        if (!containsVertex(source) || !containsVertex(target)) {
            return false;
        }

        if (source.equals(target)) {
            return true;
        }

        return getReachableVertices(source).contains(target);
    }

    /**
     * Perform a topological sort of the digraph (if acyclic)
     * @return List of vertices in topological order, or empty list if cyclic
     */
    public List<LineVertex<V, E>> topologicalSort() {
        List<LineVertex<V, E>> result = new ArrayList<>();
        Map<LineVertex<V, E>, Integer> inDegreeMap = new HashMap<>();
        Queue<LineVertex<V, E>> queue = new LinkedList<>();

        // Initialize in-degree map
        for (LineVertex<V, E> vertex : vertices) {
            inDegreeMap.put(vertex, getInDegree(vertex));
            if (getInDegree(vertex) == 0) {
                queue.offer(vertex);
            }
        }

        // Process vertices with zero in-degree
        while (!queue.isEmpty()) {
            LineVertex<V, E> current = queue.poll();
            result.add(current);

            for (LineVertex<V, E> neighbor : getOutgoingNeighbors(current)) {
                int newInDegree = inDegreeMap.get(neighbor) - 1;
                inDegreeMap.put(neighbor, newInDegree);

                if (newInDegree == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        // Return empty list if graph has cycles
        return result.size() == vertices.size() ? result : Collections.emptyList();
    }

    /**
     * Create a copy of this line digraph
     * @return A new LineDigraph with the same structure
     */
    public LineDigraph<V, E> copy() {
        LineDigraph<V, E> copy = new LineDigraph<>();

        // Add all vertices
        vertices.forEach(copy::addVertex);

        // Add all edges
        for (LineVertex<V, E> source : vertices) {
            for (LineVertex<V, E> target : getOutgoingNeighbors(source)) {
                copy.addEdge(source, target);
            }
        }

        return copy;
    }

    /**
     * Get statistics about the line digraph
     * @return Map containing various statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("vertexCount", vertexCount());
        stats.put("edgeCount", edgeCount());
        stats.put("sourceCount", getSources().size());
        stats.put("sinkCount", getSinks().size());
        stats.put("isEmpty", isEmpty());

        if (!isEmpty()) {
            double avgOutDegree =
                    vertices.stream().mapToInt(this::getOutDegree).average().orElse(0.0);

            double avgInDegree =
                    vertices.stream().mapToInt(this::getInDegree).average().orElse(0.0);

            stats.put("avgOutDegree", avgOutDegree);
            stats.put("avgInDegree", avgInDegree);
            stats.put("density", (double) edgeCount() / (vertexCount() * (vertexCount() - 1)));
        }

        return stats;
    }

    /**
     * Convert to string representation for debugging
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LineDigraph{");
        sb.append("vertices=").append(vertices.size());
        sb.append(", edges=").append(edgeCount());
        sb.append("}");
        return sb.toString();
    }

    /**
     * Get detailed string representation with all edges
     * @return Detailed string representation
     */
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LineDigraph Details:\n");
        sb.append("Vertices: ").append(vertices.size()).append("\n");
        sb.append("Edges: ").append(edgeCount()).append("\n\n");

        for (LineVertex<V, E> vertex : vertices) {
            sb.append(vertex).append(" -> ");
            Set<LineVertex<V, E>> outgoing = getOutgoingNeighbors(vertex);
            if (outgoing.isEmpty()) {
                sb.append("[]");
            } else {
                sb.append(outgoing);
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Validate the internal consistency of the digraph
     * @return true if consistent, false otherwise
     */
    public boolean validateConsistency() {
        // Check that every outgoing edge has a corresponding incoming edge
        for (LineVertex<V, E> source : vertices) {
            for (LineVertex<V, E> target : getOutgoingNeighbors(source)) {
                if (!getIncomingNeighbors(target).contains(source)) {
                    return false;
                }
            }
        }

        // Check that every incoming edge has a corresponding outgoing edge
        for (LineVertex<V, E> target : vertices) {
            for (LineVertex<V, E> source : getIncomingNeighbors(target)) {
                if (!getOutgoingNeighbors(source).contains(target)) {
                    return false;
                }
            }
        }

        return true;
    }
}
