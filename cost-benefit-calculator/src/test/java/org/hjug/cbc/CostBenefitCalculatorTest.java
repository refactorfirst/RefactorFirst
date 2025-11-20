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
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
                new CostBenefitCalculator(git.getRepository().getDirectory().getParent());
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
                new CostBenefitCalculator(git.getRepository().getDirectory().getParent());
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

        DefaultWeightedEdge edge3 = classGraph.addEdge("ClassE", "ClassF");
        classGraph.setEdgeWeight(edge3, 1);

        Map<DefaultWeightedEdge, CycleNode> edgeSourceNodeInfos = new HashMap<>();
        edgeSourceNodeInfos.put(edge1, new CycleNode("ClassA", hudsonPath + "ClassA.java"));
        edgeSourceNodeInfos.put(edge2, new CycleNode("ClassC", hudsonPath + "ClassC.java"));
        edgeSourceNodeInfos.put(edge3, new CycleNode("ClassE", hudsonPath + "Missing.java"));

        Map<DefaultWeightedEdge, Integer> edgeToRemoveCycleCounts = new HashMap<>();
        edgeToRemoveCycleCounts.put(edge1, 5);
        edgeToRemoveCycleCounts.put(edge2, 3);
        edgeToRemoveCycleCounts.put(edge3, 8);

        Set<String> vertexesToRemove = new HashSet<>(Arrays.asList("ClassA", "ClassD"));

        ScmLogInfo scmLogInfo1 = new ScmLogInfo(hudsonPath + "ClassA.java", 1, 2, 3);
        scmLogInfo1.setChangePronenessRank(4);

        ScmLogInfo scmLogInfo2 = new ScmLogInfo(hudsonPath + "ClassC.java", 1, 2, 5);
        scmLogInfo2.setChangePronenessRank(7);

        List<ScmLogInfo> scmLogInfos = Arrays.asList(scmLogInfo1, scmLogInfo2);

        try (TestableCostBenefitCalculator costBenefitCalculator = new TestableCostBenefitCalculator(
                git.getRepository().getDirectory().getParent(), scmLogInfos)) {

            List<RankedDisharmony> disharmonies = costBenefitCalculator.calculateSourceNodeCostBenefitValues(
                    classGraph, edgeSourceNodeInfos, edgeToRemoveCycleCounts, vertexesToRemove);

            Assertions.assertEquals(2, disharmonies.size());

            RankedDisharmony first = disharmonies.get(0);
            Assertions.assertEquals("ClassA", first.getClassName());
            Assertions.assertEquals(5, first.getCycleCount().intValue());
            Assertions.assertEquals(4, first.getEffortRank().intValue());
            Assertions.assertEquals(1, first.getSourceNodeShouldBeRemoved());
            Assertions.assertEquals(0, first.getTargetNodeShouldBeRemoved());
            Assertions.assertEquals(1, first.getPriority().intValue());

            RankedDisharmony second = disharmonies.get(1);
            Assertions.assertEquals("ClassC", second.getClassName());
            Assertions.assertEquals(3, second.getCycleCount().intValue());
            Assertions.assertEquals(2, second.getEffortRank().intValue());
            Assertions.assertEquals(0, second.getSourceNodeShouldBeRemoved());
            Assertions.assertEquals(1, second.getTargetNodeShouldBeRemoved());
            Assertions.assertEquals(2, second.getPriority().intValue());
            Assertions.assertEquals(7, second.getChangePronenessRank());
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

        Map<DefaultWeightedEdge, Integer> edgeToRemoveCycleCounts = new HashMap<>();
        edgeToRemoveCycleCounts.put(edge1, 4);
        edgeToRemoveCycleCounts.put(edge2, 4);

        Set<String> vertexesToRemove = new HashSet<>(Arrays.asList("Alpha", "Gamma"));

        ScmLogInfo scmLogInfo1 = new ScmLogInfo(faceletsPath + "Alpha.java", 2, 3, 1);
        scmLogInfo1.setChangePronenessRank(2);

        ScmLogInfo scmLogInfo2 = new ScmLogInfo(faceletsPath + "Gamma.java", 2, 3, 1);
        scmLogInfo2.setChangePronenessRank(8);

        List<ScmLogInfo> scmLogInfos = Arrays.asList(scmLogInfo1, scmLogInfo2);

        try (TestableCostBenefitCalculator costBenefitCalculator = new TestableCostBenefitCalculator(
                git.getRepository().getDirectory().getParent(), scmLogInfos)) {

            List<RankedDisharmony> disharmonies = costBenefitCalculator.calculateSourceNodeCostBenefitValues(
                    classGraph, edgeSourceNodeInfos, edgeToRemoveCycleCounts, vertexesToRemove);

            Assertions.assertEquals(2, disharmonies.size());
            Assertions.assertEquals(8, disharmonies.get(0).getChangePronenessRank());
            Assertions.assertEquals(1, disharmonies.get(0).getPriority().intValue());
            Assertions.assertEquals(2, disharmonies.get(1).getPriority().intValue());
            Assertions.assertEquals(2, disharmonies.get(1).getChangePronenessRank());
        }
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
            super(repositoryPath);
            this.scmLogInfos = scmLogInfos;
        }

        @Override
        public <T extends Disharmony> List<ScmLogInfo> getRankedChangeProneness(List<T> disharmonies) {
            return new ArrayList<>(scmLogInfos);
        }
    }
}
