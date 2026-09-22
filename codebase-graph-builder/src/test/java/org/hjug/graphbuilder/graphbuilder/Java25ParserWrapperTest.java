package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import org.hjug.graphbuilder.JavaRuntimeDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.openrewrite.java.JavaParser;

/**
 * Tests for {@link Java25ParserWrapper}.
 *
 * <p>Behavior is runtime-dependent: on a &lt; 25 JVM the wrapper must return
 * {@code null} without ever loading the Java 25 parser classes (they are
 * compiled for class file 69.0 and would fail to load); on a Java 25 JVM with
 * {@code rewrite-java-25} on the classpath it must return a working parser.
 */
class Java25ParserWrapperTest {

    @DisplayName("Returns null without attempting to load on runtimes older than Java 25")
    @Test
    void returnsNullOnOlderRuntimes() {
        if (JavaRuntimeDetector.isJava25OrHigher()) {
            return; // covered by the @EnabledOnJre(JAVA_25) test below
        }
        assertNull(Java25ParserWrapper.tryCreateJava25Parser(false));
    }

    @DisplayName("Repeated calls are consistent (reflection results are cached)")
    @Test
    void repeatedCallsAreConsistent() {
        JavaParser first = Java25ParserWrapper.tryCreateJava25Parser(false);
        JavaParser second = Java25ParserWrapper.tryCreateJava25Parser(false);
        assertEquals(first != null, second != null);
    }

    @DisplayName("Java 25 runtime: parser is created when rewrite-java-25 is on the classpath")
    @EnabledOnJre(JRE.JAVA_25)
    @Test
    void createsParserOnJava25Runtime() {
        assertTrue(JavaRuntimeDetector.isJava25OrHigher());
        JavaParser parser = Java25ParserWrapper.tryCreateJava25Parser(false);
        assertNotNull(parser, "Expected a Java 25 parser on a Java 25+ runtime");
    }

    @DisplayName("Forced load on an older runtime fails gracefully (never throws)")
    @Test
    void forcedLoadFailsGracefullyOnOlderRuntimes() {
        if (JavaRuntimeDetector.isJava25OrHigher()) {
            // On Java 25+ a forced load simply succeeds.
            assertNotNull(Java25ParserWrapper.tryCreateJava25Parser(true));
            return;
        }
        // rewrite-java-25 is on the test classpath but compiled for class file
        // 69.0, so Class.forName fails with UnsupportedClassVersionError.
        // The wrapper must swallow that LinkageError and return null.
        assertDoesNotThrow(() -> {
            JavaParser parser = Java25ParserWrapper.tryCreateJava25Parser(true);
            assertNull(parser, "Forced load must fail gracefully on a runtime older than Java 25");
        });
    }
}
