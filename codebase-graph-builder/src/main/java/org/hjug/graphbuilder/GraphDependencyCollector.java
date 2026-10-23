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

    /**
     * Creates a dependency collector that writes into the supplied graphs.
     *
     * @param classReferencesGraph the graph receiving class dependencies
     * @param packageReferencesGraph the graph receiving cross-package dependencies
     */
    public GraphDependencyCollector(
            Graph<VertexInfo, DefaultWeightedEdge> classReferencesGraph,
            Graph<VertexInfo, DefaultWeightedEdge> packageReferencesGraph) {
        this.classReferencesGraph = classReferencesGraph;
        this.packageReferencesGraph = packageReferencesGraph;
    }

    /**
     * Adds or increments a class dependency and records its corresponding package relationship.
     * Dependencies from a class to itself are ignored.
     *
     * @param fromClassFqn the fully qualified source class name
     * @param toClassFqn the fully qualified target class name
     */
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

    /**
     * Adds or increments the package dependency implied by two class names.
     *
     * @param fromClassFqn the fully qualified source class name
     * @param toClassFqn the fully qualified target class name
     * @return the package edge, or {@code null} for equal or empty endpoint packages
     */
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
        String name = fqn;
        int lastIndex = name.lastIndexOf('.');
        // A trailing "<anonymous>"-style segment names a Kotlin anonymous class, not a package
        // member: derive the package from the enclosing class so an anonymous class never
        // manufactures a package named after its owner (dev.Owner.<anonymous> -> "dev", and
        // Owner.<anonymous> -> "" like the bare packageless literal). This keeps the package
        // graph's package names aligned with the class hierarchy's package derivation, so the
        // break-up packages flagged in the Class and Package graphs match.
        if (lastIndex >= 0 && name.substring(lastIndex + 1).startsWith("<")) {
            name = name.substring(0, lastIndex);
            if (!name.contains(".")) {
                return "";
            }
            lastIndex = name.lastIndexOf('.');
        } else if (lastIndex < 0) {
            return "";
        }
        return name.substring(0, lastIndex);
    }

    /**
     * Returns the registered {@link VertexInfo} for the given class or package
     * name, creating and registering one (with the parent namespace derived
     * from the name) when absent.
     */
    private VertexInfo getOrCreateVertex(String name) {
        return vertexRegistry.computeIfAbsent(name, VertexInfo::forName);
    }

    /**
     * Records the raw source location reported by a visitor for later inspection.
     *
     * @param classFqn the fully qualified class name
     * @param sourceFilePath the visitor-reported source location
     */
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

    /**
     * Registers a class vertex with its derived parent namespace, even if it has no dependencies.
     *
     * @param classFqn the fully qualified class name
     */
    @Override
    public void registerClassVertex(String classFqn) {
        classReferencesGraph.addVertex(getOrCreateVertex(classFqn));
    }
}
