package org.hjug.graphbuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Slf4j
public class GraphDependencyCollector implements DependencyCollector {

    @Getter
    private final Graph<VertexInfo, DefaultWeightedEdge> classReferencesGraph;

    @Getter
    private final Graph<VertexInfo, DefaultWeightedEdge> packageReferencesGraph;

    @Getter
    private final Set<String> packagesInCodebase = new HashSet<>();

    @Getter
    private final Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> classRelationshipsInPackageRelationship =
            new HashMap<>();

    /**
     * Class locations (as reported by the visitors) captured during collection,
     * keyed by class FQN. The repository-relative locations used for rendering
     * are hydrated onto the {@link VertexInfo} vertices from the canonical
     * class-to-source-file mapping after the walk (see {@link VertexInfoHydrator});
     * this map keeps the raw information captured at collection time.
     */
    @Getter
    private final Map<String, String> recordedClassLocations = new HashMap<>();

    /** Registry of the VertexInfo instances created for each vertex name. */
    private final Map<String, VertexInfo> vertexRegistry = new HashMap<>();

    public GraphDependencyCollector(
            Graph<VertexInfo, DefaultWeightedEdge> classReferencesGraph,
            Graph<VertexInfo, DefaultWeightedEdge> packageReferencesGraph) {
        this.classReferencesGraph = classReferencesGraph;
        this.packageReferencesGraph = packageReferencesGraph;
    }

    @Override
    public void addClassDependency(String fromClassFqn, String toClassFqn) {
        if (fromClassFqn.equals(toClassFqn)) {
            return;
        }

        VertexInfo fromVertex = getOrCreateVertex(fromClassFqn);
        VertexInfo toVertex = getOrCreateVertex(toClassFqn);
        classReferencesGraph.addVertex(fromVertex);
        classReferencesGraph.addVertex(toVertex);

        DefaultWeightedEdge classRelationship;
        if (!classReferencesGraph.containsEdge(fromVertex, toVertex)) {
            classRelationship = classReferencesGraph.addEdge(fromVertex, toVertex);
        } else {
            classRelationship = classReferencesGraph.getEdge(fromVertex, toVertex);
            classReferencesGraph.setEdgeWeight(
                    classRelationship, classReferencesGraph.getEdgeWeight(classRelationship) + 1);
        }

        DefaultWeightedEdge packageEdge = addPackageDependency(fromClassFqn, toClassFqn);

        if (packageEdge != null) {
            if (!classRelationshipsInPackageRelationship.containsKey(packageEdge)) {
                classRelationshipsInPackageRelationship.put(packageEdge, new HashSet<>());
            }

            Set<DefaultWeightedEdge> packageRelationship = classRelationshipsInPackageRelationship.get(packageEdge);
            packageRelationship.remove(classRelationship);
            packageRelationship.add(classRelationship);
        }
    }

    @Override
    public DefaultWeightedEdge addPackageDependency(String fromClassFqn, String toClassFqn) {
        String fromPackageName = getPackageFromFqn(fromClassFqn);
        String toPackageName = getPackageFromFqn(toClassFqn);

        // An empty package (e.g. the packageless Kotlin "<anonymous>" FQN, which has no '.') would
        // otherwise pollute the package graph with an "" vertex. Treat a degenerate (empty) package
        // on either end as a no-op — anonymous/synthetic classes are still first-class class-graph
        // members, but they cannot meaningfully contribute to the package graph.
        if (fromPackageName.isEmpty() || toPackageName.isEmpty()) {
            return null;
        }

        if (fromPackageName.equals(toPackageName)) {
            return null;
        }

        VertexInfo fromPackageVertex = getOrCreateVertex(fromPackageName);
        VertexInfo toPackageVertex = getOrCreateVertex(toPackageName);
        packageReferencesGraph.addVertex(fromPackageVertex);
        packageReferencesGraph.addVertex(toPackageVertex);

        DefaultWeightedEdge packageEdge;
        if (!packageReferencesGraph.containsEdge(fromPackageVertex, toPackageVertex)) {
            packageEdge = packageReferencesGraph.addEdge(fromPackageVertex, toPackageVertex);
        } else {
            packageEdge = packageReferencesGraph.getEdge(fromPackageVertex, toPackageVertex);
            packageReferencesGraph.setEdgeWeight(packageEdge, packageReferencesGraph.getEdgeWeight(packageEdge) + 1);
        }

        return packageEdge;
    }

    protected String getPackageFromFqn(String fqn) {
        if (!fqn.contains(".")) {
            return "";
        }
        int lastIndex = fqn.lastIndexOf(".");
        return fqn.substring(0, lastIndex);
    }

    /**
     * Returns the registered {@link VertexInfo} for the given class or package
     * name, creating and registering one (with the parent namespace derived
     * from the name) when absent.
     */
    private VertexInfo getOrCreateVertex(String name) {
        return vertexRegistry.computeIfAbsent(name, VertexInfo::forName);
    }

    @Override
    public void recordClassLocation(String classFqn, String sourceFilePath) {
        // Capture the location reported during collection; repository-relative
        // hydration of the VertexInfo vertices happens after the walk.
        recordedClassLocations.put(classFqn, sourceFilePath);
    }

    @Override
    public void registerPackage(String packageName) {
        packagesInCodebase.add(packageName);
    }

    @Override
    public void registerClassVertex(String classFqn) {
        classReferencesGraph.addVertex(getOrCreateVertex(classFqn));
    }
}
