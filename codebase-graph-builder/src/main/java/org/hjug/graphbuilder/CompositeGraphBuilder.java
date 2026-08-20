package org.hjug.graphbuilder;

import java.io.IOException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.graphbuilder.JavaSourceFileGraphBuilder;
import org.hjug.graphbuilder.graphbuilder.KotlinSourceFileGraphBuilder;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.ClassDisharmony;
import org.hjug.graphbuilder.metrics.DisharmonyDetector.MethodDisharmony;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 * Orchestrates Java and Kotlin source-file graph builders, merging
 * their independent {@link CodebaseGraphDTO}s into a single unified
 * graph. Kotlin analysis runs unconditionally; producing it requires
 * {@code org.openrewrite:rewrite-kotlin} on the classpath (a compile
 * dependency of this module).
 *
 * <p>If the Kotlin build throws (parse error, IO failure, etc.), the
 * composite falls back to returning the Java-only DTO and logs a
 * warning.
 */
@Slf4j
public class CompositeGraphBuilder {

    /**
     * Build a unified {@link CodebaseGraphDTO} from a directory that may contain
     * both Java and Kotlin source files.
     *
     * @param repositoryPath     path to the source directory
     * @param excludeTests      whether to exclude test files
     * @param testSourceDirectory test source directory pattern
     * @return a merged CodebaseGraphDTO
     * @throws IOException if parsing fails
     */
    public CodebaseGraphDTO getCodebaseGraphDTO(String repositoryPath, boolean excludeTests, String testSourceDirectory)
            throws IOException {
        if (repositoryPath == null || repositoryPath.isEmpty()) {
            throw new IllegalArgumentException("Source directory cannot be null or empty");
        }

        GraphBuilderConfig config = GraphBuilderConfig.builder()
                .excludeTests(excludeTests)
                .testSourceDirectory(testSourceDirectory)
                .build();

        return getCodebaseGraphDTO(repositoryPath, config);
    }

    public CodebaseGraphDTO getCodebaseGraphDTO(String repositoryPath, GraphBuilderConfig config) throws IOException {
        return getCodebaseGraphDTO(repositoryPath, "", config);
    }

    /**
     * Build a unified {@link CodebaseGraphDTO} from a directory that may contain
     * both Java and Kotlin source files, with explicit repository root for
     * URL canonicalization in multi-module projects.
     *
     * @param repositoryPath     path to the source directory (source root)
     * @param repositoryRoot     path to the Git repository root for URL canonicalization;
     *                           may be empty or equal to repositoryPath for single-module projects
     * @param config             graph-builder configuration
     * @return a merged CodebaseGraphDTO
     * @throws IOException if parsing fails
     */
    public CodebaseGraphDTO getCodebaseGraphDTO(String repositoryPath, String repositoryRoot, GraphBuilderConfig config)
            throws IOException {
        // Always build the Java graph
        JavaSourceFileGraphBuilder javaBuilder = new JavaSourceFileGraphBuilder();
        CodebaseGraphDTO javaDto = javaBuilder.buildGraph(repositoryPath, repositoryRoot, config);

        // Always build the Kotlin graph and merge. rewrite-kotlin is a
        // compile dependency of this module, so the Kotlin parser is always
        // on the classpath; a failure here (parse error, IO, etc.) falls back
        // to the Java-only graph.
        try {
            KotlinSourceFileGraphBuilder kotlinBuilder = new KotlinSourceFileGraphBuilder();
            CodebaseGraphDTO kotlinDto = kotlinBuilder.buildGraph(repositoryPath, repositoryRoot, config);
            return merge(javaDto, kotlinDto);
        } catch (Exception e) {
            log.warn("Kotlin analysis failed; falling back to Java-only graph", e);
            return javaDto;
        }
    }

    /**
     * Build a unified {@link CodebaseGraphDTO} from a directory that may contain
     * both Java and Kotlin source files, with explicit repository root for
     * URL canonicalization in multi-module projects.
     *
     * @param repositoryPath     path to the source directory
     * @param repositoryRoot     path to the Git repository root for URL canonicalization;
     *                           may be empty or equal to repositoryPath for single-module projects
     * @param excludeTests      whether to exclude test files
     * @param testSourceDirectory test source directory pattern
     * @return a merged CodebaseGraphDTO
     * @throws IOException if parsing fails
     */
    public CodebaseGraphDTO getCodebaseGraphDTO(
            String repositoryPath, String repositoryRoot, boolean excludeTests, String testSourceDirectory)
            throws IOException {
        if (repositoryPath == null || repositoryPath.isEmpty()) {
            throw new IllegalArgumentException("Source directory cannot be null or empty");
        }

        GraphBuilderConfig config = GraphBuilderConfig.builder()
                .excludeTests(excludeTests)
                .testSourceDirectory(testSourceDirectory)
                .build();

        return getCodebaseGraphDTO(repositoryPath, repositoryRoot, config);
    }

