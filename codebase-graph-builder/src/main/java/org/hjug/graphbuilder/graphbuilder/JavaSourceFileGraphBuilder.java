package org.hjug.graphbuilder.graphbuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.GraphBuilderConfig;
import org.hjug.graphbuilder.GraphDependencyCollector;
import org.hjug.graphbuilder.metrics.ClassMetrics;
import org.hjug.graphbuilder.metrics.DisharmonyDetector;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.ClassDisharmony;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.MethodDisharmony;
import org.hjug.graphbuilder.metrics.GraphMetricsCollector;
import org.hjug.graphbuilder.metrics.MetricsCollectingVisitor;
import org.hjug.graphbuilder.visitor.JavaVisitor;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;

/**
 * Java-language source-file graph builder. Orchestrated by
 * {@link org.hjug.graphbuilder.CompositeGraphBuilder}, which always runs
 * this builder alongside {@link KotlinSourceFileGraphBuilder}.
 */
@Slf4j
public class JavaSourceFileGraphBuilder implements SourceFileGraphBuilder {

    @Override
    public CodebaseGraphDTO buildGraph(String repositoryPath, String repositoryRoot, GraphBuilderConfig config)
            throws IOException {
        File srcDirectory = new File(repositoryPath);

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(e -> log.warn("OpenRewrite parse/visit error", e));

        final Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        final Graph<String, DefaultWeightedEdge> packageReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        final GraphDependencyCollector dependencyCollector =
                new GraphDependencyCollector(classReferencesGraph, packageReferencesGraph);

        final JavaVisitor<ExecutionContext> javaVisitor =
                new JavaVisitor<>(repositoryPath, repositoryRoot, dependencyCollector);

        GraphMetricsCollector metricsCollector =
                new GraphMetricsCollector(classReferencesGraph, packageReferencesGraph);
        MetricsCollectingVisitor metricsVisitor = new MetricsCollectingVisitor(metricsCollector);

        try (Stream<Path> pathStream = Files.walk(Path.of(srcDirectory.getAbsolutePath()))) {
            Stream<Path> filteredStream =
                    pathStream.filter(file -> file.toString().endsWith(".java"));
            if (config.isExcludeTests()
                    && config.getTestSourceDirectory() != null
                    && !config.getTestSourceDirectory().isEmpty()) {
                filteredStream =
                        filteredStream.filter(file -> !file.toString().contains(config.getTestSourceDirectory()));
            }
            List<Path> list = filteredStream.collect(Collectors.toList());
            log.info("JavaSourceFileGraphBuilder: walking {} Java files under {}", list.size(), repositoryPath);

            Path sourceRoot = Path.of(srcDirectory.getAbsolutePath());
            javaParser.parse(list, sourceRoot, ctx).forEach(cu -> {
                // Ensure source path is absolute for correct URI generation
                // Resolve relative source path against source root, not current working directory
                Path absoluteSourcePath = sourceRoot.resolve(cu.getSourcePath()).normalize();
                SourceFile cuWithAbsPath = cu.withSourcePath(absoluteSourcePath);
                javaVisitor.visit(cuWithAbsPath, ctx);
                metricsVisitor.visit(cuWithAbsPath, ctx);
            });
        }

        Map<String, String> classToSourceFilePathMapping = javaVisitor.getClassToSourceFilePathMapping();
        return finalizeDto(
                classReferencesGraph,
                packageReferencesGraph,
                dependencyCollector,
                classToSourceFilePathMapping,
                metricsCollector);
    }

    static CodebaseGraphDTO finalizeDto(
            Graph<String, DefaultWeightedEdge> classReferencesGraph,
            Graph<String, DefaultWeightedEdge> packageReferencesGraph,
            GraphDependencyCollector dependencyCollector,
            Map<String, String> classToSourceFilePathMapping,
            GraphMetricsCollector metricsCollector) {

        removeClassesNotInCodebase(dependencyCollector.getPackagesInCodebase(), classReferencesGraph);
        removePackagesNotInCodebase(dependencyCollector.getPackagesInCodebase(), packageReferencesGraph);

        dependencyCollector
                .getClassRelationshipsInPackageRelationship()
                .keySet()
                .retainAll(packageReferencesGraph.edgeSet());

        metricsCollector.finalizeMetrics();
        DisharmonyDetector detector = new DisharmonyDetector();
        Collection<ClassMetrics> metrics = metricsCollector.getAllClassMetrics().values();
        // Gate the Kotlin-specific detectors on the presence of any Kotlin
        // metric signal so Java-only builds skip three detector invocations
        // (including an O(N²) sealed-hierarchy scan) that could never flag a
        // Java class. See GraphMetricsCollector.hasKotlinMetrics().
        boolean hasKotlinMetrics = metricsCollector.hasKotlinMetrics();

        return new CodebaseGraphDTO(
                classReferencesGraph,
                packageReferencesGraph,
                dependencyCollector.getClassRelationshipsInPackageRelationship(),
                classToSourceFilePathMapping,
                getClassDisharmonies(detector, metrics, hasKotlinMetrics),
                getMethodDisharmonies(detector, metrics));
    }

