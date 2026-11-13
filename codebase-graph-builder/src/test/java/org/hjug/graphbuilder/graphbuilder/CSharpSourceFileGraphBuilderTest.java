package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.GraphBuilderConfig;
import org.hjug.graphbuilder.VertexInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CSharpSourceFileGraphBuilderTest {

    @Test
    void givenFactsFile_whenBuilt_thenDtoHasTheCycle(@TempDir Path dir) throws Exception {
        Path facts = dir.resolve("facts.json");
        try (var in = getClass().getResourceAsStream("/csharp/tiny-greeter-facts.json")) {
            Files.copy(in, facts);
        }

        CodebaseGraphDTO dto = new CSharpSourceFileGraphBuilder().buildGraphFromFacts(facts);

        assertEquals(3, dto.getClassReferencesGraph().vertexSet().size());
        assertEquals(3, dto.getClassReferencesGraph().edgeSet().size());
        assertEquals("Greeter.cs", dto.getClassToSourceFilePathMapping().get("TinyGreeter.Greeter"));
        assertTrue(dto.getClassDisharmoniesOfType("God Class").isEmpty(), "three tiny classes are no God class");
    }

    @Test
    void givenFolderWithASolution_whenAsked_thenHasCSharpSources(@TempDir Path dir) throws IOException {
        assertFalse(new CSharpSourceFileGraphBuilder().hasCSharpSources(dir.toString()));

        Files.writeString(dir.resolve("App.sln"), "");

        assertTrue(new CSharpSourceFileGraphBuilder().hasCSharpSources(dir.toString()));
    }

    /**
     * End to end through the real .NET extractor. Runs only when REFACTORFIRST_CSHARP_EXTRACTOR
     * points at a built CSharpFactExtractor.dll, so the normal build needs no .NET.
     */
    @Test
    void givenRealExtractor_whenTinyGreeterIsBuilt_thenTheCycleIsFound() throws Exception {
        String extractor = System.getenv(CSharpExtractorRunner.EXTRACTOR_ENV);
        assumeTrue(extractor != null && Files.exists(Path.of(extractor)), "extractor not configured");
        Path fixture = Path.of("..", "csharp-fact-extractor", "fixtures", "TinyGreeter")
                .toAbsolutePath()
                .normalize();
        assumeTrue(Files.isDirectory(fixture), "fixture not found");

        CodebaseGraphDTO dto = new CSharpSourceFileGraphBuilder()
                .buildGraph(fixture.toString(), fixture.toString(), GraphBuilderConfig.defaultConfig());

        var names = dto.getClassReferencesGraph().vertexSet().stream()
                .map(VertexInfo::getName)
                .sorted()
                .toList();
        assertEquals(
                java.util.List.of("TinyGreeter.Greeter", "TinyGreeter.MessageStore", "TinyGreeter.Program"), names);
        assertEquals(3, dto.getClassReferencesGraph().edgeSet().size());
    }
}
