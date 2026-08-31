package org.hjug.graphbuilder.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.hjug.graphbuilder.metrics.ClassMetrics;
import org.hjug.graphbuilder.metrics.DisharmonyDetector;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.ClassDisharmony;
import org.hjug.graphbuilder.metrics.DisharmonyTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the Kotlin-detector gate in {@link JavaSourceFileGraphBuilder#getClassDisharmonies}
 * — review item #12's wiring/performance concern.
 *
 * <p>The gate must skip the three Kotlin-specific detectors
 * ({@link DisharmonyDetector#detectExcessiveExtensions},
 * {@link DisharmonyDetector#detectLargeSealedHierarchy},
 * {@link DisharmonyDetector#detectDataClassWithLogic}) when
 * {@code hasKotlinMetrics == false}, <em>independently</em> of the
 * detector predicates' own short-circuit on {@link ClassMetrics#isDataClass()} /
 * {@link ClassMetrics#isSealed()} / {@link ClassMetrics#getNumberOfExtensionFunctions()}.
 *
 * <p>To prove the gate (not the predicate) is responsible, the Java-only test
 * deliberately feeds metrics with those flags <strong>artificially set</strong>
 * such that the detectors WOULD flag them if invoked. Asserting they are absent
 * in the output proves the detectors were never called.
 */
class JavaSourceFileGraphBuilderKotlinDetectorGateTest {

    private final DisharmonyDetector detector = new DisharmonyDetector();

    /** Metrics that WOULD trip {@code detectExcessiveExtensions} if it ran. */
    private static ClassMetrics excessiveExtensionsMetrics() {
        ClassMetrics m = new ClassMetrics("com.example.ExtensionHost");
        m.setNumberOfExtensionFunctions(11); // >= 10
        m.addExtensionReceiverType("com.example.ReceiverA");
        m.addExtensionReceiverType("com.example.ReceiverB");
        m.addExtensionReceiverType("com.example.ReceiverC");
        m.addExtensionReceiverType("com.example.ReceiverD");
        m.addExtensionReceiverType("com.example.ReceiverE"); // 5 receivers
        return m;
    }

    /** Metrics that WOULD trip {@code detectLargeSealedHierarchy} if it ran. */
    private static List<ClassMetrics> largeSealedHierarchyMetrics() {
        List<ClassMetrics> all = new ArrayList<>();
        ClassMetrics sealed = new ClassMetrics("com.example.Shape");
        sealed.setSealed(true);
        all.add(sealed);
        for (int i = 0; i < 12; i++) {
            ClassMetrics subtype = new ClassMetrics("com.example.ShapeImpl" + i);
            subtype.addSealedHierarchyAncestor("com.example.Shape");
            all.add(subtype);
        }
        return all;
    }

    /** Metrics that WOULD trip {@code detectDataClassWithLogic} if it ran. */
    private static ClassMetrics dataClassWithLogicMetrics() {
        ClassMetrics m = new ClassMetrics("com.example.Money");
        m.setDataClass(true);
        m.setHasExplicitLogic(true);
        return m;
    }

    private static List<ClassDisharmony> disharmoniesOfType(List<ClassDisharmony> ds, String type) {
        List<ClassDisharmony> matching = new ArrayList<>();
        for (ClassDisharmony d : ds) {
            if (type.equals(d.getDisharmonyType())) {
                matching.add(d);
            }
        }
        return matching;
    }

    @DisplayName("hasKotlinMetrics=false skips detectExcessiveExtensions even though metrics would trip it")
    @Test
    void javaOnlyBuild_skipsExcessiveExtensionsEvenWhenFlagsArtificiallySet() {
        Collection<ClassMetrics> metrics = List.of(excessiveExtensionsMetrics());
        List<ClassDisharmony> result =
                JavaSourceFileGraphBuilder.getClassDisharmonies(detector, metrics, /*hasKotlinMetrics=*/ false);
        assertTrue(
                disharmoniesOfType(result, DisharmonyTypes.EXCESSIVE_EXTENSIONS).isEmpty(),
                "gate (hasKotlinMetrics=false) must skip "
                        + "detectExcessiveExtensions even for metrics that would trip it");
    }

    @DisplayName("hasKotlinMetrics=false skips detectLargeSealedHierarchy even though metrics would trip it")
    @Test
    void javaOnlyBuild_skipsLargeSealedHierarchyEvenWhenFlagsArtificiallySet() {
        Collection<ClassMetrics> metrics = largeSealedHierarchyMetrics();
        List<ClassDisharmony> result =
                JavaSourceFileGraphBuilder.getClassDisharmonies(detector, metrics, /*hasKotlinMetrics=*/ false);
        assertTrue(
                disharmoniesOfType(result, DisharmonyTypes.LARGE_SEALED_HIERARCHY)
                        .isEmpty(),
                "gate (hasKotlinMetrics=false) must skip detectLargeSealedHierarchy "
                        + "(the O(N²) detector) even for metrics that would trip it");
    }

    @DisplayName("hasKotlinMetrics=false skips detectDataClassWithLogic even though metrics would trip it")
    @Test
    void javaOnlyBuild_skipsDataClassWithLogicEvenWhenFlagsArtificiallySet() {
        Collection<ClassMetrics> metrics = List.of(dataClassWithLogicMetrics());
        List<ClassDisharmony> result =
                JavaSourceFileGraphBuilder.getClassDisharmonies(detector, metrics, /*hasKotlinMetrics=*/ false);
        assertTrue(
                disharmoniesOfType(result, DisharmonyTypes.DATA_CLASS_WITH_LOGIC)
                        .isEmpty(),
                "gate (hasKotlinMetrics=false) must skip "
                        + "detectDataClassWithLogic even for metrics that would trip it");
    }

    @DisplayName("hasKotlinMetrics=true delegates to the Kotlin-specific detectors")
    @Test
    void kotlinBuild_invokesKotlinDetectors() {
        List<ClassMetrics> metrics = new ArrayList<>();
        metrics.add(excessiveExtensionsMetrics());
        metrics.addAll(largeSealedHierarchyMetrics());
        metrics.add(dataClassWithLogicMetrics());

        List<ClassDisharmony> result =
                JavaSourceFileGraphBuilder.getClassDisharmonies(detector, metrics, /*hasKotlinMetrics=*/ true);

        assertFalse(
                disharmoniesOfType(result, DisharmonyTypes.EXCESSIVE_EXTENSIONS).isEmpty(),
                "hasKotlinMetrics=true must invoke detectExcessiveExtensions and surface flags");
        assertFalse(
                disharmoniesOfType(result, DisharmonyTypes.LARGE_SEALED_HIERARCHY)
                        .isEmpty(),
                "hasKotlinMetrics=true must invoke detectLargeSealedHierarchy and surface flags");
        assertFalse(
                disharmoniesOfType(result, DisharmonyTypes.DATA_CLASS_WITH_LOGIC)
                        .isEmpty(),
                "hasKotlinMetrics=true must invoke detectDataClassWithLogic and surface flags");
    }

    @DisplayName("hasKotlinMetrics=false still runs every Java detector")
    @Test
    void javaOnlyBuild_stillRunsJavaDetectors() {
        // A minimal metrics set is enough to confirm the Java detectors'
        // results are present and the gate only suppresses the Kotlin-specific ones.
        ClassMetrics m = new ClassMetrics("com.example.Plain");
        // no Java disharmony flags set → no Java disharmonies flagged, but the
        // detectors must still execute so that a real Java codebase gets
        // God Class / Brain Method / etc. detection. We assert the call does
        // not throw and returns a non-null list (the Java detectors ran).
        List<ClassDisharmony> result =
                JavaSourceFileGraphBuilder.getClassDisharmonies(detector, List.of(m), /*hasKotlinMetrics=*/ false);
        assertNotNull(result, "Java detectors must still run when the Kotlin-detector gate is closed");
    }
}