    static CodebaseGraphDTO merge(CodebaseGraphDTO javaDto, CodebaseGraphDTO kotlinDto) {
        Graph<String, DefaultWeightedEdge> mergedClassGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Graph<String, DefaultWeightedEdge> mergedPackageGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> mergedClassRelationships = new HashMap<>();
        Map<String, String> mergedSourcePathMapping = new HashMap<>();

        // Merge Java class graph
        mergeGraph(javaDto.getClassReferencesGraph(), mergedClassGraph);
        mergeGraph(kotlinDto.getClassReferencesGraph(), mergedClassGraph);

        // Merge package graph
        mergeGraph(javaDto.getPackageReferencesGraph(), mergedPackageGraph);
        mergeGraph(kotlinDto.getPackageReferencesGraph(), mergedPackageGraph);

        // Merge class-to-package-relationship mapping
        mergeClassRelationships(javaDto, mergedPackageGraph, mergedClassRelationships);
        mergeClassRelationships(kotlinDto, mergedPackageGraph, mergedClassRelationships);

        // Merge source path mapping
        mergedSourcePathMapping.putAll(javaDto.getClassToSourceFilePathMapping());
        mergedSourcePathMapping.putAll(kotlinDto.getClassToSourceFilePathMapping());

        // Merge disharmonies
        List<ClassDisharmony> mergedClassDisharmonies = new ArrayList<>();
        mergedClassDisharmonies.addAll(javaDto.getClassDisharmonies());
        mergedClassDisharmonies.addAll(kotlinDto.getClassDisharmonies());

        List<MethodDisharmony> mergedMethodDisharmonies = new ArrayList<>();
        mergedMethodDisharmonies.addAll(javaDto.getMethodDisharmonies());
        mergedMethodDisharmonies.addAll(kotlinDto.getMethodDisharmonies());

        // Reconcile fabricated cross-language vertices against real declarations
        reconcileUnattributedVertices(mergedClassGraph, mergedPackageGraph, mergedSourcePathMapping);

        return new CodebaseGraphDTO(
                mergedClassGraph,
                mergedPackageGraph,
                mergedClassRelationships,
                mergedSourcePathMapping,
                mergedClassDisharmonies,
                mergedMethodDisharmonies);
    }

