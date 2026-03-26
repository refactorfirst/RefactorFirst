package org.hjug.graphbuilder.metrics;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

public class DisharmonyDetector {

    private static final int FEW = 3;
    private static final int SEVERAL = 5;
    private static final int MANY = 7;
    private static final int HIGH = 20;
    private static final int VERY_HIGH_WMC = 10;
    private static final int VERY_HIGH_LOC = 50;
    private static final int VERY_HIGH_CYCLO = 5;
    private static final double ONE_THIRD = 0.33;

    @Data
    public static class ClassDisharmony {
        private final String className;
        private final String disharmonyType;
        private final String description;
        private final ClassMetrics metrics;
    }

    @Data
    public static class MethodDisharmony {
        private final String className;
        private final String methodSignature;
        private final String disharmonyType;
        private final String description;
        private final MethodMetrics metrics;
    }

    public List<ClassDisharmony> detectGodClasses(List<ClassMetrics> allMetrics) {
        List<ClassDisharmony> godClasses = new ArrayList<>();
        for (ClassMetrics metrics : allMetrics) {
            if (isGodClass(metrics)) {
                String description = String.format(
                        "God Class detected: ATFD=%d, WMC=%d, TCC=%.2f",
                        metrics.getAccessToForeignData(),
                        metrics.getWeightedMethodCount(),
                        metrics.getTightClassCohesion());
                godClasses.add(new ClassDisharmony(metrics.getFullyQualifiedName(), "God Class", description, metrics));
            }
        }
        return godClasses;
    }

    public List<ClassDisharmony> detectDataClasses(List<ClassMetrics> allMetrics) {
        List<ClassDisharmony> dataClasses = new ArrayList<>();
        for (ClassMetrics metrics : allMetrics) {
            if (isDataClass(metrics)) {
                String description = String.format(
                        "Data Class detected: WOC=%.2f, Public Attributes + Accessors=%d, WMC=%d",
                        metrics.getWeightOfClass(),
                        metrics.getNumberOfPublicAttributes() + metrics.getNumberOfAccessorMethods(),
                        metrics.getWeightedMethodCount());
                dataClasses.add(new ClassDisharmony(metrics.getFullyQualifiedName(), "Data Class", description, metrics));
            }
        }
        return dataClasses;
    }

    public List<MethodDisharmony> detectBrainMethods(List<ClassMetrics> allMetrics) {
        List<MethodDisharmony> brainMethods = new ArrayList<>();
        for (ClassMetrics classMetrics : allMetrics) {
            for (MethodMetrics methodMetrics : classMetrics.getMethods().values()) {
                if (isBrainMethod(methodMetrics)) {
                    String description = String.format(
                            "Brain Method detected: LOC=%d, CYCLO=%d, MAXNESTING=%d, NOAV=%d",
                            methodMetrics.getLinesOfCode(),
                            methodMetrics.getCyclomaticComplexity(),
                            methodMetrics.getMaxNestingDepth(),
                            methodMetrics.getNumberOfAccessedVariables());
                    brainMethods.add(new MethodDisharmony(
                            classMetrics.getFullyQualifiedName(),
                            methodMetrics.getSignature(),
                            "Brain Method",
                            description,
                            methodMetrics));
                }
            }
        }
        return brainMethods;
    }

    public List<ClassDisharmony> detectBrainClasses(List<ClassMetrics> allMetrics) {
        List<ClassDisharmony> brainClasses = new ArrayList<>();
        for (ClassMetrics metrics : allMetrics) {
            if (isBrainClass(metrics)) {
                int brainMethodCount = countBrainMethods(metrics);
                String description = String.format(
                        "Brain Class detected: Brain Methods=%d, LOC=%d, WMC=%d, TCC=%.2f",
                        brainMethodCount,
                        metrics.getLinesOfCode(),
                        metrics.getWeightedMethodCount(),
                        metrics.getTightClassCohesion());
                brainClasses.add(new ClassDisharmony(metrics.getFullyQualifiedName(), "Brain Class", description, metrics));
            }
        }
        return brainClasses;
    }

    public List<ClassDisharmony> detectFeatureEnvy(List<ClassMetrics> allMetrics) {
        List<ClassDisharmony> featureEnvyClasses = new ArrayList<>();
        for (ClassMetrics metrics : allMetrics) {
            if (hasFeatureEnvy(metrics)) {
                String description = String.format(
                        "Feature Envy detected: ATFD=%d (accessing many foreign classes)",
                        metrics.getAccessToForeignData());
                featureEnvyClasses.add(
                        new ClassDisharmony(metrics.getFullyQualifiedName(), "Feature Envy", description, metrics));
            }
        }
        return featureEnvyClasses;
    }

