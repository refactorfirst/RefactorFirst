package org.hjug.graphbuilder.metrics;

import java.util.*;
import lombok.Getter;

/**
 * Per-class metrics accumulator. Mutable during the parse-time visitor walk
 * (single-threaded write phase); frozen in place by
 * {@link GraphMetricsCollector#finalizeMetrics()} after all derived
 * computations complete. Once frozen, every setter/adder rejects mutation
 * with {@link IllegalStateException} and every collection getter returns an
 * unmodifiable view (review item #9). The {@code volatile} {@link #finalized}
 * flag is the publish contract for the post-finalize read phase.
 */
public class ClassMetrics {
    /**
     * Single-threaded write-then-read publish flag, set by {@link #freeze()}
     * (invoked only from {@link GraphMetricsCollector#finalizeMetrics()}).
     * Once {@code true}, every setter/adder rejects mutation. {@code volatile}
     * documents the publish-to-reader intent and provides a happens-before for
     * a notional future cross-thread read of the finalized DTO.
     */
    private volatile boolean finalized;

    /**
     * Throws {@link IllegalStateException} naming the FQN if this instance has
     * been frozen. Called as the first statement of every setter/adder so the
     * parse-time visitors (which run pre-finalize) keep working unchanged.
     */
    private void requireMutable() {
        if (finalized) {
            throw new IllegalStateException("ClassMetrics is final for FQN=" + fullyQualifiedName);
        }
    }

    /**
     * Package-private freeze entry point, invoked only by
     * {@link GraphMetricsCollector#finalizeMetrics()} after every derived
     * computation completes. Idempotent. Cascades the freeze to every owned
     * {@link MethodMetrics} so that freezing the class cannot leave its inner
     * method accumulators mutable (the collector also freezes them, but this
     * makes the freeze self-contained and safe to call in isolation from
     * tests).
     */
    void freeze() {
        finalized = true;
        for (MethodMetrics m : methods.values()) {
            m.freeze();
        }
    }

    @Getter
    private String sourceFilePath;

    @Getter
    private String fullyQualifiedName;

    @Getter
    private String className;

    @Getter
    private String packageName;

    @Getter
    private int linesOfCode;

    @Getter
    private int numberOfAttributes;

    @Getter
    private int numberOfPublicAttributes;

    @Getter
    private int accessToForeignData;

    @Getter
    private double tightClassCohesion;

    @Getter
    private Set<String> dependencies = new HashSet<>();

    @Getter
    private Map<String, MethodMetrics> methods = new HashMap<>();

    @Getter
    private Set<String> attributes = new HashSet<>();

    @Getter
    private String parentClass;

    @Getter
    private Set<String> overriddenMethods = new HashSet<>();

    @Getter
    private int numberOfProtectedMembers;

    @Getter
    private Set<String> usedParentMembers = new HashSet<>();

    /**
     * FQNs of every class referenced as a type-parameter bound on this
     * class declaration and on any of its declared methods or Kotlin
     * properties. Populated by the metrics visitor's handling of
     * {@link org.openrewrite.java.tree.J.TypeParameter} bounds (Java and
     * Kotlin) and Kotlin {@link org.openrewrite.kotlin.tree.K.TypeAlias}
     * initializers/type parameters. Used by the type-parameter metric
     * collection and downstream Kotlin-specific disharmony detectors.
     */
    @Getter
    private Set<String> typeParameterFqns = new HashSet<>();

    /**
     * Kotlin-specific: number of extension functions
     * ({@code fun Receiver.methodName()}) declared inside this class's
     * body. Top-level extension functions (file-scope) are NOT counted
     * against an owning class; only extension functions physically
     * declared inside this class contribute. Used by
     * {@link DisharmonyDetector#detectExcessiveExtensions}.
     */
    @Getter
    private int numberOfExtensionFunctions;

    /**
     * Kotlin-specific: number of distinct receiver foreign types targeted
     * by this class's declared extension functions. Receiver types are
     * extracted from
     * {@link org.openrewrite.kotlin.tree.K.MethodDeclaration}'s
     * {@code Extension} marker / receiver type expression. Maintained as
     * the visitor walks each extension function declaration; size is used
     * as the "≥5 foreign receiver types" criterion of
     * {@link DisharmonyDetector#detectExcessiveExtensions}.
     */
    @Getter
    private final Set<String> extensionReceiverTypes = new HashSet<>();

    /**
     * Kotlin-specific: FQNs of all sealed ancestors in this class's type
     * hierarchy. Populated by inspecting the {@code implements} list
     * (Kotlin sealed subtypes are surfaced as Java {@code implements
     * Shape}) for ancestor FQNs whose corresponding
     * {@link ClassMetrics#isSealed} flag is {@code true}. Used by
     * {@link DisharmonyDetector#detectLargeSealedHierarchy}.
     */
    @Getter
    private final Set<String> sealedHierarchyAncestors = new HashSet<>();

