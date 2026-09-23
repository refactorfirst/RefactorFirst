package org.hjug.graphbuilder.graphbuilder;

import java.util.Optional;
import org.openrewrite.java.JavaParser;

/**
 * JEP 238 (multi-release jar) seam for the OpenRewrite Java 25 parser.
 *
 * <p>The packaged {@code codebase-graph-builder} jar is multi-release: a Java
 * 25-compiled variant of this class lives under
 * {@code META-INF/versions/25/org/hjug/graphbuilder/graphbuilder/}. On a JDK
 * 25+ runtime the JVM's versioned jar lookup <em>shadows</em> this base
 * variant with the versioned one, which instantiates
 * {@code org.openrewrite.java.Java25Parser} directly. On JDK 17/21 runtimes
 * the versioned entry is invisible to the class loader — its class-file
 * version (69.0) can never be loaded — and this base variant answers.
 *
 * <p>Note: multi-release shadowing only applies when classes are loaded from
 * a <em>jar</em>. In exploded-directory classpaths (e.g. unit tests running
 * against {@code target/classes}) this base variant answers even on JDK 25.
 */
public final class Java25ParserFactory {

    /** Prevents instantiation of this utility class. */
    private Java25ParserFactory() {
        // Utility class
    }

    /**
     * Creates a Java 25-capable parser.
     *
     * @return a Java 25 {@link JavaParser} when the multi-release Java 25
     *         variant of this class is active (JDK 25+ runtime, classes loaded
     *         from the packaged jar); otherwise {@link Optional#empty()}. Never
     *         throws.
     */
    public static Optional<JavaParser> createJava25Parser() {
        // Base variant: Java 25 parser classes are not referenced here, so
        // nothing newer than class file 61 exists to load. The versioned
        // variant under META-INF/versions/25 shadows this method on JDK 25+.
        return Optional.empty();
    }
}
