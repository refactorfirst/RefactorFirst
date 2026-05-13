package org.hjug.graphbuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.ClassDisharmony;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.MethodDisharmony;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Data
public class CodebaseGraphDTO {

    private final Graph<String, DefaultWeightedEdge> classReferencesGraph;
    private final Graph<String, DefaultWeightedEdge> packageReferencesGraph;
    // used for looking up files where classes reside
    private final Map<String, String> classToSourceFilePathMapping;

    private final List<ClassDisharmony> classDisharmonies;
    private final List<MethodDisharmony> methodDisharmonies;

    public List<ClassDisharmony> getClassDisharmoniesOfType(String disharmonyType) {
        return classDisharmonies.stream()
                .filter(d -> disharmonyType.equals(d.getDisharmonyType()))
                .collect(Collectors.toList());
    }
}
