package org.hjug.refactorfirst.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.*;
import org.hjug.cbc.CycleNode;
import org.hjug.cbc.RankedCycle;
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
        classGraph.addVertex("A");
        classGraph.addVertex("B");
        classGraph.addVertex("C");
        classGraph.addEdge("A", "B");
        classGraph.addEdge("B", "C");
        classGraph.addEdge("C", "A");
        classGraph.setEdgeWeight("A", "B", 2);

        String cycleName = "Test";
        List<CycleNode> cycleNodes = new ArrayList<>();
        RankedCycle rankedCycle =
                new RankedCycle(cycleName, 0, classGraph.vertexSet(), classGraph.edgeSet(), 0, null, cycleNodes);

        HtmlReport htmlReport = new HtmlReport();
        String dot = htmlReport.buildCycleDot(classGraph, rankedCycle);

        String expectedDot = "`strict digraph G {\n"
                + "A -> B [ label = \"2\" weight = \"2\" ];\n"
                + "B -> C [ label = \"1\" weight = \"1\" ];\n"
                + "C -> A [ label = \"1\" weight = \"1\" ];\n"
                + "A;\n"
                + "B;\n"
                + "C;\n"
                + "}`;";

        assertEquals(expectedDot, dot);
    }
}
