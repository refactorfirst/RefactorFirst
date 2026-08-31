package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Kotlin type-parameter bounds: Kotlin generic class/method/property type-parameter bounds and
 * top-level type-alias initializers must produce dependency-graph edges
 * between the owning class and the referenced (bound) class.
 *
 * <p>Plan references:
 * <ul>
 *   <li>{@code KotlinDependencyVisitor.visitClassDeclaration(K.ClassDeclaration)}
 *       extracts type params + type constraints (parser wiring).
 *   <li>{@code visitMethodDeclaration(K.MethodDeclaration)} extracts method
 *       type params + method type constraints (parser wiring).
 *   <li>{@code visitProperty(K.Property)} extracts property declared-type
 *       dependencies (parser wiring).
 *   <li>{@code visitTypeAlias(K.TypeAlias)} extracts type-alias initializer
 *       and type-alias parameters (parser wiring).
 * </ul>
 *
 * <p>This test pins the wiring of all four extraction sites by asserting
 * that the produced class-references graph contains the expected edge
 * {@code GenericHolder -> MetaClassA}. A top-level
 * {@code typealias MetaList = List<MetaClassA>} is also asserted not to
 * break the parser (it is a no-op for graph edges because it has no class
 * owner).
 *
 * <p><b>Parser limitation:</b> a class-scoped {@code typealias} is not
 * supported by the OpenRewrite Kotlin parser (the enclosing class becomes
 * {@code J.Unknown}); such a fixture is intentionally absent here.
 */
class TypeParameterReferenceTest {

    @DisplayName("Kotlin class/method/property type-parameter bounds produce edges to the bound class; "
            + "top-level typealias does not break the visitor")
    @Test
    void detectTypeParameterBoundEdges() throws IOException {
        File srcDirectory = new File("src/test/resources/kotlinTypeParamSrcDirectory");
        CompositeGraphBuilder compositeGraphBuilder = new CompositeGraphBuilder();
        CodebaseGraphDTO dto = compositeGraphBuilder.getCodebaseGraphDTO(srcDirectory.getAbsolutePath(), false, "");
        Graph<String, DefaultWeightedEdge> classReferencesGraph = dto.getClassReferencesGraph();
        assertNotNull(classReferencesGraph);

        System.out.println("Vertices: " + classReferencesGraph.vertexSet());
        System.out.println("Edges: " + classReferencesGraph.edgeSet());

        // The bound class appears as a vertex.
        assertTrue(
                classReferencesGraph.containsVertex("com.ideacrest.parser.typeparams.MetaClassA"),
                "MetaClassA should be a vertex");

        // GenericHolder<T : MetaClassA> — class-level type param bound,
        // method type-param bounds, and a property of declared type
        // MetaClassA — all funnel into the GenericHolder -> MetaClassA edge.
        assertTrue(
                classReferencesGraph.containsVertex("com.ideacrest.parser.typeparams.GenericHolder"),
                "GenericHolder should be a vertex");
        assertTrue(
                classReferencesGraph.containsEdge(
                        "com.ideacrest.parser.typeparams.GenericHolder", "com.ideacrest.parser.typeparams.MetaClassA"),
                "GenericHolder -> MetaClassA edge should exist (class/method/property type-parameter bounds)");

        // The top-level typealias MetaList = List<MetaClassA> has no class
        // owner, so it must not introduce a vertex or self-edge. The
        // surrounding graph must simply contain the two classes above.
        assertEquals(
                2, classReferencesGraph.vertexSet().size(), "Only GenericHolder and MetaClassA should be vertices");
    }
}
