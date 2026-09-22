package org.hjug.graphbuilder.graphbuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.JavaRuntimeDetector;
import org.openrewrite.java.JavaParser;

/**
 * Wrapper for conditionally loading the Java 25 parser
 * ({@code org.openrewrite.java.Java25Parser} from the optional
 * {@code rewrite-java-25} dependency).
 *
 * <p>Reflection is used deliberately: {@code rewrite-java-25} is compiled for
 * class file 69.0, so any direct compile-time reference would fail to load on
 * the Java 17+ runtimes the Maven/Gradle plugins must support. The load is
 * only attempted on a Java 25+ runtime (unless forced for diagnostics), and
 * any failure — including {@link UnsupportedClassVersionError} — results in a
 * graceful {@code null} so callers can fall back to
 * {@link JavaParser#fromJavaVersion()}.
 *
 * <p>Reflection results are cached: the load is attempted at most once per
 * class loader.
 */
@Slf4j
public final class Java25ParserWrapper {

    private static final String JAVA25_PARSER_CLASS = "org.openrewrite.java.Java25Parser";

    private enum LoadState {
        NOT_ATTEMPTED,
        AVAILABLE,
        UNAVAILABLE
    }

    private static volatile LoadState loadState = LoadState.NOT_ATTEMPTED;
    private static Class<?> java25ParserClass;
    private static Method builderMethod;

    private Java25ParserWrapper() {
        // Utility class
    }

    /**
     * Attempts to create a Java 25 parser via reflection.
     *
     * @param force when {@code true}, attempt the load even when the runtime
     *              does not appear to be Java 25 or higher (escape hatch for
     *              exotic JVMs where version detection fails). The attempt may
     *              still fail gracefully with {@code null}.
     * @return a Java 25 capable {@link JavaParser}, or {@code null} if the Java
     *         25 parser is not available/usable on this runtime
     */
    public static JavaParser tryCreateJava25Parser(boolean force) {
        if (!force && !JavaRuntimeDetector.isJava25OrHigher()) {
            log.debug(
                    "Java 25 runtime not detected (runtime: {}), skipping Java 25 parser",
                    JavaRuntimeDetector.getRuntimeVersion());
            return null;
        }

        if (!ensureLoaded()) {
            log.debug("rewrite-java-25 not available on classpath/runtime, falling back to standard parser");
            return null;
        }

        return buildParser();
    }

    private static synchronized boolean ensureLoaded() {
        if (loadState != LoadState.NOT_ATTEMPTED) {
            return loadState == LoadState.AVAILABLE;
        }
        try {
            java25ParserClass = Class.forName(JAVA25_PARSER_CLASS);
            builderMethod = java25ParserClass.getMethod("builder");
            loadState = LoadState.AVAILABLE;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            // LinkageError covers UnsupportedClassVersionError, thrown when the
            // Java 25-compiled class is loaded on an older runtime (e.g. a
            // forced load on Java 17/21).
            log.debug("Java 25 parser could not be loaded", e);
            loadState = LoadState.UNAVAILABLE;
        }
        return loadState == LoadState.AVAILABLE;
    }

    private static JavaParser buildParser() {
        try {
            Object builder = builderMethod.invoke(null);
            Method buildMethod = builder.getClass().getMethod("build");
            return (JavaParser) buildMethod.invoke(builder);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            log.warn("Failed to create Java 25 parser via reflection", e);
            return null;
        }
    }
}