    /**
     * Kotlin-specific: depth of this class in a sealed hierarchy. Root
     * sealed class has depth=1; each indirect descendant nests deeper.
     * Computed post-walk in
     * {@link GraphMetricsCollector#finalizeMetrics()}.
     */
    @Getter
    private int sealedHierarchyDepth;

    /**
     * Kotlin-specific: {@code true} when this class is a Kotlin data class
     * ({@code data class Foo}). Detected by inspecting
     * {@link org.openrewrite.java.tree.J.Modifier}s of type
     * {@code LanguageExtension} whose {@code getKeyword()} equals
     * {@code "data"}. Java classes always return {@code false}. Used by
     * {@link DisharmonyDetector#detectDataClassWithLogic}.
     */
    @Getter
    private boolean dataClass;

    /**
     * Kotlin-specific: {@code true} when this class is a Kotlin sealed
     * class ({@code sealed class}) or sealed interface. Detected by
     * inspecting {@link org.openrewrite.java.tree.J.Modifier}s of type
     * {@code LanguageExtension} whose {@code getKeyword()} equals
     * {@code "sealed"}. Used by
     * {@link DisharmonyDetector#detectLargeSealedHierarchy}.
     */
    @Getter
    private boolean sealed;

    /**
     * Kotlin-specific: {@code true} when this class is a data class that
     * also declares non-accessor methods beyond simple getters/setters.
     * {@link DisharmonyDetector#detectDataClassWithLogic} uses this flag
     * combined with WMC > 14.
     */
    @Getter
    private boolean hasExplicitLogic;

    // --- Setters (guarded; reject post-finalize mutation) -----------------

    public void setSourceFilePath(String sourceFilePath) {
        requireMutable();
        this.sourceFilePath = sourceFilePath;
    }

    public void setFullyQualifiedName(String fullyQualifiedName) {
        requireMutable();
        this.fullyQualifiedName = fullyQualifiedName;
    }

    public void setClassName(String className) {
        requireMutable();
        this.className = className;
    }

    public void setPackageName(String packageName) {
        requireMutable();
        this.packageName = packageName;
    }

    public void setLinesOfCode(int linesOfCode) {
        requireMutable();
        this.linesOfCode = linesOfCode;
    }

    public void setNumberOfAttributes(int numberOfAttributes) {
        requireMutable();
        this.numberOfAttributes = numberOfAttributes;
    }

    public void setNumberOfPublicAttributes(int numberOfPublicAttributes) {
        requireMutable();
        this.numberOfPublicAttributes = numberOfPublicAttributes;
    }

    public void setAccessToForeignData(int accessToForeignData) {
        requireMutable();
        this.accessToForeignData = accessToForeignData;
    }

    public void setTightClassCohesion(double tightClassCohesion) {
        requireMutable();
        this.tightClassCohesion = tightClassCohesion;
    }

    public void setParentClass(String parentClass) {
        requireMutable();
        this.parentClass = parentClass;
    }

    public void setNumberOfProtectedMembers(int numberOfProtectedMembers) {
        requireMutable();
        this.numberOfProtectedMembers = numberOfProtectedMembers;
    }

    public void setNumberOfExtensionFunctions(int numberOfExtensionFunctions) {
        requireMutable();
        this.numberOfExtensionFunctions = numberOfExtensionFunctions;
    }

    public void setSealedHierarchyDepth(int sealedHierarchyDepth) {
        requireMutable();
        this.sealedHierarchyDepth = sealedHierarchyDepth;
    }

    public void setDataClass(boolean dataClass) {
        requireMutable();
        this.dataClass = dataClass;
    }

    public void setSealed(boolean sealed) {
        requireMutable();
        this.sealed = sealed;
    }

    public void setHasExplicitLogic(boolean hasExplicitLogic) {
        requireMutable();
        this.hasExplicitLogic = hasExplicitLogic;
    }

    // --- Collection getters: eager unmodifiable views ----------------------

    private final Set<String> dependenciesView = Collections.unmodifiableSet(dependencies);

    public Set<String> getDependencies() {
        return dependenciesView;
    }

    private final Map<String, MethodMetrics> methodsView = Collections.unmodifiableMap(methods);

    public Map<String, MethodMetrics> getMethods() {
        return methodsView;
    }

    private final Set<String> attributesView = Collections.unmodifiableSet(attributes);

    public Set<String> getAttributes() {
        return attributesView;
    }

    private final Set<String> overriddenMethodsView = Collections.unmodifiableSet(overriddenMethods);

    public Set<String> getOverriddenMethods() {
        return overriddenMethodsView;
    }

    private final Set<String> usedParentMembersView = Collections.unmodifiableSet(usedParentMembers);

    public Set<String> getUsedParentMembers() {
        return usedParentMembersView;
    }

