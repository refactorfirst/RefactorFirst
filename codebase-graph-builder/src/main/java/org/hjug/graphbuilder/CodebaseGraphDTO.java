package org.hjug.graphbuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.ClassDisharmony;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.MethodDisharmony;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

@Getter
@EqualsAndHashCode
@ToString
public class CodebaseGraphDTO {

    private final Graph<String, DefaultWeightedEdge> classReferencesGraph;
    private final Graph<String, DefaultWeightedEdge> packageReferencesGraph;
    private final Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> classRelationshipsInPackageRelationship;
    // used for looking up files where classes reside
    private final Map<String, String> classToSourceFilePathMapping;

    private final List<ClassDisharmony> classDisharmonies;
    private final List<MethodDisharmony> methodDisharmonies;

    public CodebaseGraphDTO(
            Graph<String, DefaultWeightedEdge> classReferencesGraph,
            Graph<String, DefaultWeightedEdge> packageReferencesGraph,
            Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> classRelationshipsInPackageRelationship,
            Map<String, String> classToSourceFilePathMapping,
            List<ClassDisharmony> classDisharmonies,
            List<MethodDisharmony> methodDisharmonies) {
        this.classReferencesGraph = classReferencesGraph;
        this.packageReferencesGraph = packageReferencesGraph;
        this.classRelationshipsInPackageRelationship = classRelationshipsInPackageRelationship;
        this.classToSourceFilePathMapping = classToSourceFilePathMapping;
        this.classDisharmonies = classDisharmonies;
        this.methodDisharmonies = methodDisharmonies;
    }

    public List<ClassDisharmony> getClassDisharmoniesOfType(String disharmonyType) {
        return classDisharmonies.stream()
                .filter(d -> disharmonyType.equals(d.getDisharmonyType()))
                .collect(Collectors.toList());
    }

    public List<MethodDisharmony> getMethodDisharmoniesOfType(String disharmonyType) {
        return methodDisharmonies.stream()
                .filter(d -> disharmonyType.equals(d.getDisharmonyType()))
                .collect(Collectors.toList());
    }
}