    static void removeClassesNotInCodebase(
            Set<String> packagesInCodebase, Graph<String, DefaultWeightedEdge> classReferencesGraph) {
        Set<String> classesToRemove = new HashSet<>();
        for (String classFqn : classReferencesGraph.vertexSet()) {
            if (!packagesInCodebase.contains(getPackage(classFqn))) {
                classesToRemove.add(classFqn);
            }
        }
        classReferencesGraph.removeAllVertices(classesToRemove);
    }

    static void removePackagesNotInCodebase(
            Set<String> packagesInCodebase, Graph<String, DefaultWeightedEdge> packageReferencesGraph) {
        Set<String> packagesToRemove = new HashSet<>();
        for (String aPackage : packageReferencesGraph.vertexSet()) {
            if (!packagesInCodebase.contains(aPackage)) {
                packagesToRemove.add(aPackage);
            }
        }
        packageReferencesGraph.removeAllVertices(packagesToRemove);
    }

    static String getPackage(String fqn) {
        if (!fqn.contains(".")) {
            return "";
        }
        int lastIndex = fqn.lastIndexOf(".");
        return fqn.substring(0, lastIndex);
    }

    private static List<MethodDisharmony> getMethodDisharmonies(
            DisharmonyDetector detector, Collection<ClassMetrics> metrics) {
        List<MethodDisharmony> methodDisharmonies = new ArrayList<>();
        methodDisharmonies.addAll(detector.detectBrainMethods(List.copyOf(metrics)));
        methodDisharmonies.addAll(detector.detectFeatureEnvy(List.copyOf(metrics)));
        methodDisharmonies.addAll(detector.detectIntensiveCoupling(List.copyOf(metrics)));
        methodDisharmonies.addAll(detector.detectDispersedCoupling(List.copyOf(metrics)));
        methodDisharmonies.addAll(detector.detectShotgunSurgery(List.copyOf(metrics)));
        return methodDisharmonies;
    }

    static List<ClassDisharmony> getClassDisharmonies(
            DisharmonyDetector detector, Collection<ClassMetrics> metrics, boolean hasKotlinMetrics) {
        List<ClassDisharmony> classDisharmonies = new ArrayList<>();
        classDisharmonies.addAll(detector.detectGodClasses(List.copyOf(metrics)));
        classDisharmonies.addAll(detector.detectDataClasses(List.copyOf(metrics)));
        classDisharmonies.addAll(detector.detectBrainClasses(List.copyOf(metrics)));
        classDisharmonies.addAll(detector.detectRefusedParentBequest(List.copyOf(metrics)));
        classDisharmonies.addAll(detector.detectTraditionBreaker(List.copyOf(metrics)));
        classDisharmonies.addAll(detector.detectSignificantDuplication(List.copyOf(metrics)));
        // Kotlin-specific disharmonies. Gated behind hasKotlinMetrics (rather
        // than relying on the detector predicates short-circuiting against
        // isDataClass/isSealed/numberOfExtensionFunctions) so Java-only builds
        // skip the three detector invocations entirely — including
        // detectLargeSealedHierarchy which is O(N²) over all collected classes.
        if (hasKotlinMetrics) {
            classDisharmonies.addAll(detector.detectExcessiveExtensions(List.copyOf(metrics)));
            classDisharmonies.addAll(detector.detectLargeSealedHierarchy(List.copyOf(metrics)));
            classDisharmonies.addAll(detector.detectDataClassWithLogic(List.copyOf(metrics)));
        }
        return classDisharmonies;
    }
}
