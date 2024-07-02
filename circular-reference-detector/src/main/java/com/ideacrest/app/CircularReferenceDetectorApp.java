package com.ideacrest.app;

import com.ideacrest.cycledetector.CircularReferenceChecker;
import com.ideacrest.parser.JavaProjectParser;
import java.io.IOException;
import java.util.Map;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultEdge;

/**
 * Command line application to detect circular references in a java project.
 * Takes two arguments : source folder of java project, directory to store images of the circular reference graphs.
 *
 * @author nikhil_pereira
 *
 */
public class CircularReferenceDetectorApp {

    public static void main(String[] args) {
        CircularReferenceDetectorApp circularReferenceDetectorApp = new CircularReferenceDetectorApp();
        circularReferenceDetectorApp.launchApp(args);
    }

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
            String outputDirectoryPath = args[1];
            JavaProjectParser javaProjectParser = new JavaProjectParser();
            try {
                Graph<String, DefaultEdge> classReferencesGraph =
                        javaProjectParser.getClassReferences(srcDirectoryPath);
                detectAndStoreCyclesInDirectory(outputDirectoryPath, classReferencesGraph);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void detectAndStoreCyclesInDirectory(
            String outputDirectoryPath, Graph<String, DefaultEdge> classReferencesGraph) {
        CircularReferenceChecker circularReferenceChecker = new CircularReferenceChecker();
        Map<String, AsSubgraph<String, DefaultEdge>> cyclesForEveryVertexMap =
                circularReferenceChecker.detectCyles(classReferencesGraph);
        cyclesForEveryVertexMap.forEach((vertex, subGraph) -> {
            try {
                circularReferenceChecker.createImage(outputDirectoryPath, subGraph, vertex);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
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
