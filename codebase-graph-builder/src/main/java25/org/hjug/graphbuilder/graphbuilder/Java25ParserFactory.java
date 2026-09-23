package org.hjug.graphbuilder.graphbuilder;

import java.util.Optional;
import org.openrewrite.java.Java25Parser;
import org.openrewrite.java.JavaParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java 25 multi-release variant of {@code Java25ParserFactory} (JEP 238).
 *
 * <p>This source lives in {@code src/main/java25} and is compiled with
 * {@code --release 25} into {@code META-INF/versions/25} of the packaged jar.
 * It is loaded only on JDK 25+ runtimes, where it shadows the base variant —
 * so it may reference {@link Java25Parser} (class file 69.0) directly, without
 * reflection. JEP 238 requires this class to expose exactly the same public
 * API as the base variant.
 */
public final class Java25ParserFactory {

    // Plain slf4j rather than Lombok @Slf4j: keep this versioned class free of
    // annotation processing so the extra compile execution stays minimal.
    private static final Logger log = LoggerFactory.getLogger(Java25ParserFactory.class);

    /** Prevents instantiation of this utility class. */
    private Java25ParserFactory() {
        // Utility class
    }

    /**
     * Creates the Java 25 parser. This variant only ever runs on JDK 25+, so
     * the parser should always be constructible; a failure (e.g.
     * {@code rewrite-java-25} missing from a hand-rolled classpath) still
     * degrades gracefully to {@link Optional#empty()} so callers fall back to
     * the standard parser.
     *
     * @return the Java 25 parser, or {@link Optional#empty()} on failure; never
     *         throws
     */
    public static Optional<JavaParser> createJava25Parser() {
        try {
            log.info("Using Java 25 parser for Java 25 language feature support");
            return Optional.of(Java25Parser.builder().build());
        } catch (Throwable t) {
            log.warn("Java 25 parser unavailable on a Java 25+ runtime; falling back to the standard parser", t);
            return Optional.empty();
        }
    }
}
