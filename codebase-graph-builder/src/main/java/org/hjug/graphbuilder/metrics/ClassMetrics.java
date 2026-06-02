package org.hjug.graphbuilder.metrics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

public class ClassMetrics {
    @Getter
    @Setter
    private String sourceFilePath;

    @Getter
    @Setter
    private String fullyQualifiedName;

    @Getter
    @Setter
    private String className;

    @Getter
    @Setter
    private String packageName;

    @Getter
    @Setter
    private int linesOfCode;

    @Getter
    @Setter
    private int numberOfAttributes;

    @Getter
    @Setter
    private int numberOfPublicAttributes;

    @Getter
    @Setter
    private int accessToForeignData;

    @Getter
    @Setter
    private double tightClassCohesion;

    @Getter
    private Set<String> dependencies = new HashSet<>();

    @Getter
    private Map<String, MethodMetrics> methods = new HashMap<>();

    @Getter
    private Set<String> attributes = new HashSet<>();

    @Getter
    @Setter
    private String parentClass;

    @Getter
    private Set<String> overriddenMethods = new HashSet<>();

    @Getter
    @Setter
    private int numberOfProtectedMembers;

    @Getter
    private Set<String> usedParentMembers = new HashSet<>();

    public ClassMetrics(String fullyQualifiedName) {
        this.fullyQualifiedName = fullyQualifiedName;
    }

    public void addOverriddenMethod(String methodSignature) {
        this.overriddenMethods.add(methodSignature);
    }

    public int getNumberOfOverriddenMethods() {
        return overriddenMethods.size();
    }

    public void addUsedParentMember(String memberName) {
        this.usedParentMembers.add(memberName);
    }

    public int getNumberOfUsedParentMembers() {
        return usedParentMembers.size();
    }

    public void addMethod(MethodMetrics methodMetrics) {
        this.methods.put(methodMetrics.getSignature(), methodMetrics);
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
        this.attributes.add(attributeName);
        this.numberOfAttributes++;
        if (isPublic) {
            this.numberOfPublicAttributes++;
        }
    }

    public void addDependency(String className) {
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
        Set<String> foreignClasses = new HashSet<>();
        for (MethodMetrics method : methods.values()) {
            foreignClasses.addAll(method.getAccessedForeignClasses());
        }
        foreignClasses.remove(this.fullyQualifiedName);
        this.accessToForeignData = foreignClasses.size();
    }

    public void calculateTightClassCohesion() {
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
