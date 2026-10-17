package org.hjug.graphbuilder;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VertexInfo}.
 *
 * <p>VertexInfo is the graph vertex type: it carries the vertex name plus the
 * information needed to build a composed codebase map — the location on disk
 * relative to the repository root and the parent namespace (parent package for
 * package vertices, enclosing package/class for class vertices).
 *
 * <p>Identity is name-based so JGraphT vertex deduplication behaves exactly like
 * the previous String vertices, and {@code toString()} returns the name so
 * edge rendering ({@code "(source : target)"}) is unchanged.
 */
class VertexInfoTest {

    /** Verifies that a top-level class derives its package namespace and starts with no disk location. */
    @DisplayName("forName captures the vertex name and derives the enclosing package for a top-level class")
    @Test
    void forName_topLevelClass_parentNamespaceIsEnclosingPackage() {
        VertexInfo vertex = VertexInfo.forName("com.foo.Bar");

        assertEquals("com.foo.Bar", vertex.getName());
        assertEquals("com.foo", vertex.getParentNamespace());
        assertNull(vertex.getLocationOnDisk(), "location is hydrated later, not at creation");
    }

    /** Verifies that a nested class identifies its enclosing class as its parent namespace. */
    @DisplayName("forName derives the enclosing class for a nested class")
    @Test
    void forName_nestedClass_parentNamespaceIsEnclosingClass() {
        VertexInfo vertex = VertexInfo.forName("com.foo.Outer$Inner");

        assertEquals("com.foo.Outer", vertex.getParentNamespace());
    }

    /** Verifies parent derivation for anonymous, synthetic, and multiply nested Java classes. */
    @DisplayName("forName derives the enclosing class for Java anonymous and synthetic classes")
    @Test
    void forName_anonymousAndSyntheticClasses_parentNamespaceIsEnclosingClass() {
        assertEquals("com.foo.Outer", VertexInfo.forName("com.foo.Outer$1").getParentNamespace());
        assertEquals("com.foo.Outer", VertexInfo.forName("com.foo.Outer$").getParentNamespace());
        assertEquals(
                "com.foo.Outer$Inner",
                VertexInfo.forName("com.foo.Outer$Inner$2").getParentNamespace());
    }

    /** Verifies that a Kotlin anonymous vertex identifies its enclosing construct. */
    @DisplayName("forName derives the enclosing class for a Kotlin <anonymous> vertex")
    @Test
    void forName_kotlinAnonymous_parentNamespaceIsEnclosingConstruct() {
        VertexInfo vertex = VertexInfo.forName("dev.DeveloperWASDControl.<anonymous>");

        assertEquals("dev.DeveloperWASDControl", vertex.getParentNamespace());
    }

    /** Verifies that unqualified classes and standalone anonymous vertices have no parent namespace. */
    @DisplayName("forName yields no parent namespace for packageless and top-level anonymous vertices")
    @Test
    void forName_packagelessVertex_parentNamespaceIsNull() {
        assertNull(VertexInfo.forName("Bar").getParentNamespace());
        assertNull(VertexInfo.forName("<anonymous>").getParentNamespace());
    }

    /** Verifies that package vertices derive parent packages and top-level packages have no parent. */
    @DisplayName("forName derives the parent package for a package vertex")
    @Test
    void forName_packageVertex_parentNamespaceIsParentPackage() {
        VertexInfo vertex = VertexInfo.forName("com.foo");

        assertEquals("com", vertex.getParentNamespace());
        assertNull(VertexInfo.forName("com").getParentNamespace(), "top-level package has no parent");
    }

    /** Verifies that vertex identity depends only on the name and is unaffected by location hydration. */
    @DisplayName("VertexInfo equality and hashing are name-based only")
    @Test
    void equalsAndHashCode_considerOnlyTheName() {
        VertexInfo bare = VertexInfo.forName("com.foo.Bar");
        VertexInfo hydrated = VertexInfo.forName("com.foo.Bar");
        hydrated.setLocationOnDisk("src/main/java/com/foo/Bar.java");

        assertEquals(bare, hydrated);
        assertEquals(bare.hashCode(), hydrated.hashCode());
        assertNotEquals(bare, VertexInfo.forName("com.foo.Baz"));
    }

    /** Verifies that a fresh vertex probe finds a graph vertex with the same name. */
    @DisplayName("A VertexInfo probe created by forName is found in a graph by name")
    @Test
    void probe_matchesGraphVertexWithSameName() {
        org.jgrapht.Graph<VertexInfo, org.jgrapht.graph.DefaultWeightedEdge> graph =
                new org.jgrapht.graph.DefaultDirectedWeightedGraph<>(org.jgrapht.graph.DefaultWeightedEdge.class);
        VertexInfo collected = VertexInfo.forName("com.foo.Bar");
        graph.addVertex(collected);

        assertTrue(graph.containsVertex(VertexInfo.forName("com.foo.Bar")));
        assertTrue(graph.vertexSet().contains(collected));
    }

    /** Verifies that string conversion preserves named and anonymous vertex names for edge rendering. */
    @DisplayName("toString returns the vertex name so edge rendering is unchanged")
    @Test
    void toString_returnsName() {
        assertEquals("com.foo.Bar", VertexInfo.forName("com.foo.Bar").toString());
        assertEquals("<anonymous>", VertexInfo.forName("<anonymous>").toString());
    }

    /** Verifies that hydration can assign a repository-relative disk location. */
    @DisplayName("locationOnDisk can be set during hydration and read back")
    @Test
    void locationOnDisk_canBeHydrated() {
        VertexInfo vertex = VertexInfo.forName("com.foo.Bar");

        vertex.setLocationOnDisk("src/main/java/com/foo/Bar.java");

        assertEquals("src/main/java/com/foo/Bar.java", vertex.getLocationOnDisk());
    }

    /** Verifies that vertex creation rejects null and empty names with the expected exceptions. */
    @DisplayName("forName rejects null and empty names")
    @Test
    void forName_rejectsNullAndEmptyNames() {
        assertThrows(NullPointerException.class, () -> VertexInfo.forName(null));
        assertThrows(IllegalArgumentException.class, () -> VertexInfo.forName(""));
    }
}
