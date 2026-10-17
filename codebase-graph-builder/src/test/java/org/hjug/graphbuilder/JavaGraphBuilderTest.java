package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end Java-fixture graph tests, driven through the orchestration entry
 * point {@link CompositeGraphBuilder} (which always runs the Java builder and
 * merges an empty Kotlin DTO for a Java-only source directory). The Java-only
 * fixture under {@code src/test/resources/javaSrcDirectory} produces the same
 * vertices/edges through the composite as the removed {@code JavaGraphBuilder}
 * once did.
 */
class JavaGraphBuilderTest {

    private final CompositeGraphBuilder compositeGraphBuilder = new CompositeGraphBuilder();

    @DisplayName("When source directory input param is empty or null throw IllegalArgumentException.")
    @Test
    void parseSourceDirectoryEmptyTest() {
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> compositeGraphBuilder.getCodebaseGraphDTO("", false, ""));
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> compositeGraphBuilder.getCodebaseGraphDTO(null, false, ""));
    }

    @DisplayName("Vertices are VertexInfo instances hydrated with repo-relative location and parent namespace")
    @Test
    void parseSourceDirectoryVerticesCarryVertexInfo() throws IOException {
        File srcDirectory = new File("src/test/resources/javaSrcDirectory");
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");

        Graph<VertexInfo, DefaultWeightedEdge> classGraph = dto.getClassReferencesGraph();
        VertexInfo classA = classGraph.vertexSet().stream()
                .filter(v -> v.getName().equals("com.ideacrest.parser.testclasses.A"))
                .findFirst()
                .orElseThrow();
        assertEquals("com.ideacrest.parser.testclasses", classA.getParentNamespace());
        assertEquals("com/ideacrest/parser/testclasses/A.java", classA.getLocationOnDisk());

        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = dto.getPackageReferencesGraph();
        // single-package fixture: no cross-package edges, so no package vertices exist
        assertTrue(packageGraph.vertexSet().isEmpty());
        assertTrue(packageGraph.edgeSet().isEmpty());
    }

    @DisplayName("Cross-package builds hydrate package vertices with parent package and member-file directory")
    @Test
    void crossPackageBuildHydratesPackageVertices() throws IOException {
        File srcDirectory = new File("src/test/resources/mixedSrcDirectoryCrossPackage");
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");

        Graph<VertexInfo, DefaultWeightedEdge> packageGraph = dto.getPackageReferencesGraph();
        VertexInfo fxglAppPackage = packageGraph.vertexSet().stream()
                .filter(v -> v.getName().equals("com.almasb.fxgl.app"))
                .findFirst()
                .orElseThrow();
        assertEquals("com.almasb.fxgl", fxglAppPackage.getParentNamespace());
        assertNotNull(fxglAppPackage.getLocationOnDisk(), "package location derived from a member file");
        assertTrue(
                fxglAppPackage.getLocationOnDisk().endsWith("com/almasb/fxgl/app"),
                "expected the package directory, got: " + fxglAppPackage.getLocationOnDisk());
    }

    @DisplayName("Given a valid source directory input parameter return a valid graph.")
    @Test
    void parseSourceDirectoryTest() throws IOException {
        File srcDirectory = new File("src/test/resources/javaSrcDirectory");
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");
        Graph<VertexInfo, DefaultWeightedEdge> classReferencesGraph = dto.getClassReferencesGraph();
        assertNotNull(classReferencesGraph);
        assertEquals(5, classReferencesGraph.vertexSet().size());
        assertEquals(7, classReferencesGraph.edgeSet().size());
        assertTrue(classReferencesGraph.containsVertex(VertexInfo.forName("com.ideacrest.parser.testclasses.A")));
        assertTrue(classReferencesGraph.containsVertex(VertexInfo.forName("com.ideacrest.parser.testclasses.B")));
        assertTrue(classReferencesGraph.containsVertex(VertexInfo.forName("com.ideacrest.parser.testclasses.C")));
        assertTrue(classReferencesGraph.containsVertex(VertexInfo.forName("com.ideacrest.parser.testclasses.D")));
        assertTrue(classReferencesGraph.containsVertex(VertexInfo.forName("com.ideacrest.parser.testclasses.E")));
        assertTrue(classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.A"),
                VertexInfo.forName("com.ideacrest.parser.testclasses.B")));
        assertTrue(classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.B"),
                VertexInfo.forName("com.ideacrest.parser.testclasses.C")));
        assertTrue(classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.C"),
                VertexInfo.forName("com.ideacrest.parser.testclasses.A")));
        assertTrue(classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.C"),
                VertexInfo.forName("com.ideacrest.parser.testclasses.E")));
        assertTrue(classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.D"),
                VertexInfo.forName("com.ideacrest.parser.testclasses.A")));
        assertTrue(classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.D"),
                VertexInfo.forName("com.ideacrest.parser.testclasses.C")));
        assertTrue(classReferencesGraph.containsEdge(
                VertexInfo.forName("com.ideacrest.parser.testclasses.E"),
                VertexInfo.forName("com.ideacrest.parser.testclasses.D")));

        // confirm edge weight calculations
        assertEquals(
                1,
                getEdgeWeight(
                        classReferencesGraph,
                        VertexInfo.forName("com.ideacrest.parser.testclasses.A"),
                        VertexInfo.forName("com.ideacrest.parser.testclasses.B")));
        assertEquals(
                2,
                getEdgeWeight(
                        classReferencesGraph,
                        VertexInfo.forName("com.ideacrest.parser.testclasses.E"),
                        VertexInfo.forName("com.ideacrest.parser.testclasses.D")));
    }

    private static double getEdgeWeight(
            Graph<VertexInfo, DefaultWeightedEdge> classReferencesGraph,
            VertexInfo sourceVertex,
            VertexInfo targetVertex) {
        return classReferencesGraph.getEdgeWeight(classReferencesGraph.getEdge(sourceVertex, targetVertex));
    }
}
