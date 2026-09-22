package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Failsafe IT (runs after {@code package}) verifying that the packaged
 * {@code codebase-graph-builder} jar is a well-formed JEP 238 multi-release
 * jar: it carries {@code Multi-Release: true}, contains the Java 25 variant
 * of {@code Java25ParserFactory} under {@code META-INF/versions/25}, and the
 * JVM's versioned jar lookup actually shadows the base variant on JDK 25.
 *
 * <p>Only built artifacts from a JDK 25+ build contain versioned entries (the
 * {@code jdk25-multi-release} profile), so these tests assume a JDK 25+
 * runtime and self-skip otherwise. CI's release job pins JDK 25 and fails the
 * build if the versioned entries are missing, so a skipped run here on JDK 17
 * is by design.
 */
class MultiReleaseJarIT {

    private static final String FACTORY_FQN = "org.hjug.graphbuilder.graphbuilder.Java25ParserFactory";
    private static final String VERSIONED_ENTRY =
            "META-INF/versions/25/org/hjug/graphbuilder/graphbuilder/Java25ParserFactory.class";

    @DisplayName("T4: packaged jar declares Multi-Release and carries the versioned factory")
    @Test
    void packagedJarIsMultiReleaseWithVersionedFactory() throws Exception {
        Path jar = packagedJar();
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            assertEquals(
                    "true",
                    jarFile.getManifest().getMainAttributes().getValue("Multi-Release"),
                    "Packaged jar manifest must declare Multi-Release: true");
            JarEntry versionedEntry = jarFile.getJarEntry(VERSIONED_ENTRY);
            assertNotNull(versionedEntry, "Packaged jar must contain " + VERSIONED_ENTRY);

            // The versioned class file must really be class file version 69
            // (Java 25): bytes 6-7 of the class file hold the major version.
            byte[] bytes = jarFile.getInputStream(versionedEntry).readAllBytes();
            int major = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
            assertEquals(69, major, "Versioned factory must be compiled for Java 25 (class file 69.0)");
        }
    }

    @DisplayName("T4: loading the factory from the packaged jar on JDK 25 activates the Java 25 variant")
    @Test
    void jarShadowingActivatesJava25Variant() throws Exception {
        Path jar = packagedJar();
        // The filtered parent hides org.hjug classes so the child genuinely
        // loads the factory from the jar (a plain parent-first delegation
        // would resolve the base variant from target/classes instead).
        ClassLoader filteredParent = new ClassLoader(MultiReleaseJarIT.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("org.hjug.")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, filteredParent)) {
            Class<?> factoryFromJar = Class.forName(FACTORY_FQN, false, loader);
            assertTrue(
                    factoryFromJar
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .getPath()
                            .contains(".jar"),
                    "Factory must be loaded from the packaged jar");

            Method create = factoryFromJar.getMethod("createJava25Parser");
            Object result = create.invoke(null);
            assertInstanceOf(Optional.class, result);
            assertTrue(
                    ((Optional<?>) result).isPresent(),
                    "Multi-release shadowing must activate the Java 25 variant on JDK 25 runtimes");

            // API parity with the base variant, asserted from the jar-loaded class.
            assertEquals(
                    publicStaticSignatures(Java25ParserFactory.class),
                    publicStaticSignatures(factoryFromJar),
                    "JEP 238 requires the versioned class to expose the same public API as the base class");
        }
    }

    private static Path packagedJar() throws IOException {
        assumeTrue(Runtime.version().feature() >= 25, "Versioned jar entries exist only in artifacts built on JDK 25+");
        Path target = Paths.get("target");
        try (Stream<Path> files = Files.list(target)) {
            return files.filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("codebase-graph-builder-")
                                && name.endsWith(".jar")
                                && !name.contains("-sources")
                                && !name.contains("-javadoc");
                    })
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Packaged codebase-graph-builder jar not found in target/ — ITs must run after package"));
        }
    }

    private static Set<String> publicStaticSignatures(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()))
                .map(m -> m.getName() + Arrays.toString(m.getParameterTypes()) + ":"
                        + m.getReturnType().getSimpleName())
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
