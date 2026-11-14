package org.hjug.graphbuilder.graphbuilder;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    /** Folders never searched for projects: build output, packages and version control. */
    private static final Set<String> SKIPPED_FOLDERS = Set.of("bin", "obj", "node_modules", ".git");

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
        List<Path> solutions;
        try (Stream<Path> top = Files.list(directory)) {
            solutions = top.filter(Files::isRegularFile)
                    .filter(p -> endsWith(p, ".sln") || endsWith(p, ".slnx"))
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (!solutions.isEmpty()) {
            if (solutions.size() > 1) {
                log.warn(
                        "Several solutions in {}; analysing {} and ignoring {}",
                        directory,
                        solutions.get(0).getFileName(),
                        solutions.subList(1, solutions.size()));
            }
            return List.of(solutions.get(0));
        }
        List<Path> projects = new ArrayList<>();
        Files.walkFileTree(
                directory, EnumSet.noneOf(FileVisitOption.class), PROJECT_SEARCH_DEPTH, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        boolean skipped = !dir.equals(directory)
                                && SKIPPED_FOLDERS.contains(dir.getFileName().toString());
                        return skipped ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile() && endsWith(file, ".csproj")) {
                            projects.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    /** A folder that cannot be read is passed over; it must not stop the search. */
                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException e) {
                        return FileVisitResult.CONTINUE;
                    }
                });
        Collections.sort(projects);
        return projects;
    }

    /** Runs the extractor with test projects left out, in the repository root. */
    Path run(List<Path> targets, Path repositoryRoot) throws IOException {
        return run(targets, repositoryRoot, repositoryRoot, false);
    }

    /**
     * Runs the extractor and returns the facts file it wrote. The caller deletes the file.
     *
     * @param workingDirectory where the extractor runs: the analysed folder, so that .NET picks the
     *                         SDK that folder pins (global.json) and not the one where Java was started
     * @param includeTests     true to analyse test projects too
     */
    Path run(List<Path> targets, Path repositoryRoot, Path workingDirectory, boolean includeTests) throws IOException {
        if (extractorDll == null || extractorDll.isBlank()) {
            throw new IOException("C# sources found but no extractor is configured. Set " + EXTRACTOR_ENV + " (or -D"
                    + EXTRACTOR_PROPERTY + ") to the path of CSharpFactExtractor.dll");
        }
        if (!Files.isRegularFile(Path.of(extractorDll))) {
            throw new IOException("C# fact extractor not found at " + extractorDll);
        }
        Path out = Files.createTempFile("refactorfirst-csharp-facts", ".json");
        List<String> command = command(targets, repositoryRoot, out, includeTests);
        log.info("Running C# fact extractor on {}", targets);
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .inheritIO()
                    .start();
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

    /** The command line. Paths are made absolute because the extractor runs in another folder. */
    List<String> command(List<Path> targets, Path repositoryRoot, Path out, boolean includeTests) {
        List<String> command = new ArrayList<>(
                List.of(dotnet, Path.of(extractorDll).toAbsolutePath().toString()));
        targets.forEach(target -> command.add(target.toAbsolutePath().toString()));
        command.addAll(List.of(
                "--root",
                repositoryRoot.toAbsolutePath().toString(),
                "--out",
                out.toAbsolutePath().toString()));
        if (includeTests) {
            command.add("--include-tests");
        }
        return command;
    }

    private static boolean endsWith(Path path, String extension) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension);
    }
}
