package org.hjug.cbc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.hjug.graphbuilder.metrics.ClassMetrics;
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

/**
 * Verifies that calculateDisharmonyCostBenefitValues correctly ranks generic disharmonies
 * by change proneness (churn) from git history, analogous to calculateGodClassCostBenefitValues.
 */
class DisharmonyChurnRankingTest {

    @TempDir
    File tempFolder;

    private Git git;
    private static final String CLASS_A_PATH = "com/example/ClassA.java";
    private static final String CLASS_B_PATH = "com/example/ClassB.java";
    private static final String CLASS_A_FQN = "com.example.ClassA";
    private static final String CLASS_B_FQN = "com.example.ClassB";

    @BeforeEach
    void setUp() throws GitAPIException, IOException {
        git = Git.init().setDirectory(tempFolder).call();
        new File(tempFolder.getPath() + "/com/example").mkdirs();
    }

    @AfterEach
    void tearDown() {
        git.getRepository().close();
    }

    // ── Test 1: multi-file churn sorting ──────────────────────────────────────

    @Test
    void classDisharmoniesAreRankedByChurnAcrossMultipleFiles() throws Exception {
        // ClassA: 2 commits (more churn), ClassB: 1 commit (less churn)
        writeFile(CLASS_A_PATH, "public class ClassA {}");
        writeFile(CLASS_B_PATH, "public class ClassB {}");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial").call();

        writeFile(CLASS_A_PATH, "public class ClassA { /* v2 */ }");
        git.add().addFilepattern(CLASS_A_PATH).call();
        git.commit().setMessage("update ClassA").call();

        Map<String, String> fileMap = new HashMap<>();
        fileMap.put(CLASS_A_FQN, CLASS_A_PATH);
        fileMap.put(CLASS_B_FQN, CLASS_B_PATH);

        // ClassA overallRank=2 (higher effort), ClassB overallRank=1 (lower effort)
        DisharmonyInstance classA = makeInstance(CLASS_A_FQN, CLASS_A_PATH, 2);
        DisharmonyInstance classB = makeInstance(CLASS_B_FQN, CLASS_B_PATH, 1);

        try (CostBenefitCalculator calc =
                new CostBenefitCalculator(git.getRepository().getDirectory().getParent(), fileMap)) {

            List<RankedDisharmony> ranked = calc.calculateDisharmonyCostBenefitValues(List.of(classA, classB));

            assertEquals(2, ranked.size(), "both instances should appear — none silently dropped");

            RankedDisharmony rdA = findByFileName(ranked, "ClassA.java");
            RankedDisharmony rdB = findByFileName(ranked, "ClassB.java");

            assertEquals(2, rdA.getCommitCount(), "ClassA should have 2 commits");
            assertEquals(1, rdB.getCommitCount(), "ClassB should have 1 commit");

            assertTrue(
                    rdA.getChangePronenessRank() > rdB.getChangePronenessRank(),
                    "more commits → higher changePronenessRank; ClassA=" + rdA.getChangePronenessRank() + " ClassB="
                            + rdB.getChangePronenessRank());

            assertEquals(
                    rdA.getChangePronenessRank() - rdA.getEffortRank(),
                    rdA.getRawPriority(),
                    "rawPriority must equal changePronenessRank - effortRank for ClassA");
            assertEquals(
                    rdB.getChangePronenessRank() - rdB.getEffortRank(),
                    rdB.getRawPriority(),
                    "rawPriority must equal changePronenessRank - effortRank for ClassB");

            assertEquals(1, ranked.get(0).getPriority(), "priority 1 must be first in the list");
            assertNotNull(rdA.getFirstCommitTime());
            assertNotNull(rdA.getMostRecentCommitTime());
            assertNotNull(rdB.getFirstCommitTime());
            assertNotNull(rdB.getMostRecentCommitTime());
        }
    }

    // ── Test 2: commit timestamps from git history ─────────────────────────────

    @Test
    void classDisharmonyChurnFieldsAreSetFromGitHistory() throws Exception {
        writeFile(CLASS_A_PATH, "public class ClassA {}");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("first commit").call();

        Thread.sleep(1000); // guarantee distinct commit timestamps

        writeFile(CLASS_A_PATH, "public class ClassA { /* v2 */ }");
        git.add().addFilepattern(CLASS_A_PATH).call();
        git.commit().setMessage("second commit").call();

        Map<String, String> fileMap = new HashMap<>();
        fileMap.put(CLASS_A_FQN, CLASS_A_PATH);

        DisharmonyInstance instance = makeInstance(CLASS_A_FQN, CLASS_A_PATH, 1);

        try (CostBenefitCalculator calc =
                new CostBenefitCalculator(git.getRepository().getDirectory().getParent(), fileMap)) {

            List<RankedDisharmony> ranked = calc.calculateDisharmonyCostBenefitValues(List.of(instance));

            assertFalse(ranked.isEmpty());
            RankedDisharmony rd = ranked.get(0);

            assertTrue(rd.getCommitCount() >= 2, "should reflect 2 actual commits");
            assertNotNull(rd.getChangePronenessRank());
            assertTrue(rd.getChangePronenessRank() >= 1, "changePronenessRank must be >= 1");
            assertNotNull(rd.getFirstCommitTime());
            assertNotNull(rd.getMostRecentCommitTime());
            assertTrue(
                    rd.getFirstCommitTime().isBefore(rd.getMostRecentCommitTime()),
                    "firstCommitTime must be before mostRecentCommitTime");
        }
    }

    // ── Test 3: method-level — class IS in mapping ─────────────────────────────

