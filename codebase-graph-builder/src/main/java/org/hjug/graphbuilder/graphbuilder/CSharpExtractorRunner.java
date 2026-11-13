package org.hjug.graphbuilder.graphbuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/** Finds the C# solution in a folder and runs csharp-fact-extractor on it. */
@Slf4j
final class CSharpExtractorRunner {

    static final String EXTRACTOR_PROPERTY = "refactorfirst.csharp.extractor";
    static final String EXTRACTOR_ENV = "REFACTORFIRST_CSHARP_EXTRACTOR";
    /** Which dotnet to launch. The extractor needs a .NET 10 runtime, and the first dotnet on the PATH may be older. */
    static final String DOTNET_PROPERTY = "refactorfirst.dotnet";

    static final String DOTNET_ENV = "REFACTORFIRST_DOTNET";
    private static final long TIMEOUT_MINUTES = 30;
    private static final int PROJECT_SEARCH_DEPTH = 6;

    private final String extractorDll;
    private final String dotnet;

    CSharpExtractorRunner() {
        this(
                System.getProperty(EXTRACTOR_PROPERTY, System.getenv(EXTRACTOR_ENV)),
                System.getProperty(DOTNET_PROPERTY, System.getenv(DOTNET_ENV)));
    }

    CSharpExtractorRunner(String extractorDll) {
        this(extractorDll, null);
    }

    CSharpExtractorRunner(String extractorDll, String dotnet) {
        this.extractorDll = extractorDll;
        this.dotnet = dotnet == null || dotnet.isBlank() ? "dotnet" : dotnet;
    }

    /**
     * What to hand the extractor: the .sln or .slnx in the folder itself if there is one, otherwise
     * every .csproj found outside build output. A repository with several projects and no solution at
     * its root must have all of them analysed, not an arbitrary one.
     */
    static List<Path> findTargets(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> top = Files.list(directory)) {
            Optional<Path> solution = top.filter(p -> endsWith(p, ".sln") || endsWith(p, ".slnx"))
                    .sorted()
                    .findFirst();
            if (solution.isPresent()) {
                return List.of(solution.get());
            }
        }
        try (Stream<Path> all = Files.walk(directory, PROJECT_SEARCH_DEPTH)) {
            return all.filter(p -> endsWith(p, ".csproj"))
                    .filter(p -> !inBuildOutput(directory.relativize(p)))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /** Runs the extractor and returns the facts file it wrote. The caller deletes the file. */
    Path run(List<Path> targets, Path repositoryRoot) throws IOException {
        if (extractorDll == null || extractorDll.isBlank()) {
            throw new IOException("C# sources found but no extractor is configured. Set " + EXTRACTOR_ENV + " (or -D"
                    + EXTRACTOR_PROPERTY + ") to the path of CSharpFactExtractor.dll");
        }
        if (!Files.isRegularFile(Path.of(extractorDll))) {
            throw new IOException("C# fact extractor not found at " + extractorDll);
        }
        Path out = Files.createTempFile("refactorfirst-csharp-facts", ".json");
        List<String> command = new ArrayList<>(List.of(dotnet, extractorDll));
        targets.forEach(target -> command.add(target.toString()));
        command.addAll(List.of("--root", repositoryRoot.toString(), "--out", out.toString()));
        log.info("Running C# fact extractor on {}", targets);
        Process process;
        try {
            process = new ProcessBuilder(command).inheritIO().start();
        } catch (IOException e) {
            Files.deleteIfExists(out);
            throw new IOException(
                    "Could not start '" + dotnet + "'; is .NET installed? Set " + DOTNET_ENV
                            + " to the full path of a dotnet that has the .NET 10 runtime",
                    e);
        }
        try {
            if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                Files.deleteIfExists(out);
                throw new IOException("C# fact extractor timed out after " + TIMEOUT_MINUTES + " minutes");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            Files.deleteIfExists(out);
            throw new IOException("Interrupted while waiting for the C# fact extractor", e);
        }
        if (process.exitValue() != 0) {
            Files.deleteIfExists(out);
            throw new IOException("C# fact extractor exited with code " + process.exitValue()
                    + ". If the output above says to install or update .NET, set " + DOTNET_ENV
                    + " to the full path of a dotnet that has the .NET 10 runtime");
        }
        return out;
    }

    private static boolean endsWith(Path path, String extension) {
        return path.getFileName().toString().toLowerCase().endsWith(extension);
    }

    private static boolean inBuildOutput(Path relative) {
        for (Path part : relative) {
            String name = part.toString();
            if (name.equals("bin") || name.equals("obj") || name.equals("node_modules")) {
                return true;
            }
        }
        return false;
    }
}
