package org.hjug.refactorfirst;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportCommandTest {

    @Test
    void call_returnsFailureWhenOutputPathContainsSymlink(@TempDir Path tempDir) throws Exception {
        Path target = Files.createDirectory(tempDir.resolve("target"));
        Path link = tempDir.resolve("link");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException e) {
            assumeTrue(false, "Symbolic links not supported on this platform");
        }

        ReportCommand command = new ReportCommand();
        setField(command, "baseDir", tempDir.toFile());
        setField(command, "outputDirectory", "link/reports");
        setField(command, "reportType", ReportType.CSV);

        assertEquals(1, command.call());
    }

    @Test
    void call_returnsFailureWhenOutputPathTraversesOutsideBase(@TempDir Path tempDir) throws Exception {
        Path baseDirectory = Files.createDirectory(tempDir.resolve("project"));
        ReportCommand command = new ReportCommand();
        setField(command, "baseDir", baseDirectory.toFile());
        setField(command, "outputDirectory", "../escape");
        setField(command, "reportType", ReportType.CSV);

        assertEquals(1, command.call());
        assertFalse(Files.exists(tempDir.resolve("escape")));
    }

    private static void setField(ReportCommand command, String name, Object value) throws ReflectiveOperationException {
        Field field = ReportCommand.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(command, value);
    }
}
