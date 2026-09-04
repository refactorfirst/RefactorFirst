package org.hjug.refactorfirst.report;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ReportWriter {

    /** Resolves a configured report directory and rejects escapes or existing link components. */
    public static String containReportDirectory(final File baseDir, final String configuredDir) {
        final Path base = (baseDir != null ? baseDir.toPath() : Path.of(""))
                .toAbsolutePath()
                .normalize();
        final String configured =
                configuredDir == null || configuredDir.isBlank() ? "target" + File.separator + "site" : configuredDir;
        final Path resolved = base.resolve(configured).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException(
                    "Report output directory escapes the project base directory: " + configuredDir);
        }
        rejectExistingSymbolicLinkComponents(resolved);
        return resolved.toString();
    }

    /**
     * Writes through directory descriptors and atomically renames the completed report into place.
     * Failures propagate so command and plugin callers cannot report success after a blocked write.
     */
    public static void writeReportToDisk(
            final String reportOutputDirectory, final String filename, final String string) {
        Path outputDirectory = Path.of(reportOutputDirectory).toAbsolutePath().normalize();
        Path reportName = validateFilename(filename);
        List<DirectoryStream<Path>> openedDirectories = new ArrayList<>();

        try {
            SecureDirectoryStream<Path> outputStream = openSecureDirectoryPath(outputDirectory, openedDirectories);
            writeAtomically(outputStream, reportName, string);
            log.info("Done! View the report at {}", outputDirectory.resolve(reportName));
        } catch (IOException | UnsupportedOperationException e) {
            log.error("Unable to write report {}", outputDirectory.resolve(reportName), e);
            throw new ReportWriteException("Unable to write report " + outputDirectory.resolve(reportName), e);
        } finally {
            closeDirectories(openedDirectories);
        }
    }

    private static Path validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new ReportWriteException("Report filename must not be empty");
        }
        Path reportName = Path.of(filename);
        if (reportName.isAbsolute()
                || reportName.getNameCount() != 1
                || ".".equals(filename)
                || "..".equals(filename)) {
            throw new ReportWriteException("Report filename must be a single path component: " + filename);
        }
        return reportName;
    }

    private static SecureDirectoryStream<Path> openSecureDirectoryPath(
            Path outputDirectory, List<DirectoryStream<Path>> openedDirectories) throws IOException {
        Path root = outputDirectory.getRoot();
        if (root == null) {
            throw new IOException("Report output directory has no filesystem root: " + outputDirectory);
        }

        DirectoryStream<Path> rootStream = Files.newDirectoryStream(root);
        openedDirectories.add(rootStream);
        SecureDirectoryStream<Path> current = asSecureDirectoryStream(rootStream, root);
        Path currentPath = root;

        for (Path component : root.relativize(outputDirectory)) {
            SecureDirectoryStream<Path> child;
            try {
                child = current.newDirectoryStream(component, NOFOLLOW_LINKS);
            } catch (NoSuchFileException e) {
                Path directoryToCreate = currentPath.resolve(component);
                Files.createDirectory(directoryToCreate);
                child = current.newDirectoryStream(component, NOFOLLOW_LINKS);
            }
            openedDirectories.add(child);
            current = child;
            currentPath = currentPath.resolve(component);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static SecureDirectoryStream<Path> asSecureDirectoryStream(DirectoryStream<Path> stream, Path directory) {
        if (!(stream instanceof SecureDirectoryStream<?>)) {
            throw new UnsupportedOperationException(
                    "Secure directory operations are unavailable for report output: " + directory);
        }
        return (SecureDirectoryStream<Path>) stream;
    }

    private static void writeAtomically(SecureDirectoryStream<Path> directory, Path reportName, String content)
            throws IOException {
        BasicFileAttributeView targetView =
                directory.getFileAttributeView(reportName, BasicFileAttributeView.class, NOFOLLOW_LINKS);
        try {
            BasicFileAttributes attributes = targetView.readAttributes();
            if (attributes.isSymbolicLink() || attributes.isDirectory()) {
                throw new IOException("Refusing to replace non-regular report path: " + reportName);
            }
        } catch (NoSuchFileException ignored) {
            // The normal first-write case.
        }

        Path temporaryName = Path.of("." + reportName + "." + UUID.randomUUID() + ".tmp");
        Set<OpenOption> options = Set.of(CREATE_NEW, WRITE, NOFOLLOW_LINKS);
        boolean moved = false;
        try {
            try (SeekableByteChannel channel = directory.newByteChannel(temporaryName, options);
                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(Channels.newOutputStream(channel), Charset.defaultCharset()))) {
                writer.write(content);
            }
            directory.move(temporaryName, directory, reportName);
            moved = true;
        } finally {
            if (!moved) {
                try {
                    directory.deleteFile(temporaryName);
                } catch (NoSuchFileException ignored) {
                    // Nothing to clean up.
                }
            }
        }
    }

    private static void rejectExistingSymbolicLinkComponents(Path path) {
        Path current = path.getRoot();
        if (current == null) {
            return;
        }
        for (Path component : current.relativize(path)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("Report output path contains a symbolic link: " + current);
            }
            if (!Files.exists(current, NOFOLLOW_LINKS)) {
                return;
            }
        }
    }

    private static void closeDirectories(List<DirectoryStream<Path>> directories) {
        for (int i = directories.size() - 1; i >= 0; i--) {
            try {
                directories.get(i).close();
            } catch (IOException e) {
                log.warn("Unable to close report output directory", e);
            }
        }
    }

    public static final class ReportWriteException extends RuntimeException {
        public ReportWriteException(String message) {
            super(message);
        }

        public ReportWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private ReportWriter() {}
}