    /**
     * Reconciles fabricated unattributed vertices (created by {@link UnattributedTypeFqnResolver})
     * against real declarations in the merged source path mapping.
     * <p>
     * When a type reference cannot be attributed (e.g., a Java file referencing a Kotlin class
     * outside the parser's classpath), {@link UnattributedTypeFqnResolver} fabricates an FQN using
     * the caller's package. This method finds such fabricated vertices by looking for vertices
     * that are NOT in the source path mapping but whose simple name matches exactly one real
     * class in the mapping. When a unique match is found, the fabricated vertex is contracted
     * into the canonical vertex: edges are redirected and weights summed.
     * </p>
     * <p>
     * <strong>External class removal:</strong> Fabricated vertices with <strong>zero matching
     * real declarations</strong> (i.e., truly external library classes like JavaFX) are removed
     * entirely from the class graph, along with their edges. This prevents external classes
     * from appearing in the codebase graph due to package fabrication.
     * </p>
     * <p>
     * This also recovers cross-package edges in the package graph that were previously
     * lost because the fabricated vertex's package matched the caller's package.
     * </p>
     *
     * @param classGraph the merged class graph to reconcile
     * @param packageGraph the merged package graph to update with recovered cross-package edges
     * @param sourcePathMapping the merged mapping of FQN to source file path (only real declarations)
     */
    static void reconcileUnattributedVertices(
            Graph<String, DefaultWeightedEdge> classGraph,
            Graph<String, DefaultWeightedEdge> packageGraph,
            Map<String, String> sourcePathMapping) {
        // Build index: simple name -> list of canonical FQNs that have a source mapping
        Map<String, List<String>> bySimpleName = new HashMap<>();
        for (String fqn : sourcePathMapping.keySet()) {
            String simpleName = simpleName(fqn);
            bySimpleName.computeIfAbsent(simpleName, k -> new ArrayList<>()).add(fqn);
        }

        // Find fabricated vertices: vertices in classGraph but NOT in sourcePathMapping
        Set<String> verticesToCheck = new HashSet<>(classGraph.vertexSet());
        verticesToCheck.removeAll(sourcePathMapping.keySet());

        for (String fabricatedFqn : verticesToCheck) {
            String simpleName = simpleName(fabricatedFqn);
            String fabricatedPkg = packageName(fabricatedFqn);
            List<String> candidates = bySimpleName.get(simpleName);

            String canonicalFqn = null;

            if (candidates != null && !candidates.isEmpty()) {
                if (candidates.size() == 1) {
                    // Unique match: reconcile with canonical vertex
                    canonicalFqn = candidates.get(0);
                } else {
                    // MULTIPLE CANDIDATES: Try package-aware matching
                    // Prefer candidate whose package matches the fabricated vertex's package
                    for (String candidate : candidates) {
                        if (packageName(candidate).equals(fabricatedPkg)) {
                            canonicalFqn = candidate;
                            break;
                        }
                    }
                    // If no package match, leave ambiguous (don't reconcile)
                }

                if (canonicalFqn != null && !canonicalFqn.equals(fabricatedFqn)) {
                    contractVertex(classGraph, packageGraph, fabricatedFqn, canonicalFqn);
                }
            } else {
                // ZERO MATCH: This is an external class (e.g., JavaFX) that was fabricated
                // into the caller's package. Remove it entirely.
                removeFabricatedExternalVertex(classGraph, packageGraph, fabricatedFqn);
            }
            // If multiple candidates and no package match, leave fabricated vertex untouched
            // (could be two real classes with same simple name in different packages)
        }
    }

    /**
     * Removes a fabricated vertex that corresponds to an external class.
     * Also removes all its incoming/outgoing edges.
     *
     * @param classGraph the class graph
     * @param packageGraph the package graph
     * @param fabricatedFqn the FQN of the fabricated vertex to remove
     */
    private static void removeFabricatedExternalVertex(
            Graph<String, DefaultWeightedEdge> classGraph,
            Graph<String, DefaultWeightedEdge> packageGraph,
            String fabricatedFqn) {
        // Remove all incoming edges
        Set<DefaultWeightedEdge> incomingEdges = new HashSet<>(classGraph.incomingEdgesOf(fabricatedFqn));
        for (DefaultWeightedEdge edge : incomingEdges) {
            classGraph.removeEdge(edge);
        }

        // Remove all outgoing edges
        Set<DefaultWeightedEdge> outgoingEdges = new HashSet<>(classGraph.outgoingEdgesOf(fabricatedFqn));
        for (DefaultWeightedEdge edge : outgoingEdges) {
            classGraph.removeEdge(edge);
        }

        // Remove the vertex
        classGraph.removeVertex(fabricatedFqn);

        // Note: We don't modify packageGraph here since the fabricated vertex's
        // package matched the caller's package (which is a real codebase package).
        // The package graph edge was a self-edge or intra-package edge anyway.
        // If cross-package edges were created, they would be from one codebase
        // package to another, which is valid.
    }

