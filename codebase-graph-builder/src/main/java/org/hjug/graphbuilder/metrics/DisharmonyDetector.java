package org.hjug.graphbuilder.metrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import org.hjug.graphbuilder.metrics.DisharmonyMetric.Direction;

public class DisharmonyDetector {

    // Linguistic quantifiers (Lanza & Marinescu, Table 2.4)
    private static final int FEW = 5;
    private static final int SHORT_MEMORY_CAP = 7;
    private static final int MANY =
            SHORT_MEMORY_CAP; // Table 2.4: same as SHORT_MEMORY_CAP — "many" begins above short-term memory limit
    private static final int SHALLOW = 1;

    // Fraction thresholds (Table 2.3)
    private static final double ONE_QUARTER = 0.25;
    private static final double ONE_THIRD = 0.33;
    private static final double HALF = 0.5;
    private static final double TWO_THIRDS = 0.67;

    // WMC thresholds (Table 2.2)
    private static final int WMC_AVERAGE = 14;
    private static final int WMC_HIGH = 31;
    private static final int WMC_VERY_HIGH = 47;

    // LOC class-level thresholds (Table 2.2)
    private static final int LOC_CLASS_VERY_HIGH = 195;

    // NOM thresholds (Table 2.2)
    private static final int NOM_AVERAGE = 7;
    private static final int NOM_HIGH = 12;

    // AMW thresholds (Table 2.2)
    private static final double AMW_AVERAGE = 2.0;

    // Brain Method thresholds (Lanza & Marinescu, Chapter 5)
    private static final int BRAIN_METHOD_LOC = 65; // LOC_CLASS_HIGH / 2 = 130 / 2 = 65
    private static final int CYCLO_HIGH = 4; // ceiling(AMW_HIGH = 3.1)
    private static final int MAXNESTING_DEEP = 5; // book-defined threshold
    private static final int NOAV_MANY = SHORT_MEMORY_CAP; // Table 2.4: MANY = Short Memory Capacity = 7

    // God Class thresholds (Lanza & Marinescu, Chapter 5)
    private static final int GOD_CLASS_ATFD_FEW = 5;
    private static final int GOD_CLASS_WMC_VERY_HIGH = 47;

    @Data
    public static class ClassDisharmony {
        private final String className;
        private final String disharmonyType;
        private final String description;
        private final ClassMetrics metrics;
        private final List<DisharmonyMetric> metricValues;
    }

