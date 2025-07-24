package org.hjug.dsm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DSMTest {

    DSM<String, DefaultWeightedEdge> dsm = new DSM(new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class));

    @BeforeEach
    void setUp() {
        dsm.addActivity("A");
        dsm.addActivity("B");
        dsm.addActivity("C");
        dsm.addActivity("D");

        dsm.addDependency("A", "B", 1);
        dsm.addDependency("B", "C", 2);
        dsm.addDependency("C", "D", 3);
        dsm.addDependency("B", "A", 6); // Adding a cycle
        dsm.addDependency("C", "A", 5); // Adding a cycle
        dsm.addDependency("D", "A", 4); // Adding a cycle

        /*
              D C B A
            D - 0 0 4
            C 3 - 0 5
            B 0 2 - 6
            A 0 0 1 -
        */

        dsm.addActivity("E");
        dsm.addActivity("F");
        dsm.addActivity("G");
        dsm.addActivity("H");
        dsm.addDependency("D", "C", 2);
        dsm.addDependency("A", "H", 7);
        dsm.addDependency("E", "C", 9);
        dsm.addDependency("E", "H", 2);
        dsm.addDependency("G", "E", 2);
        dsm.addDependency("H", "D", 9);
        dsm.addDependency("H", "G", 5);

        //                dsm.printDSM();
    }

    @Test
    void optimalBackwardEdgeToRemove() {
        // Identify which edge above the diagonal should be removed first
        DefaultWeightedEdge edge = dsm.getFirstLowestWeightEdgeAboveDiagonalToRemove();
        assertEquals("(D : C)", edge.toString());
    }

    @Test
    void optimalBackwardEdgeToRemoveWithWeightOfOne() {
        DSM<String, DefaultWeightedEdge> dsm2 = new DSM<>(new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class));
        dsm2.addActivity("A");
        dsm2.addActivity("B");
        dsm2.addActivity("C");

        dsm2.addDependency("A", "B", 1);
        dsm2.addDependency("B", "C", 1);
        dsm2.addDependency("B", "A", 1);
        dsm2.addDependency("C", "A", 1);

        // Identify which edge above the diagonal should be removed first
        DefaultWeightedEdge edge = dsm2.getFirstLowestWeightEdgeAboveDiagonalToRemove();
        assertEquals("(C : A)", edge.toString());
    }

    @Test
    void minWeightBackwardEdges() {
        // Identify which edge above the diagonal in the set of cycles should be removed first
        List<DefaultWeightedEdge> edges = dsm.getMinimumWeightEdgesAboveDiagonal();
        assertEquals(2, edges.size());
        assertEquals("(D : C)", edges.get(0).toString());
        assertEquals("(E : H)", edges.get(1).toString());
    }

    @Test
    void edgesAboveDiagonal() {
        // Identify edges above the diagonal
        List<DefaultWeightedEdge> edges = dsm.getEdgesAboveDiagonal();
        assertEquals(5, edges.size());
        assertEquals("(D : C)", edges.get(0).toString());
        assertEquals("(D : A)", edges.get(1).toString());
        assertEquals("(C : A)", edges.get(2).toString());
        assertEquals("(B : A)", edges.get(3).toString());
        assertEquals("(E : H)", edges.get(4).toString());
    }
}