    public List<MethodDisharmony> detectLongMethods(List<ClassMetrics> allMetrics) {
        List<MethodDisharmony> longMethods = new ArrayList<>();
        for (ClassMetrics classMetrics : allMetrics) {
            for (MethodMetrics methodMetrics : classMetrics.getMethods().values()) {
                if (isLongMethod(methodMetrics)) {
                    String description = String.format("Long Method detected: LOC=%d", methodMetrics.getLinesOfCode());
                    longMethods.add(new MethodDisharmony(
                            classMetrics.getFullyQualifiedName(),
                            methodMetrics.getSignature(),
                            "Long Method",
                            description,
                            methodMetrics));
                }
            }
        }
        return longMethods;
    }

    public List<MethodDisharmony> detectIntensiveCoupling(List<ClassMetrics> allMetrics) {
        List<MethodDisharmony> intensivelyCoupledMethods = new ArrayList<>();
        for (ClassMetrics classMetrics : allMetrics) {
            for (MethodMetrics methodMetrics : classMetrics.getMethods().values()) {
                if (hasIntensiveCoupling(methodMetrics)) {
                    String description = String.format(
                            "Intensive Coupling detected: Accesses %d foreign classes with high coupling intensity",
                            methodMetrics.getAccessedForeignClasses().size());
                    intensivelyCoupledMethods.add(new MethodDisharmony(
                            classMetrics.getFullyQualifiedName(),
                            methodMetrics.getSignature(),
                            "Intensive Coupling",
                            description,
                            methodMetrics));
                }
            }
        }
        return intensivelyCoupledMethods;
    }

    public List<MethodDisharmony> detectDispersedCoupling(List<ClassMetrics> allMetrics) {
        List<MethodDisharmony> dispersedCoupledMethods = new ArrayList<>();
        for (ClassMetrics classMetrics : allMetrics) {
            for (MethodMetrics methodMetrics : classMetrics.getMethods().values()) {
                if (hasDispersedCoupling(methodMetrics)) {
                    String description = String.format(
                            "Dispersed Coupling detected: Accesses %d different foreign classes",
                            methodMetrics.getAccessedForeignClasses().size());
                    dispersedCoupledMethods.add(new MethodDisharmony(
                            classMetrics.getFullyQualifiedName(),
                            methodMetrics.getSignature(),
                            "Dispersed Coupling",
                            description,
                            methodMetrics));
                }
            }
        }
        return dispersedCoupledMethods;
    }

    public List<ClassDisharmony> detectShotgunSurgery(List<ClassMetrics> allMetrics) {
        List<ClassDisharmony> shotgunSurgeryClasses = new ArrayList<>();
        for (ClassMetrics metrics : allMetrics) {
            if (hasShotgunSurgery(metrics)) {
                String description = String.format(
                        "Shotgun Surgery detected: High coupling (ATFD=%d, NOM=%d) - changes require modifications across many classes",
                        metrics.getAccessToForeignData(), metrics.getNumberOfMethods());
                shotgunSurgeryClasses.add(
                        new ClassDisharmony(metrics.getFullyQualifiedName(), "Shotgun Surgery", description, metrics));
            }
        }
        return shotgunSurgeryClasses;
    }

    public List<ClassDisharmony> detectRefusedParentBequest(List<ClassMetrics> allMetrics) {
        List<ClassDisharmony> refusedBequestClasses = new ArrayList<>();
        for (ClassMetrics metrics : allMetrics) {
            if (hasRefusedParentBequest(metrics, allMetrics)) {
                String description = String.format(
                        "Refused Parent Bequest detected: Subclass ignores parent's protected members (Protected Members=%d, Overridden=%d)",
                        getParentProtectedMembers(metrics, allMetrics), metrics.getNumberOfOverriddenMethods());
                refusedBequestClasses.add(new ClassDisharmony(
                        metrics.getFullyQualifiedName(), "Refused Parent Bequest", description, metrics));
            }
        }
        return refusedBequestClasses;
    }

    public List<ClassDisharmony> detectTraditionBreaker(List<ClassMetrics> allMetrics) {
        List<ClassDisharmony> traditionBreakerClasses = new ArrayList<>();
        for (ClassMetrics metrics : allMetrics) {
            if (isTraditionBreaker(metrics, allMetrics)) {
                String description = String.format(
                        "Tradition Breaker detected: Subclass overrides many parent methods (Overridden=%d, NOM=%d)",
                        metrics.getNumberOfOverriddenMethods(), metrics.getNumberOfMethods());
                traditionBreakerClasses.add(
                        new ClassDisharmony(metrics.getFullyQualifiedName(), "Tradition Breaker", description, metrics));
            }
        }
        return traditionBreakerClasses;
    }

