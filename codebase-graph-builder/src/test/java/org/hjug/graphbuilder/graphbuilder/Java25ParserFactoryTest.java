package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.JavaSourceFile;

/**
 * Contract tests for {@link Java25ParserFactory}, the JEP 238 multi-release
 * seam that activates the OpenRewrite Java 25 parser.
 *
 * <p>The packaged {@code codebase-graph-builder} jar carries two variants of
 * {@code Java25ParserFactory}: a base variant (Java 17) that always returns
 * {@link Optional#empty()}, and a {@code META-INF/versions/25} variant
 * (Java 25) that instantiates {@code Java25Parser} directly. The JVM's
 * multi-release jar lookup selects the variant.
 *
 * <p>Important test-environment note: JEP 238 shadowing only applies to
 * classes loaded from a <em>jar</em>. Surefire runs tests against the exploded
 * {@code target/classes} directory, so the {@link Java25ParserFactory} symbol
 * in this JVM always resolves to the base variant — even on JDK 25. The
 * versioned variant is therefore verified by loading it directly from
 * {@code target/classes/META-INF/versions/25} in an isolated class loader
 * (T2/T3), and the real jar shadowing is verified by
 * {@code MultiReleaseJarIT} against the packaged artifact.
 */
class Java25ParserFactoryTest {

    private static final String FACTORY_FQN = "org.hjug.graphbuilder.graphbuilder.Java25ParserFactory";
    private static final String VERSIONED_CLASS_RELATIVE_PATH =
            "org/hjug/graphbuilder/graphbuilder/Java25ParserFactory.class";
    private static final Path VERSIONED_CLASSES_DIR = Paths.get("target", "classes", "META-INF", "versions", "25");
    private static final Path JAVA25_FIXTURE_ROOT = Paths.get("src", "test", "resources", "java25SrcDirectory");

    @DisplayName("T1: base variant returns empty Optional and never throws on pre-25 runtimes")
    @Test
    void baseVariantReturnsEmpty() {
        Optional<JavaParser> parser = assertDoesNotThrow(Java25ParserFactory::createJava25Parser);
        if (Runtime.version().feature() < 25) {
            // No multi-release shadowing is possible on a pre-25 runtime.
            assertTrue(parser.isEmpty(), "Pre-25 runtimes must not receive a Java 25 parser");
        } else {
            // On JDK 25 surefire runs, target/classes is an exploded directory
            // and JEP 238 shadowing does not apply, so the base variant still
            // answers here. Jar-shadowed behavior on JDK 25 is covered by
            // MultiReleaseJarIT.
            assertTrue(parser.isEmpty(), "Exploded-classes test classpath must resolve the base variant");
        }
    }

    @DisplayName("T2: versioned variant creates a parser that accepts Java 25 source (JEP 513)")
    @EnabledForJreRange(min = JRE.JAVA_25)
    @Test
    void versionedVariantCreatesWorkingJava25Parser() throws Exception {
        Class<?> versionedFactory = loadVersionedFactory();
        Object result = versionedFactory.getMethod("createJava25Parser").invoke(null);

        assertInstanceOf(Optional.class, result);
        Optional<?> parserOptional = (Optional<?>) result;
        assertTrue(parserOptional.isPresent(), "Versioned variant must create a Java 25 parser on JDK 25+");
        Object parser = parserOptional.get();
        assertTrue(
                parser.getClass().getName().contains("Java25"),
                "Expected a Java25Parser instance, got: " + parser.getClass().getName());

        // Prove it really is the Java 25 parser: the fixture uses JEP 513
        // flexible constructor bodies (statements before super()), which the
        // Java 21 parser rejects.
        ExecutionContext ctx = new InMemoryExecutionContext(t -> fail("Parse failure on Java 25 fixture", t));
        List<SourceFile> parsed = ((JavaParser) parser)
                .parse(
                        List.of(JAVA25_FIXTURE_ROOT.resolve(
                                Paths.get("com", "example", "java25", "FlexibleConstructorBody.java"))),
                        JAVA25_FIXTURE_ROOT,
                        ctx)
                .collect(Collectors.toList());

        assertEquals(1, parsed.size(), "Expected exactly one parsed source file");
        assertTrue(
                parsed.get(0) instanceof JavaSourceFile,
                "Java 25 fixture must parse to a compilation unit, got: "
                        + parsed.get(0).getClass().getName());
    }

    @DisplayName("T3: versioned and base variants expose identical public API (JEP 238 parity)")
    @EnabledForJreRange(min = JRE.JAVA_25)
    @Test
    void versionedVariantExposesIdenticalPublicApi() throws Exception {
        Class<?> versionedFactory = loadVersionedFactory();
        assertEquals(
                publicStaticSignatures(Java25ParserFactory.class),
                publicStaticSignatures(versionedFactory),
                "JEP 238 requires the versioned class to expose the same public API as the base class");
    }

    /**
     * Loads the Java 25 variant of the factory from
     * {@code target/classes/META-INF/versions/25} in an isolated class loader.
     * Skips when the versioned class file does not exist (i.e. the module was
     * built on a pre-25 JDK, where the {@code jdk25+} build profile is
     * inactive).
     */
    private static Class<?> loadVersionedFactory() throws Exception {
        assumeTrue(
                Files.exists(VERSIONED_CLASSES_DIR.resolve(VERSIONED_CLASS_RELATIVE_PATH)),
                "Versioned factory class only exists when built on JDK 25+");
        // Hide org.hjug classes from the parent so the child genuinely loads
        // the versioned class from META-INF/versions/25 — plain parent-first
        // delegation would resolve the base variant from target/classes.
        ClassLoader filteredParent = new ClassLoader(Java25ParserFactoryTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.hjug.")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        URLClassLoader loader =
                new URLClassLoader(new URL[] {VERSIONED_CLASSES_DIR.toUri().toURL()}, filteredParent);
        return Class.forName(FACTORY_FQN, false, loader);
    }

    private static Set<String> publicStaticSignatures(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()))
                .map(Java25ParserFactoryTest::signatureOf)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String signatureOf(Method method) {
        return method.getName() + Arrays.toString(method.getParameterTypes()) + ":"
                + method.getReturnType().getSimpleName();
    }
}
