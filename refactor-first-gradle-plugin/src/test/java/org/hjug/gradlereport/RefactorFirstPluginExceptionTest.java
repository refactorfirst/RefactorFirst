package org.hjug.gradlereport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RefactorFirstPluginExceptionTest {

    /** Verifies plugin exceptions prefix their messages. */
    @Test
    void exceptionFormatsMessageWithPrefix() {
        RefactorFirstPluginException exception = new RefactorFirstPluginException("test error");

        assertTrue(exception.getMessage().startsWith("RefactorFirst plugin error:"));
        assertTrue(exception.getMessage().contains("test error"));
    }

    /** Verifies plugin exceptions include troubleshooting guidance. */
    @Test
    void exceptionIncludesHelpLink() {
        RefactorFirstPluginException exception = new RefactorFirstPluginException("test error");

        assertTrue(
                exception.getMessage().contains("https://github.com/refactorfirst/RefactorFirst/wiki/Troubleshooting"));
    }

    /** Verifies plugin exceptions retain their causes. */
    @Test
    void exceptionWithCausePreservesCause() {
        Throwable cause = new RuntimeException("original cause");
        RefactorFirstPluginException exception = new RefactorFirstPluginException("test error", cause);

        assertEquals(cause, exception.getCause());
    }

    /** Verifies plugin exceptions are Gradle exceptions. */
    @Test
    void exceptionIsGradleException() {
        RefactorFirstPluginException exception = new RefactorFirstPluginException("test error");

        assertTrue(exception instanceof org.gradle.api.GradleException);
    }
}
