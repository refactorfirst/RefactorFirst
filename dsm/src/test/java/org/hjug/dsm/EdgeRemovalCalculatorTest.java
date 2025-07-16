package org.hjug.dsm;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EdgeRemovalCalculatorTest {

    DSM<String, DefaultWeightedEdge> dsm;

    @Test
    void getImpactOfEdgesAboveDiagonalIfRemoved() {
        SimpleDirectedWeightedGraph<String, DefaultWeightedEdge> graph =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        dsm = new DSM<>(graph);
        dsm.addActivity("A");
        dsm.addActivity("B");
        dsm.addActivity("C");
        dsm.addActivity("D");

        // Cycle 1
        dsm.addDependency("A", "B", 1);
        dsm.addDependency("B", "C", 2);
        dsm.addDependency("C", "D", 3);
        dsm.addDependency("B", "A", 6); // Adding a cycle
        dsm.addDependency("C", "A", 5); // Adding a cycle
        dsm.addDependency("D", "A", 4); // Adding a cycle

        // Cycle 2
        dsm.addActivity("E");
        dsm.addActivity("F");
        dsm.addActivity("G");
        dsm.addActivity("H");
        dsm.addDependency("E", "F", 2);
        dsm.addDependency("F", "G", 7);
        dsm.addDependency("G", "H", 9);
        dsm.addDependency("H", "E", 9); // create cycle

        dsm.addDependency("A", "E", 9);
        dsm.addDependency("E", "A", 3); // create cycle between cycles

        EdgeRemovalCalculator edgeRemovalCalculator = new EdgeRemovalCalculator(graph, dsm);

        List<EdgeToRemoveInfo> infos = edgeRemovalCalculator.getImpactOfEdgesAboveDiagonalIfRemoved(50);
        assertEquals(5, infos.size());

        assertEquals("(D : A)", infos.get(0).getEdge().toString());
        assertEquals(3, infos.get(0).getNewCycleCount());
    }
}
