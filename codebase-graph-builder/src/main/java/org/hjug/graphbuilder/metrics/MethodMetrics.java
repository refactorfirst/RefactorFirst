package org.hjug.graphbuilder.metrics;

import java.util.HashSet;
import java.util.Set;
import lombok.Data;

@Data
public class MethodMetrics {
    private String methodName;
    private String signature;
    private int linesOfCode;
    private int cyclomaticComplexity = 1;
    private int maxNestingDepth;
    private int numberOfParameters;
    private Set<String> accessedVariables = new HashSet<>();
    private Set<String> accessedForeignClasses = new HashSet<>();
    private Set<String> accessedForeignAttributes = new HashSet<>();
    private Set<String> accessedOwnAttributes = new HashSet<>();
    /** CINT: distinct foreign methods called by this method (method invocations, not field accesses). */
    private Set<String> calledForeignMethods = new HashSet<>();
    /** Distinct classes that own the foreign methods called by this method (for CDISP numerator). */
    private Set<String> calledForeignMethodClasses = new HashSet<>();
    /** CM: distinct foreign methods that call this method (Changing Methods — incoming coupling). */
    private Set<String> changingMethods = new HashSet<>();
    /** CC: distinct foreign classes whose methods call this method (Changing Classes — incoming coupling). */
    private Set<String> changingClasses = new HashSet<>();

    private boolean isAccessor;
    private boolean isConstructor;

    public MethodMetrics(String methodName, String signature) {
        this.methodName = methodName;
        this.signature = signature;
    }

    public void incrementComplexity() {
        this.cyclomaticComplexity++;
    }

    public void updateMaxNesting(int depth) {
        if (depth > this.maxNestingDepth) {
            this.maxNestingDepth = depth;
        }
    }

    public void addAccessedVariable(String variable) {
        this.accessedVariables.add(variable);
    }

    public void addAccessedForeignClass(String className) {
        this.accessedForeignClasses.add(className);
    }

    public void addAccessedForeignAttribute(String qualifiedAttributeName) {
        this.accessedForeignAttributes.add(qualifiedAttributeName);
    }

    public void addAccessedOwnAttribute(String attributeName) {
        this.accessedOwnAttributes.add(attributeName);
    }

    public void addCalledForeignMethod(String qualifiedSignature) {
        this.calledForeignMethods.add(qualifiedSignature);
    }

    public void addCalledForeignMethodClass(String className) {
        this.calledForeignMethodClasses.add(className);
    }

    public void addChangingMethod(String callerMethodSig) {
        this.changingMethods.add(callerMethodSig);
    }

    public void addChangingClass(String callerClassFqn) {
        this.changingClasses.add(callerClassFqn);
    }

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
        if (cint == 0) return 0.0;
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
        if (total == 0) return 1.0;
        return (double) own / total;
    }
}
