package org.hjug.graphbuilder.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Unit tests for review.md item #9: once a {@link ClassMetrics} instance has
 * been through {@link GraphMetricsCollector#finalizeMetrics()}, it must be
 * effectively immutable for the remainder of the pipeline — every
 * setter/adder must reject mutation with an {@link IllegalStateException}
 * whose message names the FQN, and every collection getter must return an
 * unmodifiable view that reflects the pre-freeze contents.
 *
 * <p>The parse-time accumulator phase is explicitly out of scope: visitors
 * (and tests that drive them directly) may continue to mutate freely
 * <em>until</em> the instance is frozen. These tests exercise the frozen
 * view only.
 */
class ClassMetricsFinalizationImmutabilityTest {

    @Test
    void methodMetricsDoesNotExposeBackingCollectionSetters() {
        Set<String> forbiddenSetters = Set.of(
                "setAccessedForeignClasses", "setAccessedForeignAttributes", "setAccessedOwnAttributes");

        assertTrue(Stream.of(MethodMetrics.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(forbiddenSetters::contains));
    }

    @Test
    void methodMetricsEqualityDoesNotChangeWhenViewsAreCachedOrMetricsAreFrozen() {
        MethodMetrics first = new MethodMetrics("method", "method()V");
        MethodMetrics second = new MethodMetrics("method", "method()V");
        first.addAccessedForeignClass("com.example.Foreign");
        second.addAccessedForeignClass("com.example.Foreign");

        int initialHashCode = first.hashCode();
        first.getAccessedForeignClasses();
        first.freeze();

        assertEquals(second, first);
        assertEquals(initialHashCode, first.hashCode());
    }

    private static ClassMetrics newPopulated(String fqn) {
        ClassMetrics m = new ClassMetrics(fqn);
        m.setClassName(fqn.substring(fqn.lastIndexOf('.') + 1));
        m.setPackageName(fqn.contains(".") ? fqn.substring(0, fqn.lastIndexOf('.')) : "");
        m.setSourceFilePath(fqn.replace('.', '/') + ".java");
        m.setLinesOfCode(10);
        m.setNumberOfAttributes(2);
        m.setNumberOfPublicAttributes(1);
        m.setAccessToForeignData(3);
        m.setTightClassCohesion(0.25);
        m.setParentClass("com.example.Parent");
        m.setNumberOfProtectedMembers(4);
        m.setNumberOfExtensionFunctions(5);
        m.setSealedHierarchyDepth(2);
        m.setDataClass(true);
        m.setSealed(true);
        m.setHasExplicitLogic(true);
        m.addTypeParameterFqn("com.example.T");
        m.addExtensionReceiverType("com.example.Receiver");
        m.addSealedHierarchyAncestor("com.example.Sealed");
        m.addOverriddenMethod("foo()V");
        m.addUsedParentMember("bar");
        MethodMetrics mm = new MethodMetrics("hello", "hello()V");
        mm.setLinesOfCode(3);
        m.addMethod(mm);
        m.addAttribute("attr", false);
        m.addDependency("com.example.Other");
        return m;
    }

    @DisplayName("every setter/adder throws IllegalStateException naming the FQN after freeze()")
    @Test
    void setterThrowsAfterFreeze() {
        ClassMetrics cm = newPopulated("com.example.alpha.Alpha");

        cm.freeze();

        String expectedFqn = "com.example.alpha.Alpha";
        List<Executable> mutators = Arrays.asList(
                () -> cm.setSourceFilePath("x"),
                () -> cm.setLinesOfCode(1),
                () -> cm.setNumberOfAttributes(1),
                () -> cm.setNumberOfPublicAttributes(1),
                () -> cm.setAccessToForeignData(1),
                () -> cm.setTightClassCohesion(0.5),
                () -> cm.setParentClass("p"),
                () -> cm.setNumberOfProtectedMembers(1),
                () -> cm.setNumberOfExtensionFunctions(1),
                () -> cm.setSealedHierarchyDepth(1),
                () -> cm.setDataClass(false),
                () -> cm.setSealed(false),
                () -> cm.setHasExplicitLogic(false),
                () -> cm.setClassName("c"),
                () -> cm.setPackageName("p"),
                () -> cm.setFullyQualifiedName("fqn"),
                () -> cm.addTypeParameterFqn("x"),
                () -> cm.addExtensionReceiverType("x"),
                () -> cm.addSealedHierarchyAncestor("x"),
                () -> cm.addOverriddenMethod("x"),
                () -> cm.addUsedParentMember("x"),
                () -> cm.addMethod(new MethodMetrics("m", "m()V")),
                () -> cm.addAttribute("x", true),
                () -> cm.addDependency("x"),
                cm::calculateAccessToForeignData,
                cm::calculateTightClassCohesion);

        for (Executable r : mutators) {
            IllegalStateException ex = assertThrows(IllegalStateException.class, r, "mutation must fail post-freeze");
            assertTrue(
                    ex.getMessage().contains(expectedFqn),
                    "exception message should name the FQN, was: " + ex.getMessage());
        }
    }

    @DisplayName("collection getters return unmodifiable views after freeze() and preserve pre-freeze contents")
    @Test
    void collectionGettersReturnUnmodifiableAfterFreeze() {
        ClassMetrics cm = newPopulated("com.example.beta.Beta");

        // Capture expected contents BEFORE freezing.
        Set<String> expectedDependencies = new HashSet<>(cm.getDependencies());
        Set<String> expectedAttributes = new HashSet<>(cm.getAttributes());
        Set<String> expectedOverridden = new HashSet<>(cm.getOverriddenMethods());
        Set<String> expectedUsedParent = new HashSet<>(cm.getUsedParentMembers());
        Set<String> expectedTypeParams = new HashSet<>(cm.getTypeParameterFqns());
        Set<String> expectedReceivers = new HashSet<>(cm.getExtensionReceiverTypes());
        Set<String> expectedSealedAncestors = new HashSet<>(cm.getSealedHierarchyAncestors());
        int expectedMethods = cm.getMethods().size();

        cm.freeze();

        assertUnmodifiableAndContains(expectedDependencies, cm.getDependencies(), "dependencies");
        assertUnmodifiableAndContains(expectedAttributes, cm.getAttributes(), "attributes");
        assertUnmodifiableAndContains(expectedOverridden, cm.getOverriddenMethods(), "overriddenMethods");
        assertUnmodifiableAndContains(expectedUsedParent, cm.getUsedParentMembers(), "usedParentMembers");
        assertUnmodifiableAndContains(expectedTypeParams, cm.getTypeParameterFqns(), "typeParameterFqns");
        assertUnmodifiableAndContains(expectedReceivers, cm.getExtensionReceiverTypes(), "extensionReceiverTypes");
        assertUnmodifiableAndContains(
                expectedSealedAncestors, cm.getSealedHierarchyAncestors(), "sealedHierarchyAncestors");

        Map<String, MethodMetrics> methodsView = cm.getMethods();
        assertEquals(expectedMethods, methodsView.size());
        assertThrows(
                UnsupportedOperationException.class, () -> methodsView.put("z()V", new MethodMetrics("z", "z()V")));
        assertThrows(
                UnsupportedOperationException.class, () -> methodsView.values().clear());
    }

    @DisplayName("freeze() is idempotent and the frozen view is visible to a reader thread (volatile publish)")
    @Test
    void freezeIsIdempotentAndVisible() throws InterruptedException {
        ClassMetrics cm = newPopulated("com.example.gamma.Gamma");

        Set<String> expectedDependencies = new HashSet<>(cm.getDependencies());

        CountDownLatch frozen = new CountDownLatch(1);
        CountDownLatch readerDone = new CountDownLatch(1);
        AtomicReference<Set<String>> seenByReader = new AtomicReference<>();

        Thread reader = new Thread(() -> {
            try {
                frozen.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            seenByReader.set(cm.getDependencies());
            readerDone.countDown();
        });
        reader.start();

        cm.freeze();
        // double freeze is a no-op
        cm.freeze();
        frozen.countDown();
        assertTrue(readerDone.await(2, TimeUnit.SECONDS), "reader thread should finish");

        // Reader thread observed the frozen contents (proves the volatile write
        // is published; even in a notional cross-thread read the data is there).
        assertEquals(expectedDependencies, seenByReader.get());
        // And a mutation from this thread still fails, post-freeze.
        assertThrows(IllegalStateException.class, () -> cm.addDependency("x"));
    }

    @DisplayName("freezing a ClassMetrics also freezes each MethodMetrics reachable from getMethods()")
    @Test
    void methodMetricsFreeMakesInnerMethodsImmutable() {
        ClassMetrics cm = newPopulated("com.example.delta.Delta");

        cm.freeze();

        for (MethodMetrics mm : cm.getMethods().values()) {
            // mutating the inner method must fail — it was frozen alongside the class
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> mm.setLinesOfCode(99));
            assertTrue(
                    ex.getMessage().contains("MethodMetrics"),
                    "MethodMetrics freeze message should reference MethodMetrics: " + ex.getMessage());
        }
    }

    @DisplayName("pre-freeze mutation remains legal (accumulator phase still works)")
    @Test
    void preFreezeMutationRemainsLegal() {
        ClassMetrics cm = newPopulated("com.example.epsilon.Epsilon");

        // Still mutable before freeze()
        cm.setLinesOfCode(42);
        cm.addDependency("com.example.Another");
        cm.addMethod(new MethodMetrics("late", "late()V"));
        assertFalse(cm.getDependencies().isEmpty());

        cm.freeze();

        // Now it must be frozen
        assertThrows(IllegalStateException.class, () -> cm.addDependency("z"));
    }

    private static void assertUnmodifiableAndContains(Set<String> expected, Set<String> actual, String label) {
        assertEquals(expected, actual, "view contents for " + label + " should match pre-freeze contents");
        try {
            actual.add("ZZZ_MUTATION_ATTEMPT");
            fail(label + " view should be unmodifiable but add() succeeded");
        } catch (UnsupportedOperationException ok) {
            // good
        }
    }
}
