package org.hjug.cbc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.metrics.ClassMetrics;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.ClassDisharmony;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.MethodDisharmony;
import org.hjug.graphbuilder.metrics.DisharmonyMetric;
import org.hjug.graphbuilder.metrics.DisharmonyMetric.Direction;
import org.hjug.graphbuilder.metrics.DisharmonyTypes;
import org.hjug.graphbuilder.metrics.MethodMetrics;
import org.hjug.metrics.DisharmonyInstance;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class DisharmonyExtractionTest {

    @TempDir
    File tempFolder;

    private Git git;
    private static final String CLASS_PATH = "com/example/BrainClass.java";

    @BeforeEach
    void setUp() throws GitAPIException, IOException {
        git = Git.init().setDirectory(tempFolder).call();
        new File(tempFolder.getPath() + "/com/example").mkdirs();
        writeFile(CLASS_PATH, "package com.example; public class BrainClass {}");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial").call();
    }

    @AfterEach
    void tearDown() {
        git.getRepository().close();
    }

    // ── class-level extraction ─────────────────────────────────────────────────

    @Test
    void getClassDisharmoniesExtractsAndRanksBrainClasses() throws Exception {
        CodebaseGraphDTO dto = buildDtoWithBrainClass();

        try (CostBenefitCalculator calc = new CostBenefitCalculator(
                git.getRepository().getDirectory().getParent(), dto.getClassToSourceFilePathMapping())) {

            List<DisharmonyInstance> instances = calc.getClassDisharmonies(dto, DisharmonyTypes.BRAIN_CLASS);

            assertFalse(instances.isEmpty());
            DisharmonyInstance instance = instances.get(0);
            assertEquals(DisharmonyTypes.BRAIN_CLASS, instance.getDisharmonyType());
            assertEquals("com.example.BrainClass", instance.getClassName());
            assertNotNull(instance.getOverallRank());
            assertFalse(instance.getMetrics().isEmpty());
            assertNull(instance.getMethodSignature(), "class-level disharmony should have null methodSignature");
        }
    }

    @Test
    void calculateDisharmonyCostBenefitValuesReturnsRankedList() throws Exception {
        CodebaseGraphDTO dto = buildDtoWithBrainClass();

        try (CostBenefitCalculator calc = new CostBenefitCalculator(
                git.getRepository().getDirectory().getParent(), dto.getClassToSourceFilePathMapping())) {

            List<DisharmonyInstance> instances = calc.getClassDisharmonies(dto, DisharmonyTypes.BRAIN_CLASS);
            List<RankedDisharmony> ranked = calc.calculateDisharmonyCostBenefitValues(instances);

            assertFalse(ranked.isEmpty());
            RankedDisharmony rd = ranked.get(0);
            assertEquals(1, rd.getPriority());
            assertEquals(DisharmonyTypes.BRAIN_CLASS, rd.getDisharmonyType());
            assertFalse(rd.getRankedMetrics().isEmpty());
            assertEquals("BrainMethods", rd.getRankedMetrics().get(0).getName());
            assertNotNull(rd.getCommitCount());
        }
    }

    // ── method-level extraction ────────────────────────────────────────────────

    @Test
    void getMethodDisharmoniesExtractsAndRanksBrainMethods() throws Exception {
        CodebaseGraphDTO dto = buildDtoWithBrainMethod();

        try (CostBenefitCalculator calc = new CostBenefitCalculator(
                git.getRepository().getDirectory().getParent(), dto.getClassToSourceFilePathMapping())) {

            List<DisharmonyInstance> instances = calc.getMethodDisharmonies(dto, DisharmonyTypes.BRAIN_METHOD);

            assertFalse(instances.isEmpty());
            DisharmonyInstance instance = instances.get(0);
            assertEquals(DisharmonyTypes.BRAIN_METHOD, instance.getDisharmonyType());
            assertNotNull(instance.getMethodSignature(), "method-level disharmony should have a methodSignature");
            assertNotNull(instance.getOverallRank());
        }
    }

    @Test
    void rankedDisharmonyForMethodLevelIncludesMethodSignature() throws Exception {
        CodebaseGraphDTO dto = buildDtoWithBrainMethod();

        try (CostBenefitCalculator calc = new CostBenefitCalculator(
                git.getRepository().getDirectory().getParent(), dto.getClassToSourceFilePathMapping())) {

            List<DisharmonyInstance> instances = calc.getMethodDisharmonies(dto, DisharmonyTypes.BRAIN_METHOD);
            List<RankedDisharmony> ranked = calc.calculateDisharmonyCostBenefitValues(instances);

            assertFalse(ranked.isEmpty());
            RankedDisharmony rd = ranked.get(0);
            assertNotNull(rd.getMethodSignature());
        }
    }

    // ── unknown type returns empty list ───────────────────────────────────────

    @Test
    void getClassDisharmoniesForUnknownTypeReturnsEmpty() throws Exception {
        CodebaseGraphDTO dto = buildDtoWithBrainClass();

        try (CostBenefitCalculator calc = new CostBenefitCalculator(
                git.getRepository().getDirectory().getParent(), dto.getClassToSourceFilePathMapping())) {

            List<DisharmonyInstance> instances = calc.getClassDisharmonies(dto, "No Such Type");
            assertTrue(instances.isEmpty());
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private CodebaseGraphDTO buildDtoWithBrainClass() {
        ClassMetrics m = new ClassMetrics("com.example.BrainClass");
        m.setClassName("BrainClass");
        m.setPackageName("com.example");
        m.setSourceFilePath(CLASS_PATH);
        m.setLinesOfCode(200);
        m.setTightClassCohesion(0.3);

        // 2 brain methods
        for (int k = 0; k < 2; k++) {
            MethodMetrics brain = new MethodMetrics("brain" + k, "brain" + k + "()");
            brain.setLinesOfCode(70);
            brain.setCyclomaticComplexity(5);
            brain.setMaxNestingDepth(5);
            for (int i = 0; i < 8; i++) brain.addAccessedVariable("var" + k + i);
            m.addMethod(brain);
        }
        for (int i = 0; i < 45; i++) {
            MethodMetrics plain = new MethodMetrics("plain" + i, "plain" + i + "()");
            plain.setCyclomaticComplexity(1);
            m.addMethod(plain);
        }

        List<DisharmonyMetric> metrics = List.of(
                new DisharmonyMetric("BrainMethods", 2, Direction.ASCENDING),
                new DisharmonyMetric("LOC", 200, Direction.ASCENDING),
                new DisharmonyMetric("WMC", m.getWeightedMethodCount(), Direction.ASCENDING),
                new DisharmonyMetric("TCC", 0.3, Direction.DESCENDING));

        ClassDisharmony d = new ClassDisharmony(
                "com.example.BrainClass", DisharmonyTypes.BRAIN_CLASS, "Brain Class detected", m, metrics);

        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("com.example.BrainClass", CLASS_PATH);

        return new CodebaseGraphDTO(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                Set.of(), fileMap,
                List.of(d),
                List.of());
    }

    private CodebaseGraphDTO buildDtoWithBrainMethod() {
        ClassMetrics classMetrics = new ClassMetrics("com.example.BrainClass");
        classMetrics.setClassName("BrainClass");
        classMetrics.setPackageName("com.example");
        classMetrics.setSourceFilePath(CLASS_PATH);

        MethodMetrics mm = new MethodMetrics("heavyMethod", "heavyMethod()");
        mm.setLinesOfCode(70);
        mm.setCyclomaticComplexity(5);
        mm.setMaxNestingDepth(5);
        for (int i = 0; i < 8; i++) mm.addAccessedVariable("v" + i);
        classMetrics.addMethod(mm);

        List<DisharmonyMetric> metrics = List.of(
                new DisharmonyMetric("LOC", 70, Direction.ASCENDING),
                new DisharmonyMetric("CYCLO", 5, Direction.ASCENDING),
                new DisharmonyMetric("MAXNESTING", 5, Direction.ASCENDING),
                new DisharmonyMetric("NOAV", 8, Direction.ASCENDING));

        MethodDisharmony d = new MethodDisharmony(
                "com.example.BrainClass",
                "heavyMethod()",
                DisharmonyTypes.BRAIN_METHOD,
                "Brain Method detected",
                mm,
                metrics);

        Map<String, String> fileMap = new HashMap<>();
        fileMap.put("com.example.BrainClass", CLASS_PATH);

        return new CodebaseGraphDTO(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                Set.of(), fileMap,
                List.of(),
                List.of(d));
    }

    private void writeFile(String relativePath, String content) throws IOException {
        File file = new File(tempFolder.getPath() + "/" + relativePath);
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
}
