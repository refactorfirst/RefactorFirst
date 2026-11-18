package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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

    @Test
    void givenProjectsUnderPackageAndGitFolders_whenAsked_thenThoseFoldersAreNotSearched(@TempDir Path dir)
            throws IOException {
        Files.createDirectories(dir.resolve("node_modules/pkg"));
        Files.createDirectories(dir.resolve(".git/hooks"));
        Files.createDirectories(dir.resolve("src/bin"));
        Files.writeString(dir.resolve("node_modules/pkg/Pkg.csproj"), "");
        Files.writeString(dir.resolve(".git/hooks/Hook.csproj"), "");
        Files.writeString(dir.resolve("src/bin/Out.csproj"), "");
        Files.writeString(dir.resolve("src/App.csproj"), "");

        assertEquals(List.of("App.csproj"), names(CSharpExtractorRunner.findTargets(dir)));
    }

    @Test
    void givenSeveralSolutions_whenAsked_thenTheFirstByNameIsUsed(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("App.Mobile.sln"), "");
        Files.writeString(dir.resolve("App.sln"), "");

        assertEquals(List.of("App.Mobile.sln"), names(CSharpExtractorRunner.findTargets(dir)));
    }

    @Test
    void givenAFolderNamedLikeASolution_whenAsked_thenItIsNotTakenForOne(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("old.sln"));
        Files.writeString(dir.resolve("App.csproj"), "");

        assertEquals(List.of("App.csproj"), names(CSharpExtractorRunner.findTargets(dir)));
    }

    @Test
    void givenTestsAreWanted_whenTheCommandIsBuilt_thenItAsksTheExtractorToIncludeThem(@TempDir Path dir) {
        CSharpExtractorRunner runner = new CSharpExtractorRunner("tools/CSharpFactExtractor.dll", "dotnet");
        List<Path> targets = List.of(dir.resolve("App.sln"));

        List<String> without = runner.command(targets, dir, dir.resolve("facts.json"), false);
        List<String> with = runner.command(targets, dir, dir.resolve("facts.json"), true);

        assertFalse(without.contains("--include-tests"));
        assertTrue(with.contains("--include-tests"));
        // The extractor runs in the analysed folder, so a relative extractor path must be made absolute.
        assertTrue(Path.of(with.get(1)).isAbsolute());
    }

    @Test
    void givenAnExtractorThatFails_whenRun_thenTheExitCodeIsReportedAndNoFileIsLeft(@TempDir Path dir)
            throws IOException {
        Path dll = Files.writeString(dir.resolve("CSharpFactExtractor.dll"), "");
        Path dotnet = stubDotnet(dir, "exit 3");

        IOException e =
                assertThrows(IOException.class, () -> new CSharpExtractorRunner(dll.toString(), dotnet.toString())
                        .run(List.of(dir.resolve("App.sln")), dir));

        assertTrue(e.getMessage().contains("exited with code 3"), e.getMessage());
    }

    @Test
    void givenAFolderWithASpaceInItsName_whenRun_thenTheExtractorRunsInThatFolderWithIntactArguments(@TempDir Path dir)
            throws IOException {
        Path analysed = Files.createDirectories(dir.resolve("my code"));
        Path solution = Files.writeString(analysed.resolve("App.sln"), "");
        Path dll = Files.writeString(dir.resolve("CSharpFactExtractor.dll"), "");
        // Stands in for dotnet: writes the folder it runs in and its arguments, one per line, to the --out file.
        Path dotnet = stubDotnet(
                dir,
                "for last; do :; done",
                "out=$last; prev=",
                "for a; do if [ \"$prev\" = --out ]; then out=$a; fi; prev=$a; done",
                "pwd -P > \"$out\"",
                "for a; do echo \"$a\" >> \"$out\"; done");

        Path facts = new CSharpExtractorRunner(dll.toString(), dotnet.toString())
                .run(List.of(solution), analysed, analysed, true);
        try {
            List<String> lines = Files.readAllLines(facts);
            assertEquals(analysed.toRealPath().toString(), lines.get(0));
            assertTrue(lines.contains(solution.toAbsolutePath().toString()), lines.toString());
            assertTrue(lines.contains("--include-tests"), lines.toString());
        } finally {
            Files.deleteIfExists(facts);
        }
    }

    /** A shell script used in place of dotnet, so the process handling is tested without .NET. */
    private static Path stubDotnet(Path dir, String... lines) throws IOException {
        assumeFalse(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"), "needs a POSIX shell");
        Path script = dir.resolve("fake-dotnet.sh");
        Files.writeString(script, "#!/bin/sh\n" + String.join("\n", lines) + "\n");
        assertTrue(script.toFile().setExecutable(true));
        return script;
    }

    private static List<String> names(List<Path> paths) {
        return paths.stream().map(p -> p.getFileName().toString()).toList();
    }
}
