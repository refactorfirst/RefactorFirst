package org.hjug.gradlereport;

import org.gradle.api.GradleException;

public class RefactorFirstPluginException extends GradleException {
    /** Creates a plugin exception with a formatted message and cause. */
    public RefactorFirstPluginException(String message, Throwable cause) {
        super(formatErrorMessage(message), cause);
    }

    /** Creates a plugin exception with a formatted message. */
    public RefactorFirstPluginException(String message) {
        super(formatErrorMessage(message));
    }

    /** Adds plugin context and troubleshooting guidance to an error message. */
    private static String formatErrorMessage(String message) {
        return "RefactorFirst plugin error: " + message + "\n"
                + "For help, see: https://github.com/refactorfirst/RefactorFirst/wiki/Troubleshooting";
    }
}
