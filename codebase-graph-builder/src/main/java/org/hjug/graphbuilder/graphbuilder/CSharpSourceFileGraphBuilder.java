package org.hjug.graphbuilder.graphbuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.GraphBuilderConfig;
import org.hjug.graphbuilder.GraphDependencyCollector;
import org.hjug.graphbuilder.VertexInfo;
import org.hjug.graphbuilder.metrics.GraphMetricsCollector;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 * Third language reader: C#, by way of the csharp-fact-extractor .NET program.
 * The extractor writes raw facts; this class replays them into the collectors the Java
 * and Kotlin visitors feed, so every metric and smell rule downstream is shared.
 */
@Slf4j
public class CSharpSourceFileGraphBuilder implements SourceFileGraphBuilder {

    private final CSharpExtractorRunner runner;

    /** What the last search found, so asking "is there C#?" and then building does not search twice. */
    private Path searchedDirectory;

    private List<Path> foundTargets = List.of();

    public CSharpSourceFileGraphBuilder() {
        this(new CSharpExtractorRunner());
    }

    CSharpSourceFileGraphBuilder(CSharpExtractorRunner runner) {
        this.runner = runner;
    }

    /** True when the folder holds a C# solution or project worth launching .NET for. */
    public boolean hasCSharpSources(String repositoryPath) throws IOException {
        return !targetsIn(absolute(repositoryPath)).isEmpty();
    }

    private List<Path> targetsIn(Path directory) throws IOException {
        if (!directory.equals(searchedDirectory)) {
            foundTargets = CSharpExtractorRunner.findTargets(directory);
            searchedDirectory = directory;
        }
        return foundTargets;
    }

    private static Path absolute(String path) {
        return Path.of(path).toAbsolutePath().normalize();
    }

    @Override
    public CodebaseGraphDTO buildGraph(String repositoryPath, String repositoryRoot, GraphBuilderConfig config)
            throws IOException {
        Path directory = absolute(repositoryPath);
        List<Path> targets = targetsIn(directory);
        if (targets.isEmpty()) {
            throw new IOException("No .sln, .slnx or .csproj found under " + repositoryPath);
        }
        Path root = repositoryRoot == null || repositoryRoot.isEmpty() ? directory : absolute(repositoryRoot);
        // Test projects are left out unless the caller asked for tests to be analysed.
        boolean includeTests = config != null && !config.isExcludeTests();
        Path facts = runner.run(targets, root, directory, includeTests);
        try {
            return buildGraphFromFacts(facts);
        } finally {
            Files.deleteIfExists(facts);
        }
    }

    /** Builds the graph from a facts file already on disk. Separate so it can be tested without .NET. */
    protected CodebaseGraphDTO buildGraphFromFacts(Path factsFile) throws IOException {
        Graph<VertexInfo, DefaultWeightedEdge> classGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<VertexInfo, DefaultWeightedEdge> packageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        GraphDependencyCollector dependencies = new GraphDependencyCollector(classGraph, packageGraph);
        GraphMetricsCollector metrics = new GraphMetricsCollector(classGraph, packageGraph);
        Map<String, String> classToSourceFilePathMapping = new HashMap<>();

        CSharpFacts facts;
        try (InputStream in = Files.newInputStream(factsFile)) {
            facts = CSharpFacts.read(in);
        }
        log.info(
                "CSharpSourceFileGraphBuilder: replaying {} C# types",
                facts.types().size());
        CSharpFactsReplayer.replay(facts, dependencies, metrics, classToSourceFilePathMapping);

        return JavaSourceFileGraphBuilder.finalizeDto(
                classGraph, packageGraph, dependencies, classToSourceFilePathMapping, metrics);
    }
}
