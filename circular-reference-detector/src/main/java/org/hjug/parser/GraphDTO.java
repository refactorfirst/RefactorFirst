package org.hjug.parser;

import lombok.Data;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.Map;

@Data
public class GraphDTO {

    private final Graph<String, DefaultWeightedEdge> classReferencesGraph;
    private final Graph<String, DefaultWeightedEdge> packageReferencesGraph;
    // used for looking up files where classes reside
    private final Map<String, String> classToSourceFilePathMapping;
}
