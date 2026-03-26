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

    public int getNumberOfAccessedVariables() {
        return accessedVariables.size();
    }
}