    private static String simpleName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }

    private static void contractVertex(
            Graph<String, DefaultWeightedEdge> classGraph,
            Graph<String, DefaultWeightedEdge> packageGraph,
            String fabricatedFqn,
            String canonicalFqn) {
        // Ensure canonical vertex exists in classGraph
        if (!classGraph.containsVertex(canonicalFqn)) {
            classGraph.addVertex(canonicalFqn);
        }

        // Redirect incoming edges to fabricated -> canonical
        Set<DefaultWeightedEdge> incomingEdges = new HashSet<>(classGraph.incomingEdgesOf(fabricatedFqn));
        for (DefaultWeightedEdge edge : incomingEdges) {
            String source = classGraph.getEdgeSource(edge);
            double weight = classGraph.getEdgeWeight(edge);
            classGraph.removeEdge(edge);

            if (!classGraph.containsEdge(source, canonicalFqn)) {
                DefaultWeightedEdge newEdge = classGraph.addEdge(source, canonicalFqn);
                classGraph.setEdgeWeight(newEdge, weight);
                // Add corresponding package edge if cross-package
                addPackageEdgeIfCrossPackage(packageGraph, source, canonicalFqn);
            } else {
                DefaultWeightedEdge existingEdge = classGraph.getEdge(source, canonicalFqn);
                classGraph.setEdgeWeight(existingEdge, classGraph.getEdgeWeight(existingEdge) + weight);
            }
        }

        // Redirect outgoing edges from fabricated -> canonical
        Set<DefaultWeightedEdge> outgoingEdges = new HashSet<>(classGraph.outgoingEdgesOf(fabricatedFqn));
        for (DefaultWeightedEdge edge : outgoingEdges) {
            String target = classGraph.getEdgeTarget(edge);
            double weight = classGraph.getEdgeWeight(edge);
            classGraph.removeEdge(edge);

            if (!classGraph.containsEdge(canonicalFqn, target)) {
                DefaultWeightedEdge newEdge = classGraph.addEdge(canonicalFqn, target);
                classGraph.setEdgeWeight(newEdge, weight);
                // Add corresponding package edge if cross-package
                addPackageEdgeIfCrossPackage(packageGraph, canonicalFqn, target);
            } else {
                DefaultWeightedEdge existingEdge = classGraph.getEdge(canonicalFqn, target);
                classGraph.setEdgeWeight(existingEdge, classGraph.getEdgeWeight(existingEdge) + weight);
            }
        }

        // Remove the fabricated vertex
        classGraph.removeVertex(fabricatedFqn);
    }

    private static void addPackageEdgeIfCrossPackage(
            Graph<String, DefaultWeightedEdge> packageGraph, String classSource, String classTarget) {
        String pkgSource = packageName(classSource);
        String pkgTarget = packageName(classTarget);
        if (!pkgSource.equals(pkgTarget)) {
            if (!packageGraph.containsVertex(pkgSource)) {
                packageGraph.addVertex(pkgSource);
            }
            if (!packageGraph.containsVertex(pkgTarget)) {
                packageGraph.addVertex(pkgTarget);
            }
            if (!packageGraph.containsEdge(pkgSource, pkgTarget)) {
                DefaultWeightedEdge newEdge = packageGraph.addEdge(pkgSource, pkgTarget);
                packageGraph.setEdgeWeight(newEdge, 1);
            } else {
                DefaultWeightedEdge existingEdge = packageGraph.getEdge(pkgSource, pkgTarget);
                packageGraph.setEdgeWeight(existingEdge, packageGraph.getEdgeWeight(existingEdge) + 1);
            }
        }
    }

    private static String packageName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(0, lastDot) : "";
    }

    private static void mergeGraph(
            Graph<String, DefaultWeightedEdge> source, Graph<String, DefaultWeightedEdge> target) {
        // Add vertices
        for (String vertex : source.vertexSet()) {
            target.addVertex(vertex);
        }
        // Add edges, preserving weights
        for (DefaultWeightedEdge edge : source.edgeSet()) {
            String sourceVertex = source.getEdgeSource(edge);
            String targetVertex = source.getEdgeTarget(edge);
            double weight = source.getEdgeWeight(edge);

            if (!target.containsEdge(sourceVertex, targetVertex)) {
                DefaultWeightedEdge newEdge = target.addEdge(sourceVertex, targetVertex);
                target.setEdgeWeight(newEdge, weight);
            } else {
                DefaultWeightedEdge existingEdge = target.getEdge(sourceVertex, targetVertex);
                target.setEdgeWeight(existingEdge, target.getEdgeWeight(existingEdge) + weight);
            }
        }
    }

    private static void mergeClassRelationships(
            CodebaseGraphDTO dto,
            Graph<String, DefaultWeightedEdge> mergedPackageGraph,
            Map<DefaultWeightedEdge, Set<DefaultWeightedEdge>> mergedClassRelationships) {
        for (Map.Entry<DefaultWeightedEdge, Set<DefaultWeightedEdge>> entry :
                dto.getClassRelationshipsInPackageRelationship().entrySet()) {

            String pkgSource = dto.getPackageReferencesGraph().getEdgeSource(entry.getKey());
            String pkgTarget = dto.getPackageReferencesGraph().getEdgeTarget(entry.getKey());
            DefaultWeightedEdge mergedPkgEdge = mergedPackageGraph.getEdge(pkgSource, pkgTarget);
            if (mergedPkgEdge != null) {
                mergedClassRelationships
                        .computeIfAbsent(mergedPkgEdge, k -> new HashSet<>())
                        .addAll(entry.getValue());
            }
        }
    }
}
