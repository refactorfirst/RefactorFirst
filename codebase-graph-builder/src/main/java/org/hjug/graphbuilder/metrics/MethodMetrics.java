package org.hjug.graphbuilder.metrics;

import java.util.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 * Per-method metrics accumulator. Mutable during the parse-time visitor
 * walk (single-threaded write phase); frozen in place alongside its owning
 * {@link ClassMetrics} by {@link GraphMetricsCollector#finalizeMetrics()}.
 * Once frozen, every setter/mutator rejects mutation with
 * {@link IllegalStateException} and collection getters return unmodifiable
 * views (review item #9). {@code @Data} is retained for
 * equals/hashCode/toString and the plain getters; the guarded hand-written
 * setters/adders below shadow the Lombok-generated ones.
 */
@Data
public class MethodMetrics {
    @Setter(AccessLevel.NONE)
    private String methodName;

    @Setter(AccessLevel.NONE)
    private String signature;

    private int linesOfCode;
    private int cyclomaticComplexity = 1;
    private int maxNestingDepth;
    private int numberOfParameters;
    /** CINT: distinct foreign methods called by this method (method invocations, not field accesses). */
    private Set<String> accessedVariables = new HashSet<>();

    private Set<String> accessedForeignClasses = new HashSet<>();
    private Set<String> accessedForeignAttributes = new HashSet<>();
    private Set<String> accessedOwnAttributes = new HashSet<>();
    private Set<String> calledForeignMethods = new HashSet<>();
    /** Distinct classes that own the foreign methods called by this method (for CDISP numerator). */
    private Set<String> calledForeignMethodClasses = new HashSet<>();
    /** CM: distinct foreign methods that call this method (Changing Methods — incoming coupling). */
    private Set<String> changingMethods = new HashSet<>();
    /** CC: distinct foreign classes whose methods call this method (Changing Classes — incoming coupling). */
    private Set<String> changingClasses = new HashSet<>();
    /** Number of method/constructor references (`Klass::method`) this method body emits. */
    private int numberOfCallableReferences;
    /**
     * FQNs of classes referenced by this method's type-parameter bounds
     * (Kotlin generic methods, Java generic methods). Populated by the
     * metrics visitor while walking {@link org.openrewrite.java.tree.J.TypeParameter}
     * bounds on the method declaration.
     */
    private Set<String> typeParameterFqns = new HashSet<>();

    private boolean isAccessor;
    private boolean isConstructor;
    private List<String> normalizedBodyLines = new ArrayList<>();

    /**
     * Post-finalize publish flag, set by {@link #freeze()} (invoked only by
     * {@link GraphMetricsCollector#finalizeMetrics()} alongside the owning
     * {@link ClassMetrics}). Once {@code true}, every setter/mutator rejects
     * mutation.
     */
    private volatile boolean finalized;

    private void requireMutable() {
        if (finalized) {
            throw new IllegalStateException("MethodMetrics is final for signature=" + signature);
        }
    }

    /** Package-private freeze entry point, called by {@link GraphMetricsCollector}. Idempotent. */
    void freeze() {
        finalized = true;
    }

    public MethodMetrics(String methodName, String signature) {
        this.methodName = methodName;
        this.signature = signature;
    }

    // --- Guarded setters (shadow Lombok-generated ones) -------------------

    public void setLinesOfCode(int linesOfCode) {
        requireMutable();
        this.linesOfCode = linesOfCode;
    }

    public void setCyclomaticComplexity(int cyclomaticComplexity) {
        requireMutable();
        this.cyclomaticComplexity = cyclomaticComplexity;
    }

    public void setMaxNestingDepth(int maxNestingDepth) {
        requireMutable();
        this.maxNestingDepth = maxNestingDepth;
    }

    public void setNumberOfParameters(int numberOfParameters) {
        requireMutable();
        this.numberOfParameters = numberOfParameters;
    }

    public void setAccessor(boolean isAccessor) {
        requireMutable();
        this.isAccessor = isAccessor;
    }

    public void setConstructor(boolean isConstructor) {
        requireMutable();
        this.isConstructor = isConstructor;
    }

    public void setNormalizedBodyLines(List<String> normalizedBodyLines) {
        requireMutable();
        this.normalizedBodyLines = normalizedBodyLines;
    }

    // --- Guarded mutators --------------------------------------------------

    public void incrementComplexity() {
        requireMutable();
        this.cyclomaticComplexity++;
    }

    public void updateMaxNesting(int depth) {
        requireMutable();
        if (depth > this.maxNestingDepth) {
            this.maxNestingDepth = depth;
        }
    }

    public void addAccessedVariable(String variable) {
        requireMutable();
        this.accessedVariables.add(variable);
    }

    public void addAccessedForeignClass(String className) {
        requireMutable();
        this.accessedForeignClasses.add(className);
    }

    public void addAccessedForeignAttribute(String qualifiedAttributeName) {
        requireMutable();
        this.accessedForeignAttributes.add(qualifiedAttributeName);
    }

    public void addAccessedOwnAttribute(String attributeName) {
        requireMutable();
        this.accessedOwnAttributes.add(attributeName);
    }

    public void addCalledForeignMethod(String qualifiedSignature) {
        requireMutable();
        this.calledForeignMethods.add(qualifiedSignature);
    }

    public void addCalledForeignMethodClass(String className) {
        requireMutable();
        this.calledForeignMethodClasses.add(className);
    }

    public void addChangingMethod(String callerMethodSig) {
        requireMutable();
        this.changingMethods.add(callerMethodSig);
    }

    public void addChangingClass(String callerClassFqn) {
        requireMutable();
        this.changingClasses.add(callerClassFqn);
    }

    public void incrementCallableReferences() {
        requireMutable();
        this.numberOfCallableReferences++;
    }

    public void addTypeParameterFqn(String fqn) {
        requireMutable();
        if (fqn != null && !fqn.isEmpty()) {
            this.typeParameterFqns.add(fqn);
        }
    }

    // --- Collection getters: lazy-cached unmodifiable views ----------------

    private Set<String> accessedVariablesView;

    public Set<String> getAccessedVariables() {
        Set<String> v = accessedVariablesView;
        if (v == null) {
            v = Collections.unmodifiableSet(accessedVariables);
            accessedVariablesView = v;
        }
        return v;
    }

    private Set<String> accessedForeignClassesView;

    public Set<String> getAccessedForeignClasses() {
        Set<String> v = accessedForeignClassesView;
        if (v == null) {
            v = Collections.unmodifiableSet(accessedForeignClasses);
            accessedForeignClassesView = v;
        }
        return v;
    }

    private Set<String> accessedForeignAttributesView;

    public Set<String> getAccessedForeignAttributes() {
        Set<String> v = accessedForeignAttributesView;
        if (v == null) {
            v = Collections.unmodifiableSet(accessedForeignAttributes);
            accessedForeignAttributesView = v;
        }
        return v;
    }

    private Set<String> accessedOwnAttributesView;

    public Set<String> getAccessedOwnAttributes() {
        Set<String> v = accessedOwnAttributesView;
        if (v == null) {
            v = Collections.unmodifiableSet(accessedOwnAttributes);
            accessedOwnAttributesView = v;
        }
        return v;
    }

    private Set<String> calledForeignMethodsView;

    public Set<String> getCalledForeignMethods() {
        Set<String> v = calledForeignMethodsView;
        if (v == null) {
            v = Collections.unmodifiableSet(calledForeignMethods);
            calledForeignMethodsView = v;
        }
        return v;
    }

    private Set<String> calledForeignMethodClassesView;

    public Set<String> getCalledForeignMethodClasses() {
        Set<String> v = calledForeignMethodClassesView;
        if (v == null) {
            v = Collections.unmodifiableSet(calledForeignMethodClasses);
            calledForeignMethodClassesView = v;
        }
        return v;
    }

    private Set<String> changingMethodsView;

    public Set<String> getChangingMethods() {
        Set<String> v = changingMethodsView;
        if (v == null) {
            v = Collections.unmodifiableSet(changingMethods);
            changingMethodsView = v;
        }
        return v;
    }

    private Set<String> changingClassesView;

    public Set<String> getChangingClasses() {
        Set<String> v = changingClassesView;
        if (v == null) {
            v = Collections.unmodifiableSet(changingClasses);
            changingClassesView = v;
        }
        return v;
    }

    private Set<String> typeParameterFqnsView;

    public Set<String> getTypeParameterFqns() {
        Set<String> v = typeParameterFqnsView;
        if (v == null) {
            v = Collections.unmodifiableSet(typeParameterFqns);
            typeParameterFqnsView = v;
        }
        return v;
    }

    private List<String> normalizedBodyLinesView;

    public List<String> getNormalizedBodyLines() {
        List<String> v = normalizedBodyLinesView;
        if (v == null) {
            v = Collections.unmodifiableList(normalizedBodyLines);
            normalizedBodyLinesView = v;
        }
        return v;
    }

    // --- Derived counts (read-only) ---------------------------------------

    /** CM: number of distinct foreign methods that call this method. */
    public int getChangingMethodCount() {
        return changingMethods.size();
    }

    /** CC: number of distinct foreign classes whose methods call this method. */
    public int getChangingClassCount() {
        return changingClasses.size();
    }

    public int getNumberOfAccessedVariables() {
        return accessedVariables.size();
    }

    /** ATFD (method-level): number of distinct foreign class attributes accessed by this method. */
    public int getAccessToForeignData() {
        return accessedForeignAttributes.size();
    }

    /**
     * CINT: Coupling INTensity — number of distinct foreign methods called by this method.
     */
    public int getCouplingIntensity() {
        return calledForeignMethods.size();
    }

    /**
     * CDISP: Coupling DISPersion — ratio of distinct provider classes to CINT.
     * Low CDISP = intensive (concentrated in few classes); high CDISP = dispersed.
     * Returns 0.0 when CINT is 0.
     */
    public double getCouplingDispersion() {
        int cint = getCouplingIntensity();
        if (cint == 0) {
            return 0.0;
        }
        return (double) calledForeignMethodClasses.size() / cint;
    }

    /**
     * LAA: Locality of Attribute Accesses.
     * = own-class attributes accessed / total class attributes accessed (own + foreign).
     * Returns 1.0 when the method accesses no class attributes at all.
     */
    public double getLocalityOfAttributeAccess() {
        int own = accessedOwnAttributes.size();
        int foreign = accessedForeignAttributes.size();
        int total = own + foreign;
        if (total == 0) {
            return 1.0;
        }
        return (double) own / total;
    }
}