    @Data
    public static class MethodDisharmony {
        private final String className;
        private final String methodSignature;
        private final String disharmonyType;
        private final String description;
        private final MethodMetrics metrics;
        private final List<DisharmonyMetric> metricValues;
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
                List<DisharmonyMetric> metricValues = List.of(
                        new DisharmonyMetric("ATFD", metrics.getAccessToForeignData(), Direction.ASCENDING),
                        new DisharmonyMetric("WMC", metrics.getWeightedMethodCount(), Direction.ASCENDING),
                        new DisharmonyMetric("TCC", metrics.getTightClassCohesion(), Direction.DESCENDING));
                godClasses.add(new ClassDisharmony(
                        metrics.getFullyQualifiedName(),
                        DisharmonyTypes.GOD_CLASS,
                        description,
                        metrics,
                        metricValues));
            }
        }
        return godClasses;
    }

    public List<ClassDisharmony> detectDataClasses(List<ClassMetrics> allMetrics) {
        List<ClassDisharmony> dataClasses = new ArrayList<>();
        for (ClassMetrics metrics : allMetrics) {
            if (isDataClass(metrics)) {
                double woc = metrics.getWeightOfClass();
                int publicAccessors = metrics.getNumberOfPublicAttributes() + metrics.getNumberOfAccessorMethods();
                int wmc = metrics.getWeightedMethodCount();
                String description = String.format(
                        "Data Class detected: WOC=%.2f, Public Attributes + Accessors=%d, WMC=%d",
                        woc, publicAccessors, wmc);
                List<DisharmonyMetric> metricValues = List.of(
                        new DisharmonyMetric("WOC", woc, Direction.DESCENDING),
                        new DisharmonyMetric("PublicAttrsAndAccessors", publicAccessors, Direction.ASCENDING),
                        new DisharmonyMetric("WMC", wmc, Direction.DESCENDING));
                dataClasses.add(new ClassDisharmony(
                        metrics.getFullyQualifiedName(),
                        DisharmonyTypes.DATA_CLASS,
                        description,
                        metrics,
                        metricValues));
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
                    List<DisharmonyMetric> metricValues = List.of(
                            new DisharmonyMetric("LOC", methodMetrics.getLinesOfCode(), Direction.ASCENDING),
                            new DisharmonyMetric("CYCLO", methodMetrics.getCyclomaticComplexity(), Direction.ASCENDING),
                            new DisharmonyMetric("MAXNESTING", methodMetrics.getMaxNestingDepth(), Direction.ASCENDING),
                            new DisharmonyMetric(
                                    "NOAV", methodMetrics.getNumberOfAccessedVariables(), Direction.ASCENDING));
                    brainMethods.add(new MethodDisharmony(
                            classMetrics.getFullyQualifiedName(),
                            methodMetrics.getSignature(),
                            DisharmonyTypes.BRAIN_METHOD,
                            description,
                            methodMetrics,
                            metricValues));
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
                List<DisharmonyMetric> metricValues = List.of(
                        new DisharmonyMetric("BrainMethods", brainMethodCount, Direction.ASCENDING),
                        new DisharmonyMetric("LOC", metrics.getLinesOfCode(), Direction.ASCENDING),
                        new DisharmonyMetric("WMC", metrics.getWeightedMethodCount(), Direction.ASCENDING),
                        new DisharmonyMetric("TCC", metrics.getTightClassCohesion(), Direction.DESCENDING));
                brainClasses.add(new ClassDisharmony(
                        metrics.getFullyQualifiedName(),
                        DisharmonyTypes.BRAIN_CLASS,
                        description,
                        metrics,
                        metricValues));
            }
        }
        return brainClasses;
    }

    public List<MethodDisharmony> detectFeatureEnvy(List<ClassMetrics> allMetrics) {
        List<MethodDisharmony> featureEnvyMethods = new ArrayList<>();
        for (ClassMetrics classMetrics : allMetrics) {
            for (MethodMetrics methodMetrics : classMetrics.getMethods().values()) {
                if (hasFeatureEnvy(methodMetrics)) {
                    int fdp = methodMetrics.getAccessedForeignClasses().size();
                    String description = String.format(
                            "Feature Envy detected: ATFD=%d, LAA=%.2f, FDP=%d",
                            methodMetrics.getAccessToForeignData(), methodMetrics.getLocalityOfAttributeAccess(), fdp);
                    List<DisharmonyMetric> metricValues = List.of(
                            new DisharmonyMetric("ATFD", methodMetrics.getAccessToForeignData(), Direction.ASCENDING),
                            new DisharmonyMetric(
                                    "LAA", methodMetrics.getLocalityOfAttributeAccess(), Direction.DESCENDING),
                            new DisharmonyMetric("FDP", fdp, Direction.DESCENDING));
                    featureEnvyMethods.add(new MethodDisharmony(
                            classMetrics.getFullyQualifiedName(),
                            methodMetrics.getSignature(),
                            DisharmonyTypes.FEATURE_ENVY,
                            description,
                            methodMetrics,
                            metricValues));
                }
            }
        }
        return featureEnvyMethods;
    }

    // !Not used in production code for metric capture
    public List<MethodDisharmony> detectLongMethods(List<ClassMetrics> allMetrics) {
        List<MethodDisharmony> longMethods = new ArrayList<>();
        for (ClassMetrics classMetrics : allMetrics) {
            for (MethodMetrics methodMetrics : classMetrics.getMethods().values()) {
                if (isLongMethod(methodMetrics)) {
                    String description = String.format("Long Method detected: LOC=%d", methodMetrics.getLinesOfCode());
                    List<DisharmonyMetric> metricValues =
                            List.of(new DisharmonyMetric("LOC", methodMetrics.getLinesOfCode(), Direction.ASCENDING));
                    longMethods.add(new MethodDisharmony(
                            classMetrics.getFullyQualifiedName(),
                            methodMetrics.getSignature(),
                            DisharmonyTypes.LONG_METHOD,
                            description,
                            methodMetrics,
                            metricValues));
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
                    int cint = methodMetrics.getCouplingIntensity();
                    double cdisp = methodMetrics.getCouplingDispersion();
                    int nest = methodMetrics.getMaxNestingDepth();
                    String description = String.format(
                            "Intensive Coupling detected: CINT=%d, CDISP=%.2f (calls concentrated in few classes)",
                            cint, cdisp);
                    List<DisharmonyMetric> metricValues = List.of(
                            new DisharmonyMetric("CINT", cint, Direction.ASCENDING),
                            new DisharmonyMetric("CDISP", cdisp, Direction.DESCENDING),
                            new DisharmonyMetric("MAXNESTING", nest, Direction.ASCENDING));
                    intensivelyCoupledMethods.add(new MethodDisharmony(
                            classMetrics.getFullyQualifiedName(),
                            methodMetrics.getSignature(),
                            DisharmonyTypes.INTENSIVE_COUPLING,
                            description,
                            methodMetrics,
                            metricValues));
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
                    int cint = methodMetrics.getCouplingIntensity();
                    double cdisp = methodMetrics.getCouplingDispersion();
                    int nest = methodMetrics.getMaxNestingDepth();
                    String description = String.format(
                            "Dispersed Coupling detected: CINT=%d, CDISP=%.2f (calls spread across many classes)",
                            cint, cdisp);
                    List<DisharmonyMetric> metricValues = List.of(
                            new DisharmonyMetric("CINT", cint, Direction.ASCENDING),
                            new DisharmonyMetric("CDISP", cdisp, Direction.ASCENDING),
                            new DisharmonyMetric("MAXNESTING", nest, Direction.ASCENDING));
                    dispersedCoupledMethods.add(new MethodDisharmony(
                            classMetrics.getFullyQualifiedName(),
                            methodMetrics.getSignature(),
                            DisharmonyTypes.DISPERSED_COUPLING,
                            description,
                            methodMetrics,
                            metricValues));
                }
            }
        }
        return dispersedCoupledMethods;
    }

    public List<MethodDisharmony> detectShotgunSurgery(List<ClassMetrics> allMetrics) {
        List<MethodDisharmony> shotgunSurgeryMethods = new ArrayList<>();
        for (ClassMetrics classMetrics : allMetrics) {
            for (MethodMetrics methodMetrics : classMetrics.getMethods().values()) {
                if (hasShotgunSurgery(methodMetrics)) {
                    int cm = methodMetrics.getChangingMethodCount();
                    int cc = methodMetrics.getChangingClassCount();
                    String description = String.format(
                            "Shotgun Surgery detected: CM=%d, CC=%d (called by many methods from many classes)",
                            cm, cc);
                    List<DisharmonyMetric> metricValues = List.of(
                            new DisharmonyMetric("CM", cm, Direction.ASCENDING),
                            new DisharmonyMetric("CC", cc, Direction.ASCENDING));
                    shotgunSurgeryMethods.add(new MethodDisharmony(
                            classMetrics.getFullyQualifiedName(),
                            methodMetrics.getSignature(),
                            DisharmonyTypes.SHOTGUN_SURGERY,
                            description,
                            methodMetrics,
                            metricValues));
                }
            }
        }
        return shotgunSurgeryMethods;
    }

    public List<ClassDisharmony> detectRefusedParentBequest(List<ClassMetrics> allMetrics) {
        List<ClassDisharmony> refusedBequestClasses = new ArrayList<>();
        for (ClassMetrics metrics : allMetrics) {
            if (hasRefusedParentBequest(metrics, allMetrics)) {
                int parentProtected = getParentProtectedMembers(metrics, allMetrics);
                int usedParent = metrics.getNumberOfUsedParentMembers();
                int overridden = metrics.getNumberOfOverriddenMethods();
                int descNom = metrics.getNumberOfMethods();
                double bur = parentProtected > 0 ? (double) usedParent / parentProtected : 0.0;
                double bovr = descNom > 0 ? (double) overridden / descNom : 0.0;
                int wmc = metrics.getWeightedMethodCount();
                double amw = descNom > 0 ? (double) wmc / descNom : 0.0;
                String description = String.format(
                        "Refused Parent Bequest detected: NProtM=%d, BUR=%.2f, BOvR=%.2f, NOM=%d, AMW=%.2f, WMC=%d",
                        parentProtected, bur, bovr, descNom, amw, wmc);
                List<DisharmonyMetric> metricValues = List.of(
                        new DisharmonyMetric("NProtM", parentProtected, Direction.ASCENDING),
                        new DisharmonyMetric("BUR", bur, Direction.DESCENDING),
                        new DisharmonyMetric("BOvR", bovr, Direction.DESCENDING),
                        new DisharmonyMetric("NOM", descNom, Direction.ASCENDING),
                        new DisharmonyMetric("AMW", amw, Direction.ASCENDING),
                        new DisharmonyMetric("WMC", wmc, Direction.ASCENDING));
                refusedBequestClasses.add(new ClassDisharmony(
                        metrics.getFullyQualifiedName(),
                        DisharmonyTypes.REFUSED_PARENT_BEQUEST,
                        description,
                        metrics,
                        metricValues));
            }
        }
        return refusedBequestClasses;
    }

    public List<ClassDisharmony> detectTraditionBreaker(List<ClassMetrics> allMetrics) {
        List<ClassDisharmony> traditionBreakerClasses = new ArrayList<>();
        for (ClassMetrics metrics : allMetrics) {
            if (isTraditionBreaker(metrics, allMetrics)) {
                int nom = metrics.getNumberOfMethods();
                int nas = nom - metrics.getNumberOfOverriddenMethods();
                double pnas = nom > 0 ? (double) nas / nom : 0.0;
                int wmc = metrics.getWeightedMethodCount();
                double amw = nom > 0 ? (double) wmc / nom : 0.0;
                String description = String.format(
                        "Tradition Breaker detected: NAS=%d, PNAS=%.2f, NOM=%d, AMW=%.2f, WMC=%d, Overridden=%d",
                        nas, pnas, nom, amw, wmc, metrics.getNumberOfOverriddenMethods());
                List<DisharmonyMetric> metricValues = List.of(
                        new DisharmonyMetric("NAS", nas, Direction.ASCENDING),
                        new DisharmonyMetric("PNAS", pnas, Direction.ASCENDING),
                        new DisharmonyMetric("NOM", nom, Direction.ASCENDING),
                        new DisharmonyMetric("AMW", amw, Direction.ASCENDING),
                        new DisharmonyMetric("WMC", wmc, Direction.ASCENDING),
                        new DisharmonyMetric(
                                "Overridden", metrics.getNumberOfOverriddenMethods(), Direction.ASCENDING));
                traditionBreakerClasses.add(new ClassDisharmony(
                        metrics.getFullyQualifiedName(),
                        DisharmonyTypes.TRADITION_BREAKER,
                        description,
                        metrics,
                        metricValues));
            }
        }
        return traditionBreakerClasses;
    }

    public boolean isGodClass(ClassMetrics metrics) {
        return metrics.getAccessToForeignData() > GOD_CLASS_ATFD_FEW
                && metrics.getWeightedMethodCount() >= GOD_CLASS_WMC_VERY_HIGH
                && metrics.getTightClassCohesion() < ONE_THIRD;
    }

    public boolean isDataClass(ClassMetrics metrics) {
        double woc = metrics.getWeightOfClass();
        int publicAccessors = metrics.getNumberOfPublicAttributes() + metrics.getNumberOfAccessorMethods();
        int wmc = metrics.getWeightedMethodCount();
        return woc < ONE_THIRD
                && ((publicAccessors > FEW && wmc < WMC_HIGH) || (publicAccessors > MANY && wmc < WMC_VERY_HIGH));
    }

    public boolean isBrainMethod(MethodMetrics metrics) {
        return metrics.getLinesOfCode() > BRAIN_METHOD_LOC
                && metrics.getCyclomaticComplexity() >= CYCLO_HIGH
                && metrics.getMaxNestingDepth() >= MAXNESTING_DEEP
                && metrics.getNumberOfAccessedVariables() > NOAV_MANY;
    }

    /**
     * Feature Envy (Fig. 5.4): method accesses more foreign data than local data.
     * ATFD > FEW AND LAA < ONE_THIRD AND FDP <= FEW
     */
    public boolean hasFeatureEnvy(MethodMetrics metrics) {
        return metrics.getAccessToForeignData() > FEW
                && metrics.getLocalityOfAttributeAccess() < ONE_THIRD
                && metrics.getAccessedForeignClasses().size() <= FEW;
    }

    public boolean isLongMethod(MethodMetrics metrics) {
        return metrics.getLinesOfCode() > BRAIN_METHOD_LOC;
    }

    public boolean isComplexMethod(MethodMetrics metrics) {
        return metrics.getCyclomaticComplexity() >= CYCLO_HIGH;
    }

    public boolean isBrainClass(ClassMetrics metrics) {
        // Book: God Classes are excluded a priori (p.97 footnote 4)
        if (isGodClass(metrics)) {
            return false;
        }
        int brainMethodCount = countBrainMethods(metrics);
        if (brainMethodCount == 0) {
            return false;
        }
        // Fig. 5.12 Term 3 (common filter): WMC >= VERY_HIGH AND TCC < HALF
        boolean veryComplexAndNonCohesive =
                metrics.getWeightedMethodCount() >= WMC_VERY_HIGH && metrics.getTightClassCohesion() < HALF;
        if (!veryComplexAndNonCohesive) {
            return false;
        }
        // Fig. 5.12 Term 1: >1 Brain Methods AND LOC >= VERY_HIGH (195)
        if (brainMethodCount > 1 && metrics.getLinesOfCode() >= LOC_CLASS_VERY_HIGH) {
            return true;
        }
        // Fig. 5.12 Term 2: exactly 1 Brain Method AND LOC >= 2xVERY_HIGH (390) AND WMC >= 2xVERY_HIGH (94)
        return brainMethodCount == 1
                && metrics.getLinesOfCode() >= LOC_CLASS_VERY_HIGH * 2
                && metrics.getWeightedMethodCount() >= WMC_VERY_HIGH * 2;
    }

    /**
     * Intensive Coupling (Fig. 6.3/6.4): method calls many methods concentrated in few classes.
     * Branch 1: CINT > SHORT_MEMORY_CAP AND CDISP < HALF
     * Branch 2: CINT > FEW AND CDISP < ONE_QUARTER
     * Both branches require MAXNESTING > SHALLOW.
     */
    public boolean hasIntensiveCoupling(MethodMetrics metrics) {
        int cint = metrics.getCouplingIntensity();
        double cdisp = metrics.getCouplingDispersion();
        boolean intensivelyCoupled = (cint > SHORT_MEMORY_CAP && cdisp < HALF) || (cint > FEW && cdisp < ONE_QUARTER);
        return intensivelyCoupled && metrics.getMaxNestingDepth() > SHALLOW;
    }

    /**
     * Dispersed Coupling (Fig. 6.9/6.10): method calls many methods spread across many classes.
     * CINT > SHORT_MEMORY_CAP AND CDISP >= HALF AND MAXNESTING > SHALLOW
     */
    public boolean hasDispersedCoupling(MethodMetrics metrics) {
        int cint = metrics.getCouplingIntensity();
        double cdisp = metrics.getCouplingDispersion();
        return cint > SHORT_MEMORY_CAP && cdisp >= HALF && metrics.getMaxNestingDepth() > SHALLOW;
    }

    /**
     * Shotgun Surgery (Fig. 6.14): method is called by too many methods from too many classes.
     * CM > SHORT_MEMORY_CAP(7) AND CC > MANY(7)
     * Only foreign callers (outside the method's own class) are counted.
     */
    public boolean hasShotgunSurgery(MethodMetrics metrics) {
        return metrics.getChangingMethodCount() > SHORT_MEMORY_CAP && metrics.getChangingClassCount() > MANY;
    }

    public boolean hasRefusedParentBequest(ClassMetrics metrics, List<ClassMetrics> allMetrics) {
        if (metrics.getParentClass() == null) {
            return false;
        }
        int parentProtectedMembers = getParentProtectedMembers(metrics, allMetrics);
        // BOvR: ratio of subclass methods that override base class methods, over total subclass methods (NOM)
        int nom = metrics.getNumberOfMethods();
        double bovr = nom > 0 ? (double) metrics.getNumberOfOverriddenMethods() / nom : 0.0;
        // BUR: ratio of parent protected members actually used (called/accessed) by the subclass
        // BUR is only meaningful when NProtM > FEW; otherwise the NProtM branch does not apply
        double bur = parentProtectedMembers > 0
                ? (double) metrics.getNumberOfUsedParentMembers() / parentProtectedMembers
                : 0.0;
        // Fig. 7.3: BOvR < ONE_THIRD OR (NProtM > FEW AND BUR < ONE_THIRD)
        boolean refusesBequest = bovr < ONE_THIRD || (parentProtectedMembers > FEW && bur < ONE_THIRD);
        int wmc = metrics.getWeightedMethodCount();
        double amw = nom > 0 ? (double) wmc / nom : 0.0;
        // Fig. 7.3: NOM > AVERAGE AND (AMW > AVERAGE OR WMC > AVERAGE)
        boolean isLargeClass = nom > NOM_AVERAGE && (amw > AMW_AVERAGE || wmc > WMC_AVERAGE);
        return refusesBequest && isLargeClass;
    }

    public boolean isTraditionBreaker(ClassMetrics metrics, List<ClassMetrics> allMetrics) {
        if (metrics.getParentClass() == null) {
            return false;
        }
        ClassMetrics parentMetrics = getParentClassMetrics(metrics, allMetrics);
        if (parentMetrics == null) {
            return false;
        }
        // Fig. 7.9: Parent is "neither small nor dumb":
        //   AMW > AVERAGE AND NOM > NOM_HIGH/2 AND WMC >= WMC_VERY_HIGH/2
        int parentNom = parentMetrics.getNumberOfMethods();
        int parentWmc = parentMetrics.getWeightedMethodCount();
        double parentAmw = parentNom > 0 ? (double) parentWmc / parentNom : 0.0;
        boolean parentIsNonDumb = parentAmw > AMW_AVERAGE && parentNom > NOM_HIGH / 2 && parentWmc >= WMC_VERY_HIGH / 2;
        if (!parentIsNonDumb) {
            return false;
        }
        int nom = metrics.getNumberOfMethods();
        if (nom == 0) {
            return false;
        }
        // Fig. 7.9: Child has substantial size and complexity:
        //   NOM >= NOM_HIGH AND (AMW > AVERAGE OR WMC >= VERY_HIGH)
        int wmc = metrics.getWeightedMethodCount();
        double amw = (double) wmc / nom;
        boolean isLargeAndComplex = nom >= NOM_HIGH && (amw > AMW_AVERAGE || wmc >= WMC_VERY_HIGH);
        // Fig. 7.9: Excessive increase of child interface:
        //   NAS >= NOM_AVERAGE AND PNAS >= TWO_THIRDS
        int nas = nom - metrics.getNumberOfOverriddenMethods();
        double pnas = (double) nas / nom;
        boolean excessiveInterface = nas >= NOM_AVERAGE && pnas >= TWO_THIRDS;
        return excessiveInterface && isLargeAndComplex;
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

    private ClassMetrics getParentClassMetrics(ClassMetrics metrics, List<ClassMetrics> allMetrics) {
        if (metrics.getParentClass() == null) {
            return null;
        }
        for (ClassMetrics parent : allMetrics) {
            if (parent.getFullyQualifiedName().equals(metrics.getParentClass())) {
                return parent;
            }
        }
        return null;
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

    public List<ClassDisharmony> detectSignificantDuplication(List<ClassMetrics> allMetrics) {
        List<MethodEntry> eligibleMethods = new ArrayList<>();
        long totalLoc = 0;
        int totalCount = 0;

        for (ClassMetrics classMetrics : allMetrics) {
            for (MethodMetrics method : classMetrics.getMethods().values()) {
                if (!method.isConstructor()
                        && !method.isAccessor()
                        && method.getNormalizedBodyLines().size() >= FEW) {
                    eligibleMethods.add(new MethodEntry(classMetrics, method));
                    totalLoc += method.getLinesOfCode();
                    totalCount++;
                }
            }
        }

        if (eligibleMethods.size() < 2) {
            return new ArrayList<>();
        }

        double systemAvgMethodLoc = (double) totalLoc / totalCount;

        Map<String, ClassMetrics> classMetricsMap = new HashMap<>();
        for (ClassMetrics cm : allMetrics) {
            classMetricsMap.put(cm.getFullyQualifiedName(), cm);
        }

        Map<String, FlaggedClassData> flaggedClasses = new HashMap<>();

        for (int i = 0; i < eligibleMethods.size(); i++) {
            for (int j = i + 1; j < eligibleMethods.size(); j++) {
                MethodEntry entryA = eligibleMethods.get(i);
                MethodEntry entryB = eligibleMethods.get(j);

                List<Clone> clones =
                        findExactClones(entryA.method.getNormalizedBodyLines(), entryB.method.getNormalizedBodyLines());
                if (clones.isEmpty()) {
                    continue;
                }

                boolean significant = false;
                int maxSEC = 0;
                int maxSDC = 0;

                for (Clone clone : clones) {
                    if (clone.size > systemAvgMethodLoc) {
                        significant = true;
                        if (clone.size > maxSEC) maxSEC = clone.size;
                    }
                }

                for (List<Clone> chain : buildChains(clones)) {
                    int sdc = 0;
                    int minSEC = Integer.MAX_VALUE;
                    int maxLB = 0;
                    int chainMaxSEC = 0;
                    for (Clone clone : chain) {
                        sdc += clone.size;
                        if (clone.size < minSEC) minSEC = clone.size;
                        if (clone.size > chainMaxSEC) chainMaxSEC = clone.size;
                    }
                    for (int k = 0; k < chain.size() - 1; k++) {
                        Clone c1 = chain.get(k);
                        Clone c2 = chain.get(k + 1);
                        int lb = Math.min(c2.startA - (c1.startA + c1.size), c2.startB - (c1.startB + c1.size));
                        sdc += lb;
                        if (lb > maxLB) maxLB = lb;
                    }
                    if (sdc >= 2 * (FEW + 1) + 1 && minSEC > FEW && maxLB <= FEW) {
                        significant = true;
                        if (sdc > maxSDC) maxSDC = sdc;
                        if (chainMaxSEC > maxSEC) maxSEC = chainMaxSEC;
                    }
                }

                if (significant) {
                    String fqnA = entryA.classMetrics.getFullyQualifiedName();
                    String fqnB = entryB.classMetrics.getFullyQualifiedName();
                    String sigA = entryA.method.getSignature();
                    String sigB = entryB.method.getSignature();
                    String simpleA = fqnA.substring(fqnA.lastIndexOf('.') + 1);
                    String simpleB = fqnB.substring(fqnB.lastIndexOf('.') + 1);
                    flaggedClasses
                            .computeIfAbsent(fqnA, k -> new FlaggedClassData())
                            .update(maxSEC, maxSDC, sigA + " ↔ " + simpleB + "." + sigB);
                    if (!fqnA.equals(fqnB)) {
                        flaggedClasses
                                .computeIfAbsent(fqnB, k -> new FlaggedClassData())
                                .update(maxSEC, maxSDC, sigB + " ↔ " + simpleA + "." + sigA);
                    }
                }
            }
        }

        List<ClassDisharmony> results = new ArrayList<>();
        for (Map.Entry<String, FlaggedClassData> entry : flaggedClasses.entrySet()) {
            String fqn = entry.getKey();
            FlaggedClassData data = entry.getValue();
            ClassMetrics cm = classMetricsMap.get(fqn);
            if (cm == null) continue;
            String description = String.format(
                    "Significant Duplication: SEC=%d, SDC=%d | %s",
                    data.maxSEC, data.maxSDC, String.join("; ", data.partnerDescriptions));
            List<DisharmonyMetric> metricValues = List.of(
                    new DisharmonyMetric("SEC", data.maxSEC, Direction.ASCENDING),
                    new DisharmonyMetric("SDC", data.maxSDC, Direction.ASCENDING));
            results.add(
                    new ClassDisharmony(fqn, DisharmonyTypes.SIGNIFICANT_DUPLICATION, description, cm, metricValues));
        }
        return results;
    }

    private List<Clone> findExactClones(List<String> linesA, List<String> linesB) {
        List<Clone> clones = new ArrayList<>();
        int m = linesA.size();
        int n = linesB.size();
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (linesA.get(i).equals(linesB.get(j))) {
                    if (i > 0 && j > 0 && linesA.get(i - 1).equals(linesB.get(j - 1))) {
                        continue;
                    }
                    int size = 0;
                    while (i + size < m && j + size < n && linesA.get(i + size).equals(linesB.get(j + size))) {
                        size++;
                    }
                    clones.add(new Clone(i, j, size));
                }
            }
        }
        return clones;
    }

    private List<List<Clone>> buildChains(List<Clone> clones) {
        List<List<Clone>> chains = new ArrayList<>();
        if (clones.isEmpty()) return chains;

        List<Clone> current = new ArrayList<>();
        current.add(clones.get(0));

        for (int i = 1; i < clones.size(); i++) {
            Clone prev = clones.get(i - 1);
            Clone curr = clones.get(i);
            int gapA = curr.startA - (prev.startA + prev.size);
            int gapB = curr.startB - (prev.startB + prev.size);
            if (gapA >= 0 && gapB >= 0 && Math.min(gapA, gapB) <= FEW) {
                current.add(curr);
            } else {
                if (current.size() > 1) {
                    chains.add(new ArrayList<>(current));
                }
                current = new ArrayList<>();
                current.add(curr);
            }
        }
        if (current.size() > 1) {
            chains.add(current);
        }
        return chains;
    }

    private static final class FlaggedClassData {
        int maxSEC = 0;
        int maxSDC = 0;
        final Set<String> partnerDescriptions = new LinkedHashSet<>();

        void update(int sec, int sdc, String partnerDescription) {
            if (sec > maxSEC) maxSEC = sec;
            if (sdc > maxSDC) maxSDC = sdc;
            partnerDescriptions.add(partnerDescription);
        }
    }

    private static final class MethodEntry {
        final ClassMetrics classMetrics;
        final MethodMetrics method;

        MethodEntry(ClassMetrics classMetrics, MethodMetrics method) {
            this.classMetrics = classMetrics;
            this.method = method;
        }
    }

    private static final class Clone {
        final int startA;
        final int startB;
        final int size;

        Clone(int startA, int startB, int size) {
            this.startA = startA;
            this.startB = startB;
            this.size = size;
        }
    }
}
