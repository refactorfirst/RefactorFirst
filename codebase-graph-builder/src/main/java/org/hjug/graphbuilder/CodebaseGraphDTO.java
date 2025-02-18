package org.hjug.graphbuilder;

import java.util.Map;
import lombok.Data;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Data
public class CodebaseGraphDTO {

    private final Graph<String, DefaultWeightedEdge> classReferencesGraph;
    private final Graph<String, DefaultWeightedEdge> packageReferencesGraph;
    // used for looking up files where classes reside
    private final Map<String, String> classToSourceFilePathMapping;
}