    private final Set<String> typeParameterFqnsView = Collections.unmodifiableSet(typeParameterFqns);

    public Set<String> getTypeParameterFqns() {
        return typeParameterFqnsView;
    }

    private final Set<String> extensionReceiverTypesView = Collections.unmodifiableSet(extensionReceiverTypes);

    public Set<String> getExtensionReceiverTypes() {
        return extensionReceiverTypesView;
    }

    private final Set<String> sealedHierarchyAncestorsView = Collections.unmodifiableSet(sealedHierarchyAncestors);

    public Set<String> getSealedHierarchyAncestors() {
        return sealedHierarchyAncestorsView;
    }

    // --- Adders (guarded) --------------------------------------------------

    public void addTypeParameterFqn(String fqn) {
        requireMutable();
        if (fqn != null && !fqn.isEmpty()) {
            this.typeParameterFqns.add(fqn);
        }
    }

    public void addExtensionReceiverType(String fqn) {
        requireMutable();
        if (fqn != null && !fqn.isEmpty()) {
            this.extensionReceiverTypes.add(fqn);
        }
    }

    public void addSealedHierarchyAncestor(String fqn) {
        requireMutable();
        if (fqn != null && !fqn.isEmpty()) {
            this.sealedHierarchyAncestors.add(fqn);
        }
    }

    public ClassMetrics(String fullyQualifiedName) {
        this.fullyQualifiedName = fullyQualifiedName;
    }

    public void addOverriddenMethod(String methodSignature) {
        requireMutable();
        this.overriddenMethods.add(methodSignature);
    }

    public int getNumberOfOverriddenMethods() {
        return overriddenMethods.size();
    }

    public void addUsedParentMember(String memberName) {
        requireMutable();
        this.usedParentMembers.add(memberName);
    }

    public int getNumberOfUsedParentMembers() {
        return usedParentMembers.size();
    }

    public void addMethod(MethodMetrics methodMetrics) {
        requireMutable();
        this.methods.put(methodMetrics.getSignature(), methodMetrics);
    }

    /**
     * Aggregated class-level count of Kotlin/Java callable references
     * ({@code Klass::method}) across all declared methods. Returns the sum
     * of {@code MethodMetrics#getNumberOfCallableReferences()} across every
     * method on this class.
     *
     * @return the total number of callable references
     */
    public int getNumberOfCallableReferences() {
        return methods.values().stream()
                .mapToInt(MethodMetrics::getNumberOfCallableReferences)
                .sum();
    }

    public int getNumberOfMethods() {
        return methods.size();
    }

    public int getWeightedMethodCount() {
        return methods.values().stream()
                .mapToInt(MethodMetrics::getCyclomaticComplexity)
                .sum();
    }

    public int getNumberOfAccessorMethods() {
        return (int) methods.values().stream().filter(MethodMetrics::isAccessor).count();
    }

    public void addAttribute(String attributeName, boolean isPublic) {
        requireMutable();
        this.attributes.add(attributeName);
        this.numberOfAttributes++;
        if (isPublic) {
            this.numberOfPublicAttributes++;
        }
    }

    public void addDependency(String className) {
        requireMutable();
        this.dependencies.add(className);
    }

    public int getCouplingBetweenObjects() {
        return dependencies.size();
    }

    public double getWeightOfClass() {
        int numMethods = getNumberOfMethods();
        if (numMethods == 0) {
            return 0.0;
        }
        return (double) (numMethods - getNumberOfAccessorMethods()) / numMethods;
    }

    public void calculateAccessToForeignData() {
        requireMutable();
        Set<String> foreignClasses = new HashSet<>();
        for (MethodMetrics method : methods.values()) {
            foreignClasses.addAll(method.getAccessedForeignClasses());
        }
        foreignClasses.remove(this.fullyQualifiedName);
        this.accessToForeignData = foreignClasses.size();
    }

    public void calculateTightClassCohesion() {
        requireMutable();
        int numMethods = getNumberOfMethods();
        if (numMethods <= 1) {
            this.tightClassCohesion = 0.0;
            return;
        }

        int directConnections = 0;
        int maxConnections = (numMethods * (numMethods - 1)) / 2;

        if (maxConnections == 0) {
            this.tightClassCohesion = 0.0;
            return;
        }

        MethodMetrics[] methodArray = methods.values().toArray(new MethodMetrics[0]);
        for (int i = 0; i < methodArray.length; i++) {
            for (int j = i + 1; j < methodArray.length; j++) {
                Set<String> intersection = new HashSet<>(methodArray[i].getAccessedVariables());
                intersection.retainAll(methodArray[j].getAccessedVariables());
                if (!intersection.isEmpty()) {
                    directConnections++;
                }
            }
        }

        this.tightClassCohesion = (double) directConnections / maxConnections;
    }
}
