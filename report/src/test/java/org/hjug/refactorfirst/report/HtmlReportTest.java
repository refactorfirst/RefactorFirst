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
    void buildClassCycleDot() {
        Graph<String, DefaultWeightedEdge> classGraph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        classGraph.addVertex("A");
        classGraph.addVertex("B");
        classGraph.addVertex("C");
        classGraph.addEdge("A", "B");
        classGraph.addEdge("B", "C");
        classGraph.addEdge("C", "A");
        classGraph.setEdgeWeight("A", "B", 2);

        String cycleName = "Test";
        List<CycleNode> cycleNodes = new ArrayList<>();
        RankedCycle rankedCycle = new RankedCycle(cycleName, classGraph.vertexSet(), classGraph.edgeSet(), cycleNodes);

        HtmlReport htmlReport = new HtmlReport();
        CodebaseGraphDTO dto = mock(CodebaseGraphDTO.class);
        HashMap<String, String> map = new HashMap<>();
        map.put("A", "/src/main/java/org/hjug/refactorfirst/A.java");
        map.put("B", "/src/main/java/org/hjug/refactorfirst/B.java");
        map.put("C", "/src/main/java/org/hjug/refactorfirst/C.java");
        when(dto.getClassToSourceFilePathMapping()).thenReturn(map);
        String repoUrl = "https://github.com/refactorfirst/RefactorFirst/blob";
        String dot = htmlReport.buildClassCycleDot(classGraph, rankedCycle, repoUrl, dto);
        String expectedDot = "`strict digraph G {\n"
                + "A -> B [ label = \"2\" weight = \"2\" ];\n"
                + "B -> C [ label = \"1\" weight = \"1\" ];\n"
                + "C -> A [ label = \"1\" weight = \"1\" ];\n"
                + "A [URL=\"https://github.com/refactorfirst/RefactorFirst/blob/src/main/java/org/hjug/refactorfirst/A.java\" target=\"_blank\"];\n"
                + "B [URL=\"https://github.com/refactorfirst/RefactorFirst/blob/src/main/java/org/hjug/refactorfirst/B.java\" target=\"_blank\"];\n"
                + "C [URL=\"https://github.com/refactorfirst/RefactorFirst/blob/src/main/java/org/hjug/refactorfirst/C.java\" target=\"_blank\"];\n"
                + "}`;";

        assertEquals(expectedDot, dot);
    }
}
