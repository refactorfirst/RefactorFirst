package org.hjug.graphbuilder.metrics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.ClassDisharmony;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.MethodDisharmony;
import org.hjug.graphbuilder.metrics.DisharmonyMetric.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DisharmonyDetectorMetricsTest {

    private final DisharmonyDetector detector = new DisharmonyDetector();
    private List<ClassMetrics> allMetrics;

    // ── helpers ────────────────────────────────────────────────────────────────

    private ClassMetrics godClassMetrics() {
        ClassMetrics m = new ClassMetrics("com.example.GodClass");
        m.setClassName("GodClass");
        m.setPackageName("com.example");
        m.setSourceFilePath("src/main/java/com/example/GodClass.java");
        m.setAccessToForeignData(10); // ATFD > 5
        m.setTightClassCohesion(0.1); // TCC < 0.33
        // WMC >= 47: add 47 methods each with complexity 1
        for (int i = 0; i < 47; i++) {
            MethodMetrics mm = new MethodMetrics("m" + i, "m" + i + "()");
            mm.setCyclomaticComplexity(1);
            m.addMethod(mm);
        }
        return m;
    }

    private ClassMetrics dataClassMetrics() {
        ClassMetrics m = new ClassMetrics("com.example.DataClass");
        m.setClassName("DataClass");
        m.setPackageName("com.example");
        m.setSourceFilePath("src/main/java/com/example/DataClass.java");
        // WOC < 1/3: more accessors than non-accessors
        for (int i = 0; i < 6; i++) {
            MethodMetrics mm = new MethodMetrics("get" + i, "get" + i + "()");
            mm.setAccessor(true);
            mm.setCyclomaticComplexity(1);
            m.addMethod(mm);
        }
        // WMC < 31, publicAccessors > 5
        m.setNumberOfPublicAttributes(0); // will be 0 pub attrs, 6 accessors
        // WOC = (NOM - accessors)/NOM = 0/6 = 0 < 1/3
        return m;
    }

    private ClassMetrics brainMethodClassMetrics() {
        ClassMetrics classMetrics = new ClassMetrics("com.example.BrainMethodClass");
        classMetrics.setClassName("BrainMethodClass");
        classMetrics.setPackageName("com.example");
        classMetrics.setSourceFilePath("src/main/java/com/example/BrainMethodClass.java");

        MethodMetrics mm = new MethodMetrics("heavyMethod", "heavyMethod()");
        mm.setLinesOfCode(70); // > 65
        mm.setCyclomaticComplexity(5); // >= 4
        mm.setMaxNestingDepth(5); // >= 5
        for (int i = 0; i < 8; i++) {
            mm.addAccessedVariable("var" + i);
        }
        classMetrics.addMethod(mm);
        return classMetrics;
    }

    private ClassMetrics brainClassMetrics() {
        ClassMetrics m = new ClassMetrics("com.example.BrainClass");
        m.setClassName("BrainClass");
        m.setPackageName("com.example");
        m.setSourceFilePath("src/main/java/com/example/BrainClass.java");
        m.setAccessToForeignData(2); // ATFD <= 5 (not god class)
        m.setTightClassCohesion(0.3); // TCC < 0.5
        m.setLinesOfCode(200); // >= 195

        // 2 brain methods to satisfy: brainMethodCount > 1 AND LOC >= 195 AND WMC >= 47 AND TCC < 0.5
        for (int k = 0; k < 2; k++) {
            MethodMetrics brain = new MethodMetrics("brain" + k, "brain" + k + "()");
            brain.setLinesOfCode(70);
            brain.setCyclomaticComplexity(5);
            brain.setMaxNestingDepth(5);
            for (int i = 0; i < 8; i++) {
                brain.addAccessedVariable("var" + k + i);
            }
            m.addMethod(brain);
        }
        // pad methods to get WMC >= 47 (2 brain methods + 45 plain)
        for (int i = 0; i < 45; i++) {
            MethodMetrics plain = new MethodMetrics("plain" + i, "plain" + i + "()");
            plain.setCyclomaticComplexity(1);
            m.addMethod(plain);
        }
        return m;
    }

    private ClassMetrics featureEnvyClassMetrics() {
        ClassMetrics classMetrics = new ClassMetrics("com.example.FeatureEnvyClass");
        classMetrics.setClassName("FeatureEnvyClass");
        classMetrics.setPackageName("com.example");
        classMetrics.setSourceFilePath("src/main/java/com/example/FeatureEnvyClass.java");

        MethodMetrics mm = new MethodMetrics("envyMethod", "envyMethod()");
        // ATFD > 5
        for (int i = 0; i < 6; i++) {
            mm.addAccessedForeignAttribute("Foreign.attr" + i);
        }
        // LAA < 0.33: add own attrs too but mostly foreign
        for (int i = 0; i < 2; i++) {
            mm.addAccessedOwnAttribute("own" + i);
        }
        // FDP <= 5: only a few foreign classes
        mm.addAccessedForeignClass("ClassA");
        mm.addAccessedForeignClass("ClassB");
        classMetrics.addMethod(mm);
        return classMetrics;
    }

    private ClassMetrics longMethodClassMetrics() {
        ClassMetrics classMetrics = new ClassMetrics("com.example.LongMethodClass");
        classMetrics.setClassName("LongMethodClass");
        classMetrics.setPackageName("com.example");
        classMetrics.setSourceFilePath("src/main/java/com/example/LongMethodClass.java");

        MethodMetrics mm = new MethodMetrics("longMethod", "longMethod()");
        mm.setLinesOfCode(70); // > 65
        classMetrics.addMethod(mm);
        return classMetrics;
    }

    private ClassMetrics intensiveCouplingClassMetrics() {
        ClassMetrics classMetrics = new ClassMetrics("com.example.IntensiveCoupling");
        classMetrics.setClassName("IntensiveCoupling");
        classMetrics.setPackageName("com.example");
        classMetrics.setSourceFilePath("src/main/java/com/example/IntensiveCoupling.java");

        MethodMetrics mm = new MethodMetrics("intensiveMethod", "intensiveMethod()");
        // CINT > 7, CDISP < 0.25: concentrate calls in few classes
        for (int i = 0; i < 10; i++) {
            mm.addCalledForeignMethod("ClassA.m" + i);
        }
        mm.addCalledForeignMethodClass("ClassA"); // CDISP = 1/10 = 0.1 < 0.25
        mm.setMaxNestingDepth(2); // > 1
        classMetrics.addMethod(mm);
        return classMetrics;
    }

    private ClassMetrics dispersedCouplingClassMetrics() {
        ClassMetrics classMetrics = new ClassMetrics("com.example.DispersedCoupling");
        classMetrics.setClassName("DispersedCoupling");
        classMetrics.setPackageName("com.example");
        classMetrics.setSourceFilePath("src/main/java/com/example/DispersedCoupling.java");

        MethodMetrics mm = new MethodMetrics("dispersedMethod", "dispersedMethod()");
        // CINT > 7, CDISP >= 0.5: spread calls across many classes
        for (int i = 0; i < 8; i++) {
            mm.addCalledForeignMethod("Class" + i + ".method");
            mm.addCalledForeignMethodClass("Class" + i);
        }
        // CDISP = 8/8 = 1.0 >= 0.5
        mm.setMaxNestingDepth(2); // > 1
        classMetrics.addMethod(mm);
        return classMetrics;
    }

    private ClassMetrics shotgunSurgeryClassMetrics(List<ClassMetrics> allMetrics) {
        ClassMetrics target = new ClassMetrics("com.example.ShotgunTarget");
        target.setClassName("ShotgunTarget");
        target.setPackageName("com.example");
        target.setSourceFilePath("src/main/java/com/example/ShotgunTarget.java");

        MethodMetrics mm = new MethodMetrics("fragileMethod", "fragileMethod()");
        // CM > 7, CC > 7: many methods from many classes call this method
        for (int i = 0; i < 9; i++) {
            mm.addChangingMethod("CallerClass" + i + ".callerMethod");
            mm.addChangingClass("CallerClass" + i);
        }
        target.addMethod(mm);
        return target;
    }

    private ClassMetrics parentMetrics(String fqn, int nomHigh, int wmcHigh) {
        ClassMetrics parent = new ClassMetrics(fqn);
        parent.setClassName(fqn.substring(fqn.lastIndexOf('.') + 1));
        parent.setPackageName("com.example");
        parent.setSourceFilePath("src/main/java/com/example/Parent.java");
        parent.setNumberOfProtectedMembers(8); // > 5
        // NOM >= NOM_HIGH/2 = 6, WMC >= WMC_VERY_HIGH/2 = 23
        for (int i = 0; i < nomHigh; i++) {
            MethodMetrics mm = new MethodMetrics("parentM" + i, "parentM" + i + "()");
            mm.setCyclomaticComplexity(wmcHigh / nomHigh + 1);
            parent.addMethod(mm);
        }
        return parent;
    }

    private ClassMetrics rpbChildMetrics(String parentFqn) {
        ClassMetrics child = new ClassMetrics("com.example.RPBChild");
        child.setClassName("RPBChild");
        child.setPackageName("com.example");
        child.setSourceFilePath("src/main/java/com/example/RPBChild.java");
        child.setParentClass(parentFqn);
        // NOM > 7 (average), AMW > 2, WMC > 14
        for (int i = 0; i < 8; i++) {
            MethodMetrics mm = new MethodMetrics("m" + i, "m" + i + "()");
            mm.setCyclomaticComplexity(3); // AMW = 3 > 2
            child.addMethod(mm);
        }
        // BOvR < 1/3: override < 1/3 of methods (0 overrides, so 0/8 < 0.33)
        return child;
    }

    private ClassMetrics traditionBreakerParent(String fqn) {
        ClassMetrics parent = new ClassMetrics(fqn);
        parent.setClassName(fqn.substring(fqn.lastIndexOf('.') + 1));
        parent.setPackageName("com.example");
        parent.setSourceFilePath("src/main/java/com/example/TBParent.java");
        // AMW > 2, NOM > 6, WMC >= 23
        for (int i = 0; i < 7; i++) {
            MethodMetrics mm = new MethodMetrics("pm" + i, "pm" + i + "()");
            mm.setCyclomaticComplexity(4); // WMC = 28, AMW = 4
            parent.addMethod(mm);
        }
        return parent;
    }

    private ClassMetrics traditionBreakerChild(String parentFqn) {
        ClassMetrics child = new ClassMetrics("com.example.TBChild");
        child.setClassName("TBChild");
        child.setPackageName("com.example");
        child.setSourceFilePath("src/main/java/com/example/TBChild.java");
        child.setParentClass(parentFqn);
        // NOM >= 12, AMW > 2 or WMC >= 47; NAS >= 7, PNAS >= 0.67
        for (int i = 0; i < 13; i++) {
            MethodMetrics mm = new MethodMetrics("cm" + i, "cm" + i + "()");
            mm.setCyclomaticComplexity(5); // WMC = 65, AMW = 5
            child.addMethod(mm);
        }
        // No overridden methods → NAS = 13, PNAS = 13/13 = 1.0 >= 0.67
        return child;
    }

    @BeforeEach
    void setUp() {
        allMetrics = new ArrayList<>();
    }

    // ── God Class ──────────────────────────────────────────────────────────────

    @Test
    void godClassDisharmonyEmitsStructuredMetrics() {
        ClassMetrics m = godClassMetrics();
        allMetrics.add(m);

        List<ClassDisharmony> result = detector.detectGodClasses(allMetrics);

        assertFalse(result.isEmpty(), "should detect god class");
        ClassDisharmony d = result.get(0);
        List<DisharmonyMetric> metrics = d.getMetricValues();
        assertNotNull(metrics);
        assertEquals(3, metrics.size());

        assertEquals("ATFD", metrics.get(0).getName());
        assertEquals(Direction.ASCENDING, metrics.get(0).getDirection());

        assertEquals("WMC", metrics.get(1).getName());
        assertEquals(Direction.ASCENDING, metrics.get(1).getDirection());

        assertEquals("TCC", metrics.get(2).getName());
        assertEquals(Direction.DESCENDING, metrics.get(2).getDirection());
    }

    // ── Data Class ─────────────────────────────────────────────────────────────

    @Test
    void dataClassDisharmonyEmitsStructuredMetrics() {
        ClassMetrics m = dataClassMetrics();
        allMetrics.add(m);

        List<ClassDisharmony> result = detector.detectDataClasses(allMetrics);

        assertFalse(result.isEmpty(), "should detect data class");
        List<DisharmonyMetric> metrics = result.get(0).getMetricValues();
        assertEquals(3, metrics.size());

        assertEquals("WOC", metrics.get(0).getName());
        assertEquals(Direction.DESCENDING, metrics.get(0).getDirection());

        assertEquals("PublicAttrsAndAccessors", metrics.get(1).getName());
        assertEquals(Direction.ASCENDING, metrics.get(1).getDirection());

        assertEquals("WMC", metrics.get(2).getName());
        assertEquals(Direction.DESCENDING, metrics.get(2).getDirection());
    }

    // ── Brain Method ───────────────────────────────────────────────────────────

    @Test
    void brainMethodDisharmonyEmitsStructuredMetrics() {
        ClassMetrics classMetrics = brainMethodClassMetrics();
        allMetrics.add(classMetrics);

        List<MethodDisharmony> result = detector.detectBrainMethods(allMetrics);

        assertFalse(result.isEmpty(), "should detect brain method");
        List<DisharmonyMetric> metrics = result.get(0).getMetricValues();
        assertEquals(4, metrics.size());

        assertEquals("LOC", metrics.get(0).getName());
        assertEquals(Direction.ASCENDING, metrics.get(0).getDirection());
        assertEquals("CYCLO", metrics.get(1).getName());
        assertEquals(Direction.ASCENDING, metrics.get(1).getDirection());
        assertEquals("MAXNESTING", metrics.get(2).getName());
        assertEquals(Direction.ASCENDING, metrics.get(2).getDirection());
        assertEquals("NOAV", metrics.get(3).getName());
        assertEquals(Direction.ASCENDING, metrics.get(3).getDirection());
    }

    // ── Brain Class ────────────────────────────────────────────────────────────

    @Test
    void brainClassDisharmonyEmitsStructuredMetrics() {
        ClassMetrics m = brainClassMetrics();
        allMetrics.add(m);

        List<ClassDisharmony> result = detector.detectBrainClasses(allMetrics);

        assertFalse(result.isEmpty(), "should detect brain class");
        List<DisharmonyMetric> metrics = result.get(0).getMetricValues();
        assertEquals(4, metrics.size());

        assertEquals("BrainMethods", metrics.get(0).getName());
        assertEquals(Direction.ASCENDING, metrics.get(0).getDirection());
        assertEquals("LOC", metrics.get(1).getName());
        assertEquals(Direction.ASCENDING, metrics.get(1).getDirection());
        assertEquals("WMC", metrics.get(2).getName());
        assertEquals(Direction.ASCENDING, metrics.get(2).getDirection());
        assertEquals("TCC", metrics.get(3).getName());
        assertEquals(Direction.DESCENDING, metrics.get(3).getDirection());
    }

    // ── Feature Envy ───────────────────────────────────────────────────────────

    @Test
    void featureEnvyDisharmonyEmitsStructuredMetrics() {
        ClassMetrics classMetrics = featureEnvyClassMetrics();
        allMetrics.add(classMetrics);

        List<MethodDisharmony> result = detector.detectFeatureEnvy(allMetrics);

        assertFalse(result.isEmpty(), "should detect feature envy");
        List<DisharmonyMetric> metrics = result.get(0).getMetricValues();
        assertEquals(3, metrics.size());

        assertEquals("ATFD", metrics.get(0).getName());
        assertEquals(Direction.ASCENDING, metrics.get(0).getDirection());
        assertEquals("LAA", metrics.get(1).getName());
        assertEquals(Direction.DESCENDING, metrics.get(1).getDirection());
        assertEquals("FDP", metrics.get(2).getName());
        assertEquals(Direction.DESCENDING, metrics.get(2).getDirection());
    }

    // ── Long Method ────────────────────────────────────────────────────────────

    @Test
    void longMethodDisharmonyEmitsStructuredMetrics() {
        ClassMetrics classMetrics = longMethodClassMetrics();
        allMetrics.add(classMetrics);

        List<MethodDisharmony> result = detector.detectLongMethods(allMetrics);

        assertFalse(result.isEmpty(), "should detect long method");
        List<DisharmonyMetric> metrics = result.get(0).getMetricValues();
        assertEquals(1, metrics.size());

        assertEquals("LOC", metrics.get(0).getName());
        assertEquals(Direction.ASCENDING, metrics.get(0).getDirection());
        assertEquals(70.0, metrics.get(0).getValue());
    }

    // ── Intensive Coupling ─────────────────────────────────────────────────────

    @Test
    void intensiveCouplingDisharmonyEmitsStructuredMetrics() {
        ClassMetrics classMetrics = intensiveCouplingClassMetrics();
        allMetrics.add(classMetrics);

        List<MethodDisharmony> result = detector.detectIntensiveCoupling(allMetrics);

        assertFalse(result.isEmpty(), "should detect intensive coupling");
        List<DisharmonyMetric> metrics = result.get(0).getMetricValues();
        assertEquals(3, metrics.size());

        assertEquals("CINT", metrics.get(0).getName());
        assertEquals(Direction.ASCENDING, metrics.get(0).getDirection());
        assertEquals("CDISP", metrics.get(1).getName());
        assertEquals(Direction.DESCENDING, metrics.get(1).getDirection()); // low CDISP = worse
    }

    // ── Dispersed Coupling ─────────────────────────────────────────────────────

    @Test
    void dispersedCouplingDisharmonyEmitsStructuredMetrics() {
        ClassMetrics classMetrics = dispersedCouplingClassMetrics();
        allMetrics.add(classMetrics);

        List<MethodDisharmony> result = detector.detectDispersedCoupling(allMetrics);

        assertFalse(result.isEmpty(), "should detect dispersed coupling");
        List<DisharmonyMetric> metrics = result.get(0).getMetricValues();
        assertEquals(3, metrics.size());

        assertEquals("CINT", metrics.get(0).getName());
        assertEquals(Direction.ASCENDING, metrics.get(0).getDirection());
        assertEquals("CDISP", metrics.get(1).getName());
        assertEquals(Direction.ASCENDING, metrics.get(1).getDirection()); // high CDISP = worse
    }

    // ── Shotgun Surgery ────────────────────────────────────────────────────────

    @Test
    void shotgunSurgeryDisharmonyEmitsStructuredMetrics() {
        ClassMetrics target = shotgunSurgeryClassMetrics(allMetrics);
        allMetrics.add(target);

        List<MethodDisharmony> result = detector.detectShotgunSurgery(allMetrics);

        assertFalse(result.isEmpty(), "should detect shotgun surgery");
        List<DisharmonyMetric> metrics = result.get(0).getMetricValues();
        assertEquals(2, metrics.size());

        assertEquals("CM", metrics.get(0).getName());
        assertEquals(Direction.ASCENDING, metrics.get(0).getDirection());
        assertEquals("CC", metrics.get(1).getName());
        assertEquals(Direction.ASCENDING, metrics.get(1).getDirection());
    }

    // ── Refused Parent Bequest ─────────────────────────────────────────────────

    @Test
    void refusedParentBequestEmitsParentDerivedMetrics() {
        String parentFqn = "com.example.ParentService";
        ClassMetrics parent = parentMetrics(parentFqn, 8, 32);
        ClassMetrics child = rpbChildMetrics(parentFqn);
        allMetrics.add(parent);
        allMetrics.add(child);

        List<ClassDisharmony> result = detector.detectRefusedParentBequest(allMetrics);

        assertFalse(result.isEmpty(), "should detect refused parent bequest");
        List<DisharmonyMetric> metrics = result.get(0).getMetricValues();
        assertEquals(6, metrics.size());

        assertEquals("NProtM", metrics.get(0).getName());
        assertEquals(Direction.ASCENDING, metrics.get(0).getDirection());

        assertEquals("BUR", metrics.get(1).getName());
        assertEquals(Direction.DESCENDING, metrics.get(1).getDirection()); // low BUR = worse

        assertEquals("BOvR", metrics.get(2).getName());
        assertEquals(Direction.DESCENDING, metrics.get(2).getDirection()); // low BOvR = worse

        assertEquals("NOM", metrics.get(3).getName());
        assertEquals(Direction.ASCENDING, metrics.get(3).getDirection());

        assertEquals("AMW", metrics.get(4).getName());
        assertEquals(Direction.ASCENDING, metrics.get(4).getDirection());

        assertEquals("WMC", metrics.get(5).getName());
        assertEquals(Direction.ASCENDING, metrics.get(5).getDirection());

        // NProtM must come from parent, not child
        assertEquals(8.0, metrics.get(0).getValue(), "NProtM must be the parent's protected member count");
    }

    // ── Tradition Breaker ──────────────────────────────────────────────────────

    @Test
    void traditionBreakerDisharmonyEmitsStructuredMetrics() {
        String parentFqn = "com.example.TBParent";
        ClassMetrics parent = traditionBreakerParent(parentFqn);
        ClassMetrics child = traditionBreakerChild(parentFqn);
        allMetrics.add(parent);
        allMetrics.add(child);

        List<ClassDisharmony> result = detector.detectTraditionBreaker(allMetrics);

        assertFalse(result.isEmpty(), "should detect tradition breaker");
        List<DisharmonyMetric> metrics = result.get(0).getMetricValues();
        assertEquals(6, metrics.size());

        assertEquals("NAS", metrics.get(0).getName());
        assertEquals(Direction.ASCENDING, metrics.get(0).getDirection());
        assertEquals("PNAS", metrics.get(1).getName());
        assertEquals(Direction.ASCENDING, metrics.get(1).getDirection());
        assertEquals("NOM", metrics.get(2).getName());
        assertEquals(Direction.ASCENDING, metrics.get(2).getDirection());
        assertEquals("AMW", metrics.get(3).getName());
        assertEquals(Direction.ASCENDING, metrics.get(3).getDirection());
        assertEquals("WMC", metrics.get(4).getName());
        assertEquals(Direction.ASCENDING, metrics.get(4).getDirection());
        assertEquals("Overridden", metrics.get(5).getName());
        assertEquals(Direction.ASCENDING, metrics.get(5).getDirection());
    }
}
