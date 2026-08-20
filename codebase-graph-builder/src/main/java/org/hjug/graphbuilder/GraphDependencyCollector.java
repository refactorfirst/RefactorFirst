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
    private final Graph<String, DefaultWeightedEdge> classReferencesGraph;

    @Getter
    private final Graph<String, DefaultWeightedEdge> packageReferencesGraph;

    @Getter
    private final Set<String> packagesInCodebase = new HashSet<>();

    @Getter
    private final Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> classRelationshipsInPackageRelationship =
            new HashMap<>();

    public GraphDependencyCollector(
            Graph<String, DefaultWeightedEdge> classReferencesGraph,
            Graph<String, DefaultWeightedEdge> packageReferencesGraph) {
        this.classReferencesGraph = classReferencesGraph;
        this.packageReferencesGraph = packageReferencesGraph;
    }

    @Override
    public void addClassDependency(String fromClassFqn, String toClassFqn) {
        if (fromClassFqn.equals(toClassFqn)) {
            return;
        }

        classReferencesGraph.addVertex(fromClassFqn);
        classReferencesGraph.addVertex(toClassFqn);

        DefaultWeightedEdge classRelationship;
        if (!classReferencesGraph.containsEdge(fromClassFqn, toClassFqn)) {
            classRelationship = classReferencesGraph.addEdge(fromClassFqn, toClassFqn);
        } else {
            classRelationship = classReferencesGraph.getEdge(fromClassFqn, toClassFqn);
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

        packageReferencesGraph.addVertex(fromPackageName);
        packageReferencesGraph.addVertex(toPackageName);

        DefaultWeightedEdge packageEdge;
        if (!packageReferencesGraph.containsEdge(fromPackageName, toPackageName)) {
            packageEdge = packageReferencesGraph.addEdge(fromPackageName, toPackageName);
        } else {
            packageEdge = packageReferencesGraph.getEdge(fromPackageName, toPackageName);
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

    @Override
    public void recordClassLocation(String classFqn, String sourceFilePath) {
        // This will be handled by JavaVisitor which maintains the mapping
    }

    @Override
    public void registerPackage(String packageName) {
        packagesInCodebase.add(packageName);
    }

    @Override
    public void registerClassVertex(String classFqn) {
        classReferencesGraph.addVertex(classFqn);
    }
}
