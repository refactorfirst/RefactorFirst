package org.hjug.cbc;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.*;
import java.util.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.hjug.git.ScmLogInfo;
import org.hjug.metrics.Disharmony;
import org.jetbrains.annotations.NotNull;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CostBenefitCalculatorTest {

    @TempDir
    public File tempFolder;

    private String faceletsPath = "org/apache/myfaces/tobago/facelets/";
    private String hudsonPath = "hudson/model/";
    private Git git;
    private Repository repository;

    @BeforeEach
    public void setUp() throws GitAPIException {
        git = Git.init().setDirectory(tempFolder).call();
        repository = git.getRepository();
        new File(tempFolder.getPath() + "/" + faceletsPath).mkdirs();
        new File(tempFolder.getPath() + "/" + hudsonPath).mkdirs();
    }

    @AfterEach
    public void tearDown() {
        repository.close();
    }

    @Test
    void testCBOViolation() throws IOException, GitAPIException, InterruptedException {
        // Has CBO violation
        String user = "User.java";
        InputStream userResourceAsStream = getClass().getClassLoader().getResourceAsStream(hudsonPath + user);
        writeFile(hudsonPath + user, convertInputStreamToString(userResourceAsStream));

        git.add().addFilepattern(".").call();
        RevCommit firstCommit = git.commit().setMessage("message").call();

        CostBenefitCalculator costBenefitCalculator =
                new CostBenefitCalculator(git.getRepository().getDirectory().getParent(), new HashMap<>());
        costBenefitCalculator.runPmdAnalysis();
        List<RankedDisharmony> disharmonies = costBenefitCalculator.calculateCBOCostBenefitValues();

        Assertions.assertFalse(disharmonies.isEmpty());
    }

    @Test
    void testCostBenefitCalculation() throws IOException, GitAPIException, InterruptedException {

        String attributeHandler = "AttributeHandler.java";
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream(faceletsPath + attributeHandler);
        writeFile(faceletsPath + attributeHandler, convertInputStreamToString(resourceAsStream));

        git.add().addFilepattern(".").call();
        RevCommit firstCommit = git.commit().setMessage("message").call();

        // Sleeping for one second to guarantee commits have different time stamps
        Thread.sleep(1000);

        // write contents of updated file to original file
        InputStream resourceAsStream2 =
                getClass().getClassLoader().getResourceAsStream(faceletsPath + "AttributeHandler2.java");
        writeFile(faceletsPath + attributeHandler, convertInputStreamToString(resourceAsStream2));

        InputStream resourceAsStream3 =
                getClass().getClassLoader().getResourceAsStream(faceletsPath + "AttributeHandlerAndSorter.java");
        writeFile(faceletsPath + "AttributeHandlerAndSorter.java", convertInputStreamToString(resourceAsStream3));

        git.add().addFilepattern(".").call();
        RevCommit secondCommit = git.commit().setMessage("message").call();

        CostBenefitCalculator costBenefitCalculator =
                new CostBenefitCalculator(git.getRepository().getDirectory().getParent(), new HashMap<>());
        costBenefitCalculator.runPmdAnalysis();
        List<RankedDisharmony> disharmonies = costBenefitCalculator.calculateGodClassCostBenefitValues();

        Assertions.assertEquals(1, disharmonies.get(0).getRawPriority().intValue());
        Assertions.assertEquals(1, disharmonies.get(1).getRawPriority().intValue());

        Assertions.assertEquals(1, disharmonies.get(0).getPriority().intValue());
        Assertions.assertEquals(2, disharmonies.get(1).getPriority().intValue());
    }

    @Test
    void calculateSourceNodeCostBenefitValues_filtersMissingLogInfoAndAssignsPriority() throws Exception {

        writeFile(hudsonPath + "Dummy.java", "public class Dummy {}");

        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();

        SimpleDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        classGraph.addVertex("ClassA");
        classGraph.addVertex("ClassB");
        classGraph.addVertex("ClassC");
        classGraph.addVertex("ClassD");
        classGraph.addVertex("ClassE");
        classGraph.addVertex("ClassF");

        DefaultWeightedEdge edge1 = classGraph.addEdge("ClassA", "ClassB");
        classGraph.setEdgeWeight(edge1, 4);

        DefaultWeightedEdge edge2 = classGraph.addEdge("ClassC", "ClassD");
        classGraph.setEdgeWeight(edge2, 2);

        Map<DefaultWeightedEdge, CycleNode> edgeSourceNodeInfos = new HashMap<>();
        edgeSourceNodeInfos.put(edge1, new CycleNode("ClassA", hudsonPath + "ClassA.java"));
        edgeSourceNodeInfos.put(edge2, new CycleNode("ClassC", hudsonPath + "ClassC.java"));

        Map<DefaultWeightedEdge, CycleNode> edgeTargeteNodeInfos = new HashMap<>();
        edgeTargeteNodeInfos.put(edge1, new CycleNode("ClassB", hudsonPath + "ClassB.java"));
        edgeTargeteNodeInfos.put(edge2, new CycleNode("ClassD", hudsonPath + "ClassD.java"));

        Map<DefaultWeightedEdge, Integer> edgeToRemoveCycleCounts = new HashMap<>();
        edgeToRemoveCycleCounts.put(edge1, 5);
        edgeToRemoveCycleCounts.put(edge2, 3);

        Set<String> vertexesToRemove = new HashSet<>(Arrays.asList("ClassA", "ClassD"));

        ScmLogInfo scmLogInfo1 = new ScmLogInfo(hudsonPath + "ClassA.java", null, 1, 2, 3);
        scmLogInfo1.setChangePronenessRank(4);

        ScmLogInfo scmLogInfo2 = new ScmLogInfo(hudsonPath + "ClassC.java", null, 1, 2, 5);
        scmLogInfo2.setChangePronenessRank(7);

        List<ScmLogInfo> scmLogInfos = Arrays.asList(scmLogInfo1, scmLogInfo2);

        try (TestableCostBenefitCalculator costBenefitCalculator = new TestableCostBenefitCalculator(
                git.getRepository().getDirectory().getParent(), scmLogInfos)) {

            List<RankedDisharmony> disharmonies = costBenefitCalculator.calculateSourceNodeCostBenefitValues(
                    classGraph, edgeTargeteNodeInfos, edgeTargeteNodeInfos, edgeToRemoveCycleCounts, vertexesToRemove);

            Assertions.assertEquals(2, disharmonies.size());

            RankedDisharmony classA = disharmonies.get(0);
            Assertions.assertEquals("ClassA", classA.getClassName());
            Assertions.assertEquals(5, classA.getCycleCount().intValue());
            Assertions.assertEquals(4, classA.getEffortRank().intValue());
            Assertions.assertEquals(1, classA.getSourceNodeShouldBeRemoved());
            Assertions.assertEquals(0, classA.getTargetNodeShouldBeRemoved());
            Assertions.assertEquals(1, classA.getPriority().intValue());

            RankedDisharmony classC = disharmonies.get(1);
            Assertions.assertEquals("ClassC", classC.getClassName());
            Assertions.assertEquals(3, classC.getCycleCount().intValue());
            Assertions.assertEquals(2, classC.getEffortRank().intValue());
            Assertions.assertEquals(0, classC.getSourceNodeShouldBeRemoved());
            Assertions.assertEquals(1, classC.getTargetNodeShouldBeRemoved());
            Assertions.assertEquals(2, classC.getPriority().intValue());
            Assertions.assertEquals(7, classC.getChangePronenessRank());
        }
    }

    @Test
    void calculateSourceNodeCostBenefitValues_prefersHigherChangePronenessRank() throws Exception {

        writeFile(faceletsPath + "Placeholder.java", "public class Placeholder {}");

        git.add().addFilepattern(".").call();
        git.commit().setMessage("initial commit").call();

        SimpleDirectedWeightedGraph<String, DefaultWeightedEdge> classGraph =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        classGraph.addVertex("Alpha");
        classGraph.addVertex("Beta");
        classGraph.addVertex("Gamma");

        DefaultWeightedEdge edge1 = classGraph.addEdge("Alpha", "Beta");
        classGraph.setEdgeWeight(edge1, 3);

        DefaultWeightedEdge edge2 = classGraph.addEdge("Gamma", "Beta");
        classGraph.setEdgeWeight(edge2, 3);

        Map<DefaultWeightedEdge, CycleNode> edgeSourceNodeInfos = new HashMap<>();
        edgeSourceNodeInfos.put(edge1, new CycleNode("Alpha", faceletsPath + "Alpha.java"));
        edgeSourceNodeInfos.put(edge2, new CycleNode("Gamma", faceletsPath + "Gamma.java"));

        Map<DefaultWeightedEdge, CycleNode> edgeTargetNodeInfos = new HashMap<>();
        edgeTargetNodeInfos.put(edge1, new CycleNode("Beta", faceletsPath + "Beta.java"));

        Map<DefaultWeightedEdge, Integer> edgeToRemoveCycleCounts = new HashMap<>();
        edgeToRemoveCycleCounts.put(edge1, 4);
        edgeToRemoveCycleCounts.put(edge2, 4);

        Set<String> vertexesToRemove = new HashSet<>(Arrays.asList("Alpha", "Gamma"));

        ScmLogInfo scmLogInfo1 = new ScmLogInfo(faceletsPath + "Alpha.java", null, 2, 3, 1);
        scmLogInfo1.setChangePronenessRank(2);

        ScmLogInfo scmLogInfo2 = new ScmLogInfo(faceletsPath + "Gamma.java", null, 2, 3, 1);
        scmLogInfo2.setChangePronenessRank(8);

        List<ScmLogInfo> scmLogInfos = Arrays.asList(scmLogInfo1, scmLogInfo2);

        try (TestableCostBenefitCalculator costBenefitCalculator = new TestableCostBenefitCalculator(
                git.getRepository().getDirectory().getParent(), scmLogInfos)) {

            List<RankedDisharmony> disharmonies = costBenefitCalculator.calculateSourceNodeCostBenefitValues(
                    classGraph, edgeSourceNodeInfos, edgeTargetNodeInfos, edgeToRemoveCycleCounts, vertexesToRemove);

            Assertions.assertEquals(2, disharmonies.size());
            Assertions.assertEquals(8, disharmonies.get(0).getChangePronenessRank());
            Assertions.assertEquals(1, disharmonies.get(0).getPriority().intValue());
            Assertions.assertEquals(2, disharmonies.get(1).getPriority().intValue());
            Assertions.assertEquals(2, disharmonies.get(1).getChangePronenessRank());
        }
    }

    @Test
    void sortEdgesThatNeedToBeRemoved_sortsByMultipleCriteria() {
        // Create ScmLogInfo objects for testing
        ScmLogInfo logInfo1 = new ScmLogInfo("path1.java", null, 1, 2, 3);
        logInfo1.setChangePronenessRank(5);

        ScmLogInfo logInfo2 = new ScmLogInfo("path2.java", null, 1, 2, 3);
        logInfo2.setChangePronenessRank(3);

        ScmLogInfo logInfo3 = new ScmLogInfo("path3.java", null, 1, 2, 3);
        logInfo3.setChangePronenessRank(8);

        ScmLogInfo logInfo4 = new ScmLogInfo("path4.java", null, 1, 2, 3);
        logInfo4.setChangePronenessRank(2);

        ScmLogInfo logInfo5 = new ScmLogInfo("path4.java", null, 1, 2, 3);
        logInfo5.setChangePronenessRank(5);

        // Create RankedDisharmony objects with different combinations
        // Expected order after sorting: cycleCount desc, then sourceRemoved desc, then targetRemoved desc, then
        // changeProneness desc
        // cycle=5, source=0, target=0, change=5
        RankedDisharmony disharmony1 = new RankedDisharmony(
                "Class1", "", new org.jgrapht.graph.DefaultWeightedEdge(), 5, 1, false, false, logInfo1, null);

        // cycle=5, source=1, target=0, change=3
        RankedDisharmony disharmony2 = new RankedDisharmony(
                "Class2", "", new org.jgrapht.graph.DefaultWeightedEdge(), 5, 1, true, false, logInfo2, null);

        // cycle=3, source=0, target=1, change=8
        RankedDisharmony disharmony3 = new RankedDisharmony(
                "Class3", "", new org.jgrapht.graph.DefaultWeightedEdge(), 3, 1, false, true, logInfo3, null);

        // cycle=3, source=0, target=0, change=2
        RankedDisharmony disharmony4 = new RankedDisharmony(
                "Class4", "", new org.jgrapht.graph.DefaultWeightedEdge(), 3, 1, false, false, logInfo4, null);

        // cycle=3, source=0, target=0, change=5
        RankedDisharmony disharmony5 = new RankedDisharmony(
                "Class5", "", new org.jgrapht.graph.DefaultWeightedEdge(), 3, 1, false, false, logInfo5, null);

        List<RankedDisharmony> disharmonies =
                Arrays.asList(disharmony4, disharmony2, disharmony1, disharmony3, disharmony5);

        // Sort the list
        CostBenefitCalculator.sortEdgesThatNeedToBeRemoved(disharmonies);

        // Verify the order
        // Order by cycle count reversed (highest count bubbles to the top)
        // then Order by source node removed (source nodes needing to be removed bubble to the top)
        // then Order by target node removed (target nodes needing to be removed bubble to the top)\
        // then Order by change proneness (highest change proneness bubbles to the top)
        for (RankedDisharmony disharmony : disharmonies) {
            System.out.println(disharmony.getClassName() + " "
                    + disharmony.getCycleCount() + " "
                    + disharmony.getEffortRank() + " "
                    + disharmony.getSourceNodeShouldBeRemoved() + " "
                    + disharmony.getTargetNodeShouldBeRemoved() + " "
                    + disharmony.getChangePronenessRank());
        }

        RankedDisharmony orderedDisharmony0 = disharmonies.get(0);
        Assertions.assertEquals("Class1", orderedDisharmony0.getClassName());
        Assertions.assertEquals(5, orderedDisharmony0.getCycleCount().intValue());
        Assertions.assertEquals(1, orderedDisharmony0.getEffortRank().intValue());
        Assertions.assertEquals(0, orderedDisharmony0.getSourceNodeShouldBeRemoved());
        Assertions.assertEquals(0, orderedDisharmony0.getTargetNodeShouldBeRemoved());
        Assertions.assertEquals(5, orderedDisharmony0.getChangePronenessRank());

        RankedDisharmony orderedDisharmony1 = disharmonies.get(1);
        Assertions.assertEquals("Class2", orderedDisharmony1.getClassName());
        Assertions.assertEquals(5, orderedDisharmony1.getCycleCount().intValue());
        Assertions.assertEquals(1, orderedDisharmony1.getEffortRank().intValue());
        Assertions.assertEquals(1, orderedDisharmony1.getSourceNodeShouldBeRemoved());
        Assertions.assertEquals(0, orderedDisharmony1.getTargetNodeShouldBeRemoved());
        Assertions.assertEquals(3, orderedDisharmony1.getChangePronenessRank());

        RankedDisharmony orderedDisharmony2 = disharmonies.get(2);
        Assertions.assertEquals("Class3", orderedDisharmony2.getClassName());
        Assertions.assertEquals(3, orderedDisharmony2.getCycleCount().intValue());
        Assertions.assertEquals(1, orderedDisharmony2.getEffortRank().intValue());
        Assertions.assertEquals(0, orderedDisharmony2.getSourceNodeShouldBeRemoved());
        Assertions.assertEquals(1, orderedDisharmony2.getTargetNodeShouldBeRemoved());
        Assertions.assertEquals(8, orderedDisharmony2.getChangePronenessRank());

        RankedDisharmony orderedDisharmony3 = disharmonies.get(3);
        Assertions.assertEquals("Class5", orderedDisharmony3.getClassName());
        Assertions.assertEquals(3, orderedDisharmony3.getCycleCount().intValue());
        Assertions.assertEquals(1, orderedDisharmony3.getEffortRank().intValue());
        Assertions.assertEquals(0, orderedDisharmony3.getSourceNodeShouldBeRemoved());
        Assertions.assertEquals(0, orderedDisharmony3.getTargetNodeShouldBeRemoved());
        Assertions.assertEquals(5, orderedDisharmony3.getChangePronenessRank());

        RankedDisharmony orderedDisharmony4 = disharmonies.get(4);
        Assertions.assertEquals("Class4", orderedDisharmony4.getClassName());
        Assertions.assertEquals(1, orderedDisharmony4.getEffortRank().intValue());
        Assertions.assertEquals(3, orderedDisharmony4.getCycleCount().intValue());
        Assertions.assertEquals(0, orderedDisharmony4.getSourceNodeShouldBeRemoved());
        Assertions.assertEquals(0, orderedDisharmony4.getTargetNodeShouldBeRemoved());
        Assertions.assertEquals(2, orderedDisharmony4.getChangePronenessRank());
    }

    private void writeFile(String name, String content) throws IOException {
        // Files.writeString(Path.of(git.getRepository().getWorkTree().getPath()), content);
        File file = new File(git.getRepository().getWorkTree(), name);

        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(content.getBytes(UTF_8));
        }
    }

    private String convertInputStreamToString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString("UTF-8");
    }

    private static class TestableCostBenefitCalculator extends CostBenefitCalculator {

        private final List<ScmLogInfo> scmLogInfos;

        TestableCostBenefitCalculator(String repositoryPath, List<ScmLogInfo> scmLogInfos) {
            super(repositoryPath, getClassToSourceFilePathMapping());
            this.scmLogInfos = scmLogInfos;
        }

        private static @NotNull Map<String, String> getClassToSourceFilePathMapping() {
            Map<String, String> classToSourceFilePathMapping = new HashMap<>();
            classToSourceFilePathMapping.put("Alpha", "org/apache/myfaces/tobago/facelets/Alpha.java");
            classToSourceFilePathMapping.put("Beta", "org/apache/myfaces/tobago/facelets/Beta.java");
            classToSourceFilePathMapping.put("Gamma", "org/apache/myfaces/tobago/facelets/Gamma.java");
            classToSourceFilePathMapping.put("ClassA", "hudson/model/ClassA.java");
            classToSourceFilePathMapping.put("ClassB", "hudson/model/ClassB.java");
            classToSourceFilePathMapping.put("ClassC", "hudson/model/ClassC.java");
            classToSourceFilePathMapping.put("ClassD", "hudson/model/ClassD.java");
            return classToSourceFilePathMapping;
        }

        @Override
        public <T extends Disharmony> List<ScmLogInfo> getRankedChangeProneness(List<T> disharmonies) {
            return new ArrayList<>(scmLogInfos);
        }
    }
}
