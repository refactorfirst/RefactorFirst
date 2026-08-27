package org.hjug.graphbuilder.graphbuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.GraphBuilderConfig;
import org.hjug.graphbuilder.GraphDependencyCollector;
import org.hjug.graphbuilder.metrics.GraphMetricsCollector;
import org.hjug.graphbuilder.metrics.KotlinMetricsCollectingVisitor;
import org.hjug.graphbuilder.visitor.KotlinDependencyVisitor;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.tree.ParseError;

/**
 * Kotlin-language source-file graph builder. Invoked unconditionally by
 * {@link CompositeGraphBuilder} (Kotlin analysis is always on). This
 * module declares a compile dependency on
 * {@code org.openrewrite:rewrite-kotlin}, so the Kotlin parser is always
 * on the classpath of any consumer of this builder.
 */
@Slf4j
public class KotlinSourceFileGraphBuilder implements SourceFileGraphBuilder {

    @Override
    public CodebaseGraphDTO buildGraph(String repositoryPath, String repositoryRoot, GraphBuilderConfig config)
            throws IOException {
        File srcDirectory = new File(repositoryPath);

        String langLevelStr = config.getKotlinLanguageLevel();
        KotlinParser.KotlinLanguageLevel kotlinLangLevel = KotlinParser.KotlinLanguageLevel.KOTLIN_2_4;
        if (!langLevelStr.isEmpty()) {
            try {
                kotlinLangLevel = KotlinParser.KotlinLanguageLevel.valueOf(langLevelStr);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown Kotlin language level '{}', falling back to KOTLIN_2_4", langLevelStr);
            }
        }

        KotlinParser kotlinParser = KotlinParser.builder()
                .languageLevel(kotlinLangLevel)
                .logCompilationWarningsAndErrors(false)
                .build();

        ExecutionContext ctx = new InMemoryExecutionContext(e -> log.warn("OpenRewrite parse/visit error", e));

        final Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        final Graph<String, DefaultWeightedEdge> packageReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        final GraphDependencyCollector dependencyCollector =
                new GraphDependencyCollector(classReferencesGraph, packageReferencesGraph);

        final KotlinDependencyVisitor<ExecutionContext> kotlinVisitor =
                new KotlinDependencyVisitor<>(repositoryPath, repositoryRoot, dependencyCollector);

        GraphMetricsCollector metricsCollector =
                new GraphMetricsCollector(classReferencesGraph, packageReferencesGraph);
        KotlinMetricsCollectingVisitor metricsVisitor = new KotlinMetricsCollectingVisitor(metricsCollector);

        try (Stream<Path> pathStream = Files.walk(Path.of(srcDirectory.getAbsolutePath()))) {
            Stream<Path> filteredStream = pathStream
                    .filter(file -> file.toString().endsWith(".kt")
                            || file.toString().endsWith(".kts"));
            if (config.isExcludeTests()
                    && config.getTestSourceDirectory() != null
                    && !config.getTestSourceDirectory().isEmpty()) {
                filteredStream = filteredStream
                        .filter(file -> !file.toString().contains(config.getTestSourceDirectory()));
            }
            List<Path> list = filteredStream.collect(Collectors.toList());

            log.info("KotlinSourceFileGraphBuilder: walking {} Kotlin files under {}", list.size(), repositoryPath);

            Path sourceRoot = Path.of(srcDirectory.getAbsolutePath());
            kotlinParser.parse(list, sourceRoot, ctx).forEach(cu -> {
                if (cu instanceof ParseError) {
                    log.warn(
                            "Parse error in {}: {}, attempting to visit erroneous tree",
                            cu.getSourcePath(),
                            ((ParseError) cu).getText());
                    // Try to visit the erroneous source file if it has a partial parse tree
                    SourceFile erroneous = ((ParseError) cu).getErroneous();
                    if (erroneous instanceof K.CompilationUnit) {
                        Path absoluteSourcePath =
                                sourceRoot.resolve(erroneous.getSourcePath()).normalize();
                        SourceFile cuWithAbsPath = erroneous.withSourcePath(absoluteSourcePath);
                        K.CompilationUnit kcu = (K.CompilationUnit) cuWithAbsPath;
                        kotlinVisitor.visit(kcu, ctx);
                        metricsVisitor.visit(kcu, ctx);
                    }
                    return;
                }
                if (!(cu instanceof K.CompilationUnit)) {
                    log.warn(
                            "Unexpected non-Kotlin compilation unit: {}",
                            cu.getClass().getName());
                    return;
                }
                // Ensure source path is absolute for correct URI generation
                // Resolve relative source path against source root, not current working directory
                Path absoluteSourcePath = sourceRoot.resolve(cu.getSourcePath()).normalize();
                SourceFile cuWithAbsPath = cu.withSourcePath(absoluteSourcePath);
                K.CompilationUnit kcu = (K.CompilationUnit) cuWithAbsPath;
                kotlinVisitor.visit(kcu, ctx);
                metricsVisitor.visit(kcu, ctx);
            });
        }

        Map<String, String> classToSourceFilePathMapping = kotlinVisitor.getClassToSourceFilePathMapping();
        return JavaSourceFileGraphBuilder.finalizeDto(
                classReferencesGraph,
                packageReferencesGraph,
                dependencyCollector,
                classToSourceFilePathMapping,
                metricsCollector);
    }
}
