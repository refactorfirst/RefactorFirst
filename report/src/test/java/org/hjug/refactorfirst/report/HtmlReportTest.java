package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.*;
import org.hjug.cbc.CycleNode;
import org.hjug.cbc.RankedCycle;
import org.hjug.graphbuilder.CodebaseGraphDTO;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Test;

class HtmlReportTest {

    private HtmlReport mavenReport = new HtmlReport();

    @Test
    void testGetOutputName() {
        // This report will generate simple-report.html when invoked in a project with `mvn site`
        assertEquals("refactor-first-report", mavenReport.getOutputName());
    }

    @Test
    void getName() {
        // Name of the report when listed in the project-reports.html page of a project
        assertEquals("Refactor First Report", mavenReport.getName(Locale.getDefault()));
    }

    @Test
    void getDescription() {
        // Description of the report when listed in the project-reports.html page of a project
        assertEquals(
                "Ranks the disharmonies in a codebase.  The classes that should be refactored first "
                        + " have the highest priority values.",
                mavenReport.getDescription(Locale.getDefault()));
    }

    @Test
    void buildCycleDot() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("org.hjug.refactorfirst.A");
        classGraph.addVertex("org.hjug.refactorfirst.B");
        classGraph.addVertex("org.hjug.refactorfirst.C");
        classGraph.addEdge("org.hjug.refactorfirst.A", "org.hjug.refactorfirst.B");
        classGraph.addEdge("org.hjug.refactorfirst.B", "org.hjug.refactorfirst.C");
        classGraph.addEdge("org.hjug.refactorfirst.C", "org.hjug.refactorfirst.A");
        classGraph.setEdgeWeight("org.hjug.refactorfirst.A", "org.hjug.refactorfirst.B", 2);

        String cycleName = "Test";
        List<CycleNode> cycleNodes = new ArrayList<>();
        RankedCycle rankedCycle =
                new RankedCycle(cycleName, 0, classGraph.vertexSet(), classGraph.edgeSet(), 0, null, cycleNodes);

        HtmlReport htmlReport = new HtmlReport();
        CodebaseGraphDTO dto = mock(CodebaseGraphDTO.class);
        HashMap<String, String> map = new HashMap<>();
        map.put("org.hjug.refactorfirst.A", "/src/main/java/org/hjug/refactorfirst/A.java");
        map.put("org.hjug.refactorfirst.B", "/src/main/java/org/hjug/refactorfirst/B.java");
        map.put("org.hjug.refactorfirst.C", "/src/main/java/org/hjug/refactorfirst/C.java");
        when(dto.getClassToSourceFilePathMapping()).thenReturn(map);
        String repoUrl = "https://github.com/refactorfirst/RefactorFirst/blob";
        String dot = htmlReport.buildCycleDot(classGraph, rankedCycle, repoUrl, dto);
        String expectedDot = "`strict digraph G {\n"
                + "org_hjug_refactorfirst_A -> org_hjug_refactorfirst_B [ label = \"2\" weight = \"2\" ];\n"
                + "org_hjug_refactorfirst_B -> org_hjug_refactorfirst_C [ label = \"1\" weight = \"1\" ];\n"
                + "org_hjug_refactorfirst_C -> org_hjug_refactorfirst_A [ label = \"1\" weight = \"1\" ];\n"
                + "org_hjug_refactorfirst_A [URL=\"https://github.com/refactorfirst/RefactorFirst/blob/src/main/java/org/hjug/refactorfirst/A.java\" target=\"_blank\" label=\"A\"];\n"
                + "org_hjug_refactorfirst_B [URL=\"https://github.com/refactorfirst/RefactorFirst/blob/src/main/java/org/hjug/refactorfirst/B.java\" target=\"_blank\" label=\"B\"];\n"
                + "org_hjug_refactorfirst_C [URL=\"https://github.com/refactorfirst/RefactorFirst/blob/src/main/java/org/hjug/refactorfirst/C.java\" target=\"_blank\" label=\"C\"];\n"
                + "}`;";

        assertEquals(expectedDot, dot);
    }

    @Test
    void getParentPackage() {
        assertEquals("org.hjug.parentPackage", mavenReport.getParentNamespace("org.hjug.parentPackage.package"));
    }

    @Test
    void getPackageOfClass() {
        assertEquals("org.hjug.package", mavenReport.getParentNamespace("org.hjug.package.ClassA"));
    }
}
