package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CSharpExtractorRunnerTest {

    @Test
    void givenFolderWithoutCSharp_whenAsked_thenNoSolutionOrProjectIsFound(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("Main.java"), "class Main {}");

        assertTrue(CSharpExtractorRunner.findTargets(dir).isEmpty());
    }

    @Test
    void givenSolutionAndProject_whenAsked_thenSolutionWins(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("App.sln"), "");
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/App.csproj"), "");

        assertEquals(List.of("App.sln"), names(CSharpExtractorRunner.findTargets(dir)));
    }

    @Test
    void givenNewFormatSolution_whenAsked_thenItIsFound(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("App.slnx"), "");

        assertEquals(List.of("App.slnx"), names(CSharpExtractorRunner.findTargets(dir)));
    }

    @Test
    void givenOnlyAProjectInASubfolder_whenAsked_thenItIsFound(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("src/App"));
        Files.writeString(dir.resolve("src/App/App.csproj"), "");

        assertEquals(List.of("App.csproj"), names(CSharpExtractorRunner.findTargets(dir)));
    }

    @Test
    void givenOnlyBuildOutput_whenAsked_thenItIsIgnored(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("obj"));
        Files.writeString(dir.resolve("obj/Generated.csproj"), "");

        assertTrue(CSharpExtractorRunner.findTargets(dir).isEmpty());
    }

    @Test
    void givenNoExtractorConfigured_whenRun_thenThrowsNamingTheSetting(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("App.sln"), "");

        IOException e = assertThrows(
                IOException.class, () -> new CSharpExtractorRunner(null).run(List.of(dir.resolve("App.sln")), dir));

        assertTrue(e.getMessage().contains(CSharpExtractorRunner.EXTRACTOR_ENV));
    }

    @Test
    void givenExtractorPathThatDoesNotExist_whenRun_thenThrowsNamingThePath(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("App.sln"), "");
        String missing = dir.resolve("nowhere/CSharpFactExtractor.dll").toString();

        IOException e = assertThrows(
                IOException.class, () -> new CSharpExtractorRunner(missing).run(List.of(dir.resolve("App.sln")), dir));

        assertTrue(e.getMessage().contains(missing));
    }

    @Test
    void givenSeveralProjectsAndNoSolution_whenAsked_thenAllOfThemAreFound(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("App"));
        Files.createDirectories(dir.resolve("App.Tests"));
        Files.createDirectories(dir.resolve("App/obj"));
        Files.writeString(dir.resolve("App/App.csproj"), "");
        Files.writeString(dir.resolve("App.Tests/App.Tests.csproj"), "");
        Files.writeString(dir.resolve("App/obj/Generated.csproj"), "");

        assertEquals(List.of("App.Tests.csproj", "App.csproj"), names(CSharpExtractorRunner.findTargets(dir)));
    }

    @Test
    void givenASolutionInASubfolderOnly_whenAsked_thenTheProjectsAreUsedInstead(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("App"));
        Files.writeString(dir.resolve("App/App.sln"), "");
        Files.writeString(dir.resolve("App/App.csproj"), "");

        assertEquals(List.of("App.csproj"), names(CSharpExtractorRunner.findTargets(dir)));
    }

    @Test
    void givenADotnetThatCannotBeStarted_whenRun_thenTheMessageNamesTheDotnetSetting(@TempDir Path dir)
            throws IOException {
        Files.writeString(dir.resolve("App.sln"), "");
        Path dll = Files.writeString(dir.resolve("CSharpFactExtractor.dll"), "");

        IOException e = assertThrows(IOException.class, () -> new CSharpExtractorRunner(
                        dll.toString(), dir.resolve("no-such-dotnet").toString())
                .run(List.of(dir.resolve("App.sln")), dir));

        assertTrue(e.getMessage().contains(CSharpExtractorRunner.DOTNET_ENV));
    }

    private static List<String> names(List<Path> paths) {
        return paths.stream().map(p -> p.getFileName().toString()).toList();
    }
}