    @Test
    void methodDisharmoniesAreRankedByChurnWhenClassIsInMapping() throws Exception {
        writeFile(CLASS_A_PATH, "public class ClassA {}");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("first").call();

        writeFile(CLASS_A_PATH, "public class ClassA { /* v2 */ }");
        git.add().addFilepattern(CLASS_A_PATH).call();
        git.commit().setMessage("second").call();

        Map<String, String> fileMap = new HashMap<>();
        fileMap.put(CLASS_A_FQN, CLASS_A_PATH);

        CodebaseGraphDTO dto = buildDtoWithMethodDisharmony(CLASS_A_FQN, CLASS_A_PATH, fileMap);

        try (CostBenefitCalculator calc = new CostBenefitCalculator(
                git.getRepository().getDirectory().getParent(), dto.getClassToSourceFilePathMapping())) {

            List<DisharmonyInstance> instances = calc.getMethodDisharmonies(dto, DisharmonyTypes.BRAIN_METHOD);
            List<RankedDisharmony> ranked = calc.calculateDisharmonyCostBenefitValues(instances);

            assertFalse(ranked.isEmpty(), "method disharmony should appear in results, not be silently dropped");

            RankedDisharmony rd = ranked.get(0);
            assertNotNull(rd.getChangePronenessRank(), "changePronenessRank must not be null");
            assertTrue(rd.getCommitCount() >= 2, "commitCount should reflect actual git history");
            assertEquals(DisharmonyTypes.BRAIN_METHOD, rd.getDisharmonyType());
            assertNotNull(rd.getMethodSignature(), "method signature must be preserved");
        }
    }

    // ── Test 4: method-level — class NOT in mapping → graceful skip ────────────

    @Test
    void methodDisharmoniesWithUnmappedClassAreSkippedGracefully() throws Exception {
        writeFile(CLASS_A_PATH, "public class ClassA {}");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial").call();

        // File map does NOT contain the class FQN used in the MethodDisharmony
        Map<String, String> emptyFileMap = new HashMap<>();

        String unmappedFqn = "com.example.Unmapped";
        CodebaseGraphDTO dto = buildDtoWithMethodDisharmony(unmappedFqn, CLASS_A_PATH, emptyFileMap);

        try (CostBenefitCalculator calc = new CostBenefitCalculator(
                git.getRepository().getDirectory().getParent(), dto.getClassToSourceFilePathMapping())) {

            // Must not throw; unmapped method disharmonies should be silently excluded
            List<DisharmonyInstance> instances = calc.getMethodDisharmonies(dto, DisharmonyTypes.BRAIN_METHOD);
            List<RankedDisharmony> ranked = calc.calculateDisharmonyCostBenefitValues(instances);

            assertTrue(ranked.isEmpty(), "instance with unmapped class should be filtered out, not cause NPE");

            // Verify no result carries a method signature as its file path
            for (RankedDisharmony rd : ranked) {
                assertFalse(
                        rd.getPath().contains("()"), "file path must not contain a method signature: " + rd.getPath());
            }
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private DisharmonyInstance makeInstance(String className, String filePath, int overallRank) {
        List<DisharmonyMetric> metrics = new ArrayList<>();
        metrics.add(new DisharmonyMetric("WMC", 50.0, Direction.ASCENDING));
        metrics.add(new DisharmonyMetric("TCC", 0.1, Direction.ASCENDING));
        metrics.get(0).setRank(overallRank);
        metrics.get(1).setRank(overallRank);

        DisharmonyInstance instance =
                new DisharmonyInstance(DisharmonyTypes.BRAIN_CLASS, className, filePath, "com.example", null, metrics);
        instance.setSumOfRanks(overallRank * 2);
        instance.setOverallRank(overallRank);
        return instance;
    }

    private CodebaseGraphDTO buildDtoWithMethodDisharmony(
            String classFqn, String classFilePath, Map<String, String> fileMap) {
        ClassMetrics classMetrics = new ClassMetrics(classFqn);
        classMetrics.setClassName(classFqn.substring(classFqn.lastIndexOf('.') + 1));
        classMetrics.setPackageName("com.example");
        classMetrics.setSourceFilePath(classFilePath);

        MethodMetrics mm = new MethodMetrics("heavyMethod", "heavyMethod()");
        mm.setLinesOfCode(70);
        mm.setCyclomaticComplexity(5);
        mm.setMaxNestingDepth(5);
        for (int i = 0; i < 8; i++) mm.addAccessedVariable("v" + i);
        classMetrics.addMethod(mm);

        List<DisharmonyMetric> methodMetrics = List.of(
                new DisharmonyMetric("LOC", 70, Direction.ASCENDING),
                new DisharmonyMetric("CYCLO", 5, Direction.ASCENDING),
                new DisharmonyMetric("MAXNESTING", 5, Direction.ASCENDING),
                new DisharmonyMetric("NOAV", 8, Direction.ASCENDING));

        MethodDisharmony d = new MethodDisharmony(
                classFqn, "heavyMethod()", DisharmonyTypes.BRAIN_METHOD, "Brain Method detected", mm, methodMetrics);

        Map<String, String> dtoFileMap = new HashMap<>(fileMap);
        // Only add the mapping if not using the unmapped scenario
        if (!fileMap.isEmpty()) {
            dtoFileMap.put(classFqn, classFilePath);
        }

        return new CodebaseGraphDTO(
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class),
                dtoFileMap,
                List.of(),
                List.of(d));
    }

    private void writeFile(String relativePath, String content) throws IOException {
        File file = new File(tempFolder.getPath() + "/" + relativePath);
        file.getParentFile().mkdirs();
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(content);
        }
    }

    private RankedDisharmony findByFileName(List<RankedDisharmony> ranked, String fileName) {
        return ranked.stream()
                .filter(rd -> fileName.equals(rd.getFileName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no result with fileName: " + fileName));
    }
}
