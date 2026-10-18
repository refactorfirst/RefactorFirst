package org.hjug.gradlereport;

import java.io.File;

/** Shared resolution of extension values into absolute task inputs. */
final class ExtensionValues {

    private ExtensionValues() {}

    /**
     * Resolves the output directory for the HTML / simple HTML / CSV reports to an absolute path.
     * The extension's {@code outputDirectory} overrides the default
     * ({@code <projectDir>/build/reports/refactorfirst}); relative overrides resolve against the
     * project directory. Absolute paths are passed to the report layer untouched — it normalizes
     * via {@code Path.of(...).toAbsolutePath()}.
     */
    static File resolveOutputDirectory(RefactorFirstExtension extension, File projectDirectory, File defaultDir) {
        String configured = extension.getOutputDirectory();
        if (configured == null || configured.isBlank()) {
            return defaultDir;
        }
        File dir = new File(configured);
        return dir.isAbsolute() ? dir : new File(projectDirectory, configured);
    }
}
