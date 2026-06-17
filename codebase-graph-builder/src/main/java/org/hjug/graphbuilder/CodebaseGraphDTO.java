package org.hjug.graphbuilder;

import java.util.HashMap;
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
    private final Map<String, Long> disharmonyCountByClass;

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
        this.disharmonyCountByClass = buildDisharmonyIndex(classDisharmonies, methodDisharmonies);
    }

    private static Map<String, Long> buildDisharmonyIndex(
            List<ClassDisharmony> classDisharmonies, List<MethodDisharmony> methodDisharmonies) {
        Map<String, Long> counts = new HashMap<>();
        classDisharmonies.forEach(d -> counts.merge(d.getMetrics().getClassName(), 1L, Long::sum));
        methodDisharmonies.forEach(m -> counts.merge(m.getClassName(), 1L, Long::sum));
        return counts;
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

    public long getClassDisharmonyCountForClass(String classFqn) {
        return disharmonyCountByClass.getOrDefault(classFqn, 0L);
    }
}
