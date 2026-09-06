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

/**
 * Data transfer object containing the complete codebase graph analysis results.
 * Includes class and package reference graphs, class-to-package relationships,
 * source file mappings, and detected disharmonies (antipatterns).
 */
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

    /**
     * Creates a new CodebaseGraphDTO.
     *
     * @param classReferencesGraph                    the class reference graph
     * @param packageReferencesGraph                  the package reference graph
     * @param classRelationshipsInPackageRelationship the class-to-package relationships
     * @param classToSourceFilePathMapping            the class to source file path mapping
     * @param classDisharmonies                       the list of class disharmonies
     * @param methodDisharmonies                      the list of method disharmonies
     */
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

    /**
     * Returns class disharmonies filtered by type.
     *
     * @param disharmonyType the disharmony type to filter by
     * @return list of matching class disharmonies
     */
    public List<ClassDisharmony> getClassDisharmoniesOfType(String disharmonyType) {
        return classDisharmonies.stream()
                .filter(d -> disharmonyType.equals(d.getDisharmonyType()))
                .collect(Collectors.toList());
    }

    /**
     * Returns method disharmonies filtered by type.
     *
     * @param disharmonyType the disharmony type to filter by
     * @return list of matching method disharmonies
     */
    public List<MethodDisharmony> getMethodDisharmoniesOfType(String disharmonyType) {
        return methodDisharmonies.stream()
                .filter(d -> disharmonyType.equals(d.getDisharmonyType()))
                .collect(Collectors.toList());
    }
}
