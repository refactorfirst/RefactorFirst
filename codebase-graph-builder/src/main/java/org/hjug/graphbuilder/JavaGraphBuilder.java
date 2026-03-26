package org.hjug.graphbuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.metrics.ClassMetrics;
import org.hjug.graphbuilder.metrics.DisharmonyDetector;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.ClassDisharmony;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.MethodDisharmony;
import org.hjug.graphbuilder.metrics.GraphMetricsCollector;
import org.hjug.graphbuilder.metrics.MetricsCollectingVisitor;
import org.hjug.graphbuilder.visitor.JavaMethodDeclarationVisitor;
import org.hjug.graphbuilder.visitor.JavaVariableTypeVisitor;
import org.hjug.graphbuilder.visitor.JavaVisitor;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

@Slf4j
public class JavaGraphBuilder {

    /**
     * Given a java source directory, return a CodebaseGraphDTO using default configuration
     *
     * @param srcDirectory The source directory to analyze
     * @param excludeTests Whether to exclude test files
     * @param testSourceDirectory The test source directory pattern to exclude
     * @return CodebaseGraphDTO
     * @throws IOException
     */
    public CodebaseGraphDTO getCodebaseGraphDTO(String srcDirectory, boolean excludeTests, String testSourceDirectory)
            throws IOException {
        GraphBuilderConfig config = GraphBuilderConfig.builder()
                .excludeTests(excludeTests)
                .testSourceDirectory(testSourceDirectory)
                .build();
        return getCodebaseGraphDTO(srcDirectory, config);
    }

    /**
     * Given a java source directory and configuration, return a CodebaseGraphDTO
     *
     * @param srcDirectory The source directory to analyze
     * @param config The configuration for the graph builder
     * @return CodebaseGraphDTO
     * @throws IOException
     */
    public CodebaseGraphDTO getCodebaseGraphDTO(String srcDirectory, GraphBuilderConfig config) throws IOException {
        if (srcDirectory == null || srcDirectory.isEmpty()) {
            throw new IllegalArgumentException("Source directory cannot be null or empty");
        }
        return processWithOpenRewrite(srcDirectory, config);
    }

    private CodebaseGraphDTO processWithOpenRewrite(String srcDir, GraphBuilderConfig config) throws IOException {
        File srcDirectory = new File(srcDir);

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        final Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        final Graph<String, DefaultWeightedEdge> packageReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        final GraphDependencyCollector dependencyCollector =
                new GraphDependencyCollector(classReferencesGraph, packageReferencesGraph);

        final JavaVisitor<ExecutionContext> javaVisitor = new JavaVisitor<>(dependencyCollector);
        final JavaVariableTypeVisitor<ExecutionContext> javaVariableTypeVisitor =
                new JavaVariableTypeVisitor<>(dependencyCollector);
        final JavaMethodDeclarationVisitor<ExecutionContext> javaMethodDeclarationVisitor =
                new JavaMethodDeclarationVisitor<>(dependencyCollector);

        GraphMetricsCollector metricsCollector =
                new GraphMetricsCollector(classReferencesGraph, packageReferencesGraph);
        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        try (Stream<Path> pathStream = Files.walk(Paths.get(srcDirectory.getAbsolutePath()))) {
            List<Path> list;
            if (config.isExcludeTests()) {
                list = pathStream
                        .filter(file -> !file.toString().contains(config.getTestSourceDirectory()))
                        .collect(Collectors.toList());
            } else {
                list = pathStream.collect(Collectors.toList());
            }

            javaParser
                    .parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx)
                    .forEach(cu -> {
                        javaVisitor.visit(cu, ctx);
                        javaVariableTypeVisitor.visit(cu, ctx);
                        javaMethodDeclarationVisitor.visit(cu, ctx);
                        metricsVisitor.visit(cu, ctx);
                    });
        }

        removeClassesNotInCodebase(dependencyCollector.getPackagesInCodebase(), classReferencesGraph);

        metricsCollector.finalizeMetrics();
        DisharmonyDetector detector = new DisharmonyDetector();
        Collection<ClassMetrics> metrics = metricsCollector.getAllClassMetrics().values();

        return new CodebaseGraphDTO(
                classReferencesGraph,
                packageReferencesGraph,
                javaVisitor.getClassToSourceFilePathMapping(),
                getClassDisharmonies(detector, metrics),
                getMethodDisharmonies(detector, metrics));
    }

    private static List<MethodDisharmony> getMethodDisharmonies(
            DisharmonyDetector detector, Collection<ClassMetrics> metrics) {
        List<MethodDisharmony> methodDisharmonies = new ArrayList<>();
        methodDisharmonies.addAll(detector.detectBrainMethods(List.copyOf(metrics)));
        methodDisharmonies.addAll(detector.detectIntensiveCoupling(List.copyOf(metrics)));
        methodDisharmonies.addAll(detector.detectDispersedCoupling(List.copyOf(metrics)));
        return methodDisharmonies;
    }

    private static List<ClassDisharmony> getClassDisharmonies(
            DisharmonyDetector detector, Collection<ClassMetrics> metrics) {
        List<ClassDisharmony> classDisharmonies = new ArrayList<>();
        classDisharmonies.addAll(detector.detectGodClasses(List.copyOf(metrics)));
        classDisharmonies.addAll(detector.detectDataClasses(List.copyOf(metrics)));
        classDisharmonies.addAll(detector.detectBrainClasses(List.copyOf(metrics)));
        classDisharmonies.addAll(detector.detectFeatureEnvy(List.copyOf(metrics)));
        classDisharmonies.addAll(detector.detectShotgunSurgery(List.copyOf(metrics)));
        classDisharmonies.addAll(detector.detectRefusedParentBequest(List.copyOf(metrics)));
        classDisharmonies.addAll(detector.detectTraditionBreaker(List.copyOf(metrics)));
        return classDisharmonies;
    }

    // remove node if package not in codebase
    void removeClassesNotInCodebase(
            Set<String> packagesInCodebase, Graph<String, DefaultWeightedEdge> classReferencesGraph) {

        // collect nodes to remove
        Set<String> classesToRemove = new HashSet<>();
        for (String classFqn : classReferencesGraph.vertexSet()) {
            if (!packagesInCodebase.contains(getPackage(classFqn))) {
                classesToRemove.add(classFqn);
            }
        }

        classReferencesGraph.removeAllVertices(classesToRemove);
    }

    String getPackage(String fqn) {
        // handle no package
        if (!fqn.contains(".")) {
            return "";
        }

        int lastIndex = fqn.lastIndexOf(".");
        return fqn.substring(0, lastIndex);
    }
}
