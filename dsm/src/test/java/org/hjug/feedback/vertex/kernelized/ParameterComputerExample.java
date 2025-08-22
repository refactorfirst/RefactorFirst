package org.hjug.feedback.vertex.kernelized;

import org.hjug.feedback.SuperTypeToken;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.Set;

public class ParameterComputerExample {

    public static void main(String[] args) {
        // Create a sample directed graph with cycles
        Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

        // Add vertices
        for (int i = 0; i < 6; i++) {
            graph.addVertex("V" + i);
        }

        // Add edges to create cycles
        graph.addEdge("V0", "V1");
        graph.addEdge("V1", "V2");
        graph.addEdge("V2", "V0");  // First cycle
        graph.addEdge("V2", "V3");
        graph.addEdge("V3", "V4");
        graph.addEdge("V4", "V5");
        graph.addEdge("V5", "V2");  // Second cycle

        // Create parameter computer
        ParameterComputer<String, DefaultEdge> computer = new ParameterComputer<>(new SuperTypeToken<>() {});

        try {
            // Compute parameters without modulator
            ParameterComputer.Parameters params1 = computer.computeParameters(graph);
            System.out.println("Parameters without modulator: " + params1);

            // Compute parameters with a modulator
            Set<String> modulator = Set.of("V2"); // V2 connects both cycles
            ParameterComputer.Parameters params2 = computer.computeParameters(graph, modulator);
            System.out.println("Parameters with modulator {V2}: " + params2);

            // Find optimal modulator automatically
            ParameterComputer.Parameters params3 =
                    computer.computeParametersWithOptimalModulator(graph, 2);
            System.out.println("Parameters with optimal modulator: " + params3);

        } finally {
            computer.shutdown();
        }
    }
}
