package org.hjug.app;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.hjug.cycledetector.CircularReferenceChecker;
import org.hjug.parser.JavaProjectParser;
import org.jgrapht.Graph;
import org.jgrapht.alg.flow.GusfieldGomoryHuCutTree;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.AsUndirectedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 * Command line application to detect circular references in a java project.
 * Takes two arguments : source folder of java project, directory to store images of the circular reference graphs.
 *
 * @author nikhil_pereira
 */
public class CircularReferenceDetectorApp {

    private Map<String, AsSubgraph> renderedSubGraphs = new HashMap<>();

    //    public static void main(String[] args) {
    //        CircularReferenceDetectorApp circularReferenceDetectorApp = new CircularReferenceDetectorApp();
    //        circularReferenceDetectorApp.launchApp(args);
    //    }

    /**
     * Parses source project files and creates a graph of class references of the java project.
     * Detects cycles in the class references graph and stores the cycle graphs in the given output directory
     *
     * @param args
     */
    public void launchApp(String[] args) {
        if (!validateArgs(args)) {
            printCommandUsage();
        } else {
            String srcDirectoryPath = args[0];
            JavaProjectParser javaProjectParser = new JavaProjectParser();
            try {
                Graph<String, DefaultWeightedEdge> classReferencesGraph =
                        javaProjectParser.getClassReferences(srcDirectoryPath);
                detectAndStoreCyclesInDirectory(classReferencesGraph);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void detectAndStoreCyclesInDirectory(Graph<String, DefaultWeightedEdge> classReferencesGraph) {
        CircularReferenceChecker circularReferenceChecker = new CircularReferenceChecker();
        Map<String, AsSubgraph<String, DefaultWeightedEdge>> cyclesForEveryVertexMap =
                circularReferenceChecker.detectCycles(classReferencesGraph);
        cyclesForEveryVertexMap.forEach((vertex, subGraph) -> {
            int vertexCount = subGraph.vertexSet().size();
            int edgeCount = subGraph.edgeSet().size();
            if (vertexCount > 1 && edgeCount > 1 && !isDuplicateSubGraph(subGraph, vertex)) {
                renderedSubGraphs.put(vertex, subGraph);
                System.out.println("Vertex: " + vertex + " vertex count: " + vertexCount + " edge count: " + edgeCount);
                GusfieldGomoryHuCutTree<String, DefaultWeightedEdge> gusfieldGomoryHuCutTree =
                        new GusfieldGomoryHuCutTree<>(new AsUndirectedGraph<>(subGraph));
                double minCut = gusfieldGomoryHuCutTree.calculateMinCut();
                System.out.println("Min cut weight: " + minCut);
                Set<DefaultWeightedEdge> minCutEdges = gusfieldGomoryHuCutTree.getCutEdges();
                System.out.println("Minimum Cut Edges:");
                for (DefaultWeightedEdge minCutEdge : minCutEdges) {
                    System.out.println(minCutEdge);
                }
            }
        });
    }

    private boolean isDuplicateSubGraph(AsSubgraph<String, DefaultWeightedEdge> subGraph, String vertex) {
        if (!renderedSubGraphs.isEmpty()) {
            for (AsSubgraph renderedSubGraph : renderedSubGraphs.values()) {
                if (renderedSubGraph.vertexSet().size() == subGraph.vertexSet().size()
                        && renderedSubGraph.edgeSet().size()
                                == subGraph.edgeSet().size()
                        && renderedSubGraph.vertexSet().contains(vertex)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean validateArgs(String[] args) {
        return args.length == 2;
    }

    private void printCommandUsage() {
        System.out.println("Usage:\n"
                + "argument 1 : file path of source directory of the java project."
                + "argument 2 : output directory path to store images of the circular reference graphs.");
    }
}
