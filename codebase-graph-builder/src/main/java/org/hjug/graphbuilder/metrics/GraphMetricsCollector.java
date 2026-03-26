package org.hjug.graphbuilder.metrics;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Getter
public class GraphMetricsCollector implements MetricsCollector {

    private final Graph<String, DefaultWeightedEdge> classGraph;
    private final Graph<String, DefaultWeightedEdge> packageGraph;
    private final Map<String, ClassMetrics> classMetrics = new HashMap<>();
    private final Map<String, String> classToSourceFileMapping = new HashMap<>();

    public GraphMetricsCollector(
            Graph<String, DefaultWeightedEdge> classGraph, Graph<String, DefaultWeightedEdge> packageGraph) {
        this.classGraph = classGraph;
        this.packageGraph = packageGraph;
    }

    @Override
    public void addClassDependency(String fromClass, String toClass) {
        if (!classGraph.containsVertex(fromClass)) {
            classGraph.addVertex(fromClass);
        }
        if (!classGraph.containsVertex(toClass)) {
            classGraph.addVertex(toClass);
        }

        DefaultWeightedEdge edge = classGraph.getEdge(fromClass, toClass);
        if (edge == null) {
            edge = classGraph.addEdge(fromClass, toClass);
            if (edge != null) {
                classGraph.setEdgeWeight(edge, 1.0);
            }
        } else {
            double weight = classGraph.getEdgeWeight(edge);
            classGraph.setEdgeWeight(edge, weight + 1.0);
        }

        getOrCreateClassMetrics(fromClass).addDependency(toClass);
    }

    @Override
    public void addPackageDependency(String fromPackage, String toPackage) {
        if (!packageGraph.containsVertex(fromPackage)) {
            packageGraph.addVertex(fromPackage);
        }
        if (!packageGraph.containsVertex(toPackage)) {
            packageGraph.addVertex(toPackage);
        }

        DefaultWeightedEdge edge = packageGraph.getEdge(fromPackage, toPackage);
        if (edge == null) {
            edge = packageGraph.addEdge(fromPackage, toPackage);
            if (edge != null) {
                packageGraph.setEdgeWeight(edge, 1.0);
            }
        } else {
            double weight = packageGraph.getEdgeWeight(edge);
            packageGraph.setEdgeWeight(edge, weight + 1.0);
        }
    }

    @Override
    public void recordClassLocation(String classFqn, String sourceFilePath) {
        classToSourceFileMapping.put(classFqn, sourceFilePath);
    }

    @Override
    public void registerPackage(String packageName) {
        if (!packageGraph.containsVertex(packageName)) {
            packageGraph.addVertex(packageName);
        }
    }

    public Set<String> getPackagesInCodebase() {
        return packageGraph.vertexSet();
    }

    @Override
    public void recordClassMetric(String className, String metricName, Object value) {
        ClassMetrics metrics = getOrCreateClassMetrics(className);
        switch (metricName) {
            case "LOC":
                metrics.setLinesOfCode((Integer) value);
                break;
            case "NOA":
                metrics.setNumberOfAttributes((Integer) value);
                break;
            case "ATFD":
                metrics.setAccessToForeignData((Integer) value);
                break;
            case "TCC":
                metrics.setTightClassCohesion((Double) value);
                break;
            default:
                break;
        }
    }

    @Override
    public void recordMethodMetric(String className, String methodSignature, String metricName, Object value) {
        ClassMetrics classMetrics = getOrCreateClassMetrics(className);
        MethodMetrics methodMetrics = classMetrics.getMethods().get(methodSignature);

        if (methodMetrics == null) {
            methodMetrics = new MethodMetrics(null, methodSignature);
            classMetrics.getMethods().put(methodSignature, methodMetrics);
        }

        switch (metricName) {
            case "LOC":
                methodMetrics.setLinesOfCode((Integer) value);
                break;
            case "CYCLO":
                methodMetrics.setCyclomaticComplexity((Integer) value);
                break;
            case "MAXNESTING":
                methodMetrics.setMaxNestingDepth((Integer) value);
                break;
            case "NOP":
                methodMetrics.setNumberOfParameters((Integer) value);
                break;
            default:
                break;
        }
    }

    @Override
    public ClassMetrics getClassMetrics(String className) {
        return classMetrics.get(className);
    }

    @Override
    public Map<String, ClassMetrics> getAllClassMetrics() {
        return classMetrics;
    }

    @Override
    public void finalizeMetrics() {
        for (ClassMetrics metrics : classMetrics.values()) {
            metrics.calculateAccessToForeignData();
            metrics.calculateTightClassCohesion();
        }
    }

    private ClassMetrics getOrCreateClassMetrics(String className) {
        return classMetrics.computeIfAbsent(className, ClassMetrics::new);
    }
}
