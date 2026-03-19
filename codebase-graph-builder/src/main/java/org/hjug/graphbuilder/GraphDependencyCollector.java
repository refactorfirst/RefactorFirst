package org.hjug.graphbuilder;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

public class GraphDependencyCollector implements DependencyCollector {

    @Getter
    private final Graph<String, DefaultWeightedEdge> classReferencesGraph;

    @Getter
    private final Graph<String, DefaultWeightedEdge> packageReferencesGraph;

    @Getter
    private final Set<String> packagesInCodebase = new HashSet<>();

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

        if (!classReferencesGraph.containsEdge(fromClassFqn, toClassFqn)) {
            classReferencesGraph.addEdge(fromClassFqn, toClassFqn);
        } else {
            DefaultWeightedEdge edge = classReferencesGraph.getEdge(fromClassFqn, toClassFqn);
            classReferencesGraph.setEdgeWeight(edge, classReferencesGraph.getEdgeWeight(edge) + 1);
        }
    }

    @Override
    public void addPackageDependency(String fromPackageName, String toPackageName) {
        if (fromPackageName.equals(toPackageName)) {
            return;
        }

        packageReferencesGraph.addVertex(fromPackageName);
        packageReferencesGraph.addVertex(toPackageName);

        if (!packageReferencesGraph.containsEdge(fromPackageName, toPackageName)) {
            packageReferencesGraph.addEdge(fromPackageName, toPackageName);
        } else {
            DefaultWeightedEdge edge = packageReferencesGraph.getEdge(fromPackageName, toPackageName);
            packageReferencesGraph.setEdgeWeight(edge, packageReferencesGraph.getEdgeWeight(edge) + 1);
        }
    }

    @Override
    public void recordClassLocation(String classFqn, String sourceFilePath) {
        // This will be handled by JavaVisitor which maintains the mapping
    }

    @Override
    public void registerPackage(String packageName) {
        packagesInCodebase.add(packageName);
    }
}
