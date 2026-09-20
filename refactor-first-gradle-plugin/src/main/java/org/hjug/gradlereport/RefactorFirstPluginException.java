package org.hjug.gradlereport;

import org.gradle.api.GradleException;

public class RefactorFirstPluginException extends GradleException {
    public RefactorFirstPluginException(String message, Throwable cause) {
        super(formatErrorMessage(message), cause);
    }

    public RefactorFirstPluginException(String message) {
        super(formatErrorMessage(message));
    }

    private static String formatErrorMessage(String message) {
        return "RefactorFirst plugin error: " + message + "\n"
                + "For help, see: https://github.com/refactorfirst/RefactorFirst/wiki/Troubleshooting";
    }
}