    public boolean isGodClass(ClassMetrics metrics) {
        return metrics.getAccessToForeignData() > FEW
                && metrics.getWeightedMethodCount() >= VERY_HIGH_WMC
                && metrics.getTightClassCohesion() < ONE_THIRD;
    }

    public boolean isDataClass(ClassMetrics metrics) {
        double woc = metrics.getWeightOfClass();
        int publicAccessors = metrics.getNumberOfPublicAttributes() + metrics.getNumberOfAccessorMethods();
        return woc < ONE_THIRD && publicAccessors > FEW && metrics.getWeightedMethodCount() < HIGH;
    }

    public boolean isBrainMethod(MethodMetrics metrics) {
        return metrics.getLinesOfCode() > VERY_HIGH_LOC
                && metrics.getCyclomaticComplexity() > VERY_HIGH_CYCLO
                && metrics.getMaxNestingDepth() > FEW
                && metrics.getNumberOfAccessedVariables() > SEVERAL;
    }

    public boolean hasFeatureEnvy(ClassMetrics metrics) {
        return metrics.getAccessToForeignData() > MANY;
    }

    public boolean isLongMethod(MethodMetrics metrics) {
        return metrics.getLinesOfCode() > VERY_HIGH_LOC;
    }

    public boolean isComplexMethod(MethodMetrics metrics) {
        return metrics.getCyclomaticComplexity() > VERY_HIGH_CYCLO;
    }

    public boolean isBrainClass(ClassMetrics metrics) {
        int brainMethodCount = countBrainMethods(metrics);
        return brainMethodCount >= 1
                && metrics.getLinesOfCode() > VERY_HIGH_LOC
                && metrics.getWeightedMethodCount() >= VERY_HIGH_WMC
                && metrics.getTightClassCohesion() < ONE_THIRD;
    }

    public boolean hasIntensiveCoupling(MethodMetrics metrics) {
        int couplingIntensity = metrics.getAccessedForeignClasses().size();
        return couplingIntensity > FEW && couplingIntensity <= MANY;
    }

    public boolean hasDispersedCoupling(MethodMetrics metrics) {
        int couplingDispersion = metrics.getAccessedForeignClasses().size();
        return couplingDispersion > MANY;
    }

    public boolean hasShotgunSurgery(ClassMetrics metrics) {
        return metrics.getAccessToForeignData() > MANY && metrics.getNumberOfMethods() > SEVERAL;
    }

    public boolean hasRefusedParentBequest(ClassMetrics metrics, List<ClassMetrics> allMetrics) {
        if (metrics.getParentClass() == null) {
            return false;
        }
        int parentProtectedMembers = getParentProtectedMembers(metrics, allMetrics);
        int overriddenMethods = metrics.getNumberOfOverriddenMethods();
        return parentProtectedMembers > FEW && overriddenMethods < FEW;
    }

    public boolean isTraditionBreaker(ClassMetrics metrics, List<ClassMetrics> allMetrics) {
        if (metrics.getParentClass() == null) {
            return false;
        }
        int parentMethods = getParentMethodCount(metrics, allMetrics);
        int overriddenMethods = metrics.getNumberOfOverriddenMethods();
        if (parentMethods == 0) {
            return false;
        }
        double overrideRatio = (double) overriddenMethods / parentMethods;
        return overriddenMethods > FEW && overrideRatio > 0.5;
    }

    private int getParentProtectedMembers(ClassMetrics metrics, List<ClassMetrics> allMetrics) {
        if (metrics.getParentClass() == null) {
            return 0;
        }
        for (ClassMetrics parent : allMetrics) {
            if (parent.getFullyQualifiedName().equals(metrics.getParentClass())) {
                return parent.getNumberOfProtectedMembers();
            }
        }
        return 0;
    }

    private int getParentMethodCount(ClassMetrics metrics, List<ClassMetrics> allMetrics) {
        if (metrics.getParentClass() == null) {
            return 0;
        }
        for (ClassMetrics parent : allMetrics) {
            if (parent.getFullyQualifiedName().equals(metrics.getParentClass())) {
                return parent.getNumberOfMethods();
            }
        }
        return 0;
    }

    private int countBrainMethods(ClassMetrics metrics) {
        int count = 0;
        for (MethodMetrics method : metrics.getMethods().values()) {
            if (isBrainMethod(method)) {
                count++;
            }
        }
        return count;
    }
}
