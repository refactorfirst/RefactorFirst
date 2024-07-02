package com.ideacrest.cycledetector;

import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.util.mxCellRenderer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultEdge;

public class CircularReferenceChecker {

    /**
     * Detects cycles in the classReferencesGraph parameter
     * and stores the cycles of a class as a subgraph in a Map
     *
     * @param classReferencesGraph
     * @return a Map of Class and its Cycle Graph
     */
    public Map<String, AsSubgraph<String, DefaultEdge>> detectCycles(Graph<String, DefaultEdge> classReferencesGraph) {
        Map<String, AsSubgraph<String, DefaultEdge>> cyclesForEveryVertexMap = new HashMap<>();
        CycleDetector<String, DefaultEdge> cycleDetector = new CycleDetector<>(classReferencesGraph);
        cycleDetector.findCycles().forEach(v -> {
            AsSubgraph<String, DefaultEdge> subGraph =
                    new AsSubgraph<>(classReferencesGraph, cycleDetector.findCyclesContainingVertex(v));
            cyclesForEveryVertexMap.put(v, subGraph);
        });
        return cyclesForEveryVertexMap;
    }

    /**
     * Given graph and image name, use jgrapht to create .png file of graph in given outputDirectory.
     * Create outputDirectory if it does not exist.
     *
     * @param outputDirectoryPath
     * @param subGraph
     * @param imageName
     * @throws IOException
     */
    public void createImage(String outputDirectoryPath, Graph<String, DefaultEdge> subGraph, String imageName)
            throws IOException {
        new File(outputDirectoryPath).mkdirs();
        File imgFile = new File(outputDirectoryPath + "/graph" + imageName + ".png");
        if (imgFile.createNewFile()) {
            JGraphXAdapter<String, DefaultEdge> graphAdapter = new JGraphXAdapter<>(subGraph);
            mxIGraphLayout layout = new mxCircleLayout(graphAdapter);
            layout.execute(graphAdapter.getDefaultParent());

            BufferedImage image = mxCellRenderer.createBufferedImage(graphAdapter, null, 2, Color.WHITE, true, null);
            if (image != null) {
                ImageIO.write(image, "PNG", imgFile);
            }
        }
    }
}
