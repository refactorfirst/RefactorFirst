package org.hjug.graphbuilder.visitor;

import org.openrewrite.Cursor;

/**
 * Strategy for resolving the source path URI of a compilation unit from a cursor.
 *
 * <p>Java and Kotlin visitors differ in how they locate the enclosing compilation
 * unit: Java uses {@code J.CompilationUnit}, while Kotlin may need to fall back
 * to {@code K.CompilationUnit} when the J-level CU is not present in the cursor
 * stack (e.g., for inner classes visited from a K-level container).
 *
 * <p>This functional interface isolates that divergence so {@link DependencyVisitorLogic}
 * can remain language-agnostic.
 */
@FunctionalInterface
public interface SourcePathResolver {

    /**
     * Resolves the source path URI string from the given cursor.
     *
     * @param cursor the current visitor cursor
     * @return the source path URI as a string, or {@code null} if not resolvable
     */
    String resolveSourcePath(Cursor cursor);
}
