package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hjug.graphbuilder.graphbuilder.CSharpSourceFileGraphBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompositeGraphBuilderCSharpTest {

    private static final String JAVA_CLASS = "package demo;\npublic class Hello { }\n";

    @Test
    void givenJavaOnlyFolder_whenBuilt_thenCSharpReaderIsNotRun(@TempDir Path dir) throws IOException {
        writeJavaClass(dir);
        AtomicBoolean ran = new AtomicBoolean(false);
        CSharpSourceFileGraphBuilder spy = new CSharpSourceFileGraphBuilder() {
            @Override
            public CodebaseGraphDTO buildGraph(String path, String root, GraphBuilderConfig config) {
                ran.set(true);
                return null;
            }
        };

        CodebaseGraphDTO dto =
                new CompositeGraphBuilder(spy).getCodebaseGraphDTO(dir.toString(), GraphBuilderConfig.defaultConfig());

        assertFalse(ran.get(), "no .sln or .csproj, so .NET must not be launched");
        assertTrue(classNames(dto).contains("demo.Hello"));
    }

    @Test
    void givenCSharpFailure_whenBuilt_thenJavaGraphIsStillReturned(@TempDir Path dir) throws IOException {
        writeJavaClass(dir);
        Files.writeString(dir.resolve("App.sln"), "");
        CSharpSourceFileGraphBuilder failing = new CSharpSourceFileGraphBuilder() {
            @Override
            public CodebaseGraphDTO buildGraph(String path, String root, GraphBuilderConfig config) throws IOException {
                throw new IOException("dotnet not found");
            }
        };

        CodebaseGraphDTO dto = new CompositeGraphBuilder(failing)
                .getCodebaseGraphDTO(dir.toString(), GraphBuilderConfig.defaultConfig());

        assertTrue(classNames(dto).contains("demo.Hello"));
    }

    @Test
    void givenJavaAndCSharp_whenBuilt_thenBothSetsOfClassesAreInOneGraph(@TempDir Path dir) throws Exception {
        writeJavaClass(dir);
        Files.writeString(dir.resolve("App.sln"), "");

        CodebaseGraphDTO dto = new CompositeGraphBuilder(readerFromGoldenFacts(dir))
                .getCodebaseGraphDTO(dir.toString(), GraphBuilderConfig.defaultConfig());

        List<String> names = classNames(dto);
        assertTrue(names.contains("demo.Hello"));
        assertTrue(names.contains("TinyGreeter.Greeter"));
        assertTrue(names.contains("TinyGreeter.MessageStore"));
        assertTrue(names.contains("TinyGreeter.Program"));
        assertEquals("Greeter.cs", dto.getClassToSourceFilePathMapping().get("TinyGreeter.Greeter"));
    }

    @Test
    void givenCSharpOnly_whenBuilt_thenEdgesAndWeightsSurviveTheMerge(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("App.sln"), "");

        CodebaseGraphDTO dto = new CompositeGraphBuilder(readerFromGoldenFacts(dir))
                .getCodebaseGraphDTO(dir.toString(), GraphBuilderConfig.defaultConfig());

        var graph = dto.getClassReferencesGraph();
        assertEquals(3, graph.vertexSet().size());
        assertEquals(3, graph.edgeSet().size());
        VertexInfo greeter = vertex(dto, "TinyGreeter.Greeter");
        VertexInfo store = vertex(dto, "TinyGreeter.MessageStore");
        assertEquals(4.0, graph.getEdgeWeight(graph.getEdge(greeter, store)));
        assertEquals(2.0, graph.getEdgeWeight(graph.getEdge(store, greeter)));
    }

    private static void writeJavaClass(Path dir) throws IOException {
        Files.createDirectories(dir.resolve("demo"));
        Files.writeString(dir.resolve("demo/Hello.java"), JAVA_CLASS);
    }

    /** A reader that skips .NET and replays the extractor's checked-in output for TinyGreeter. */
    private CSharpSourceFileGraphBuilder readerFromGoldenFacts(Path dir) throws IOException {
        Path facts = dir.resolve("facts.json");
        try (var in = getClass().getResourceAsStream("/csharp/tiny-greeter-facts.json")) {
            Files.copy(in, facts);
        }
        return new CSharpSourceFileGraphBuilder() {
            @Override
            public CodebaseGraphDTO buildGraph(String path, String root, GraphBuilderConfig config) throws IOException {
                return buildGraphFromFacts(facts);
            }
        };
    }

    private static List<String> classNames(CodebaseGraphDTO dto) {
        return dto.getClassReferencesGraph().vertexSet().stream()
                .map(VertexInfo::getName)
                .toList();
    }

    private static VertexInfo vertex(CodebaseGraphDTO dto, String name) {
        return dto.getClassReferencesGraph().vertexSet().stream()
                .filter(v -> v.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
