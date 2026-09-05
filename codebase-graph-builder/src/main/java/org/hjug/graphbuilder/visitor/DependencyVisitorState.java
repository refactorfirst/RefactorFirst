package org.hjug.graphbuilder.visitor;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.openrewrite.Cursor;

/**
 * Mutable per-traversal state shared between concrete dependency visitors
 * ({@link AbstractDependencyVisitor} and {@link KotlinDependencyVisitor}) and
 * the static helpers in {@link DependencyVisitorLogic}.
 *
 * <p>One {@code DependencyVisitorState} instance is allocated per traversal
 * (per compilation unit batch); it is reset as the visitor descends into
 * nested classes via save/restore snapshots in {@link DependencyVisitorLogic}.
 */
public class DependencyVisitorState {

    /** Current owner class FQN (set when entering a class, restored on leave). */
    @Getter
    @Setter
    private String currentOwnerFqn;

    /** Map from class FQN to canonicalised source file path. */
    @Getter
    private final Map<String, String> classToSourceFilePathMapping = new HashMap<>();

    /** Repository root path used for canonicalising source URIs. */
    @Getter
    private final String repositoryPath;

    /** Git repository root for URL canonicalization (may differ from repositoryPath in multi-module projects). */
    @Getter
    @Setter
    private String repositoryRoot = "";

    /** Type processor that records dependencies to the collector. */
    @Getter
    private final BaseTypeProcessor typeProcessor;

    /** Source file extension for synthetic path generation (".java" or ".kt"). */
    @Getter
    @Setter
    private String sourceFileExtension;

    /** Owning package name of the current compilation unit (set on enterCompilationUnit). */
    @Getter
    @Setter
    private String owningPackageName;

    /** Cursor for the current node being visited. */
    @Getter
    @Setter
    private Cursor cursor;

    /**
     * Creates a new DependencyVisitorState.
     *
     * @param repositoryPath  path to the source directory
     * @param repositoryRoot  path to the Git repository root
     * @param typeProcessor   the type processor for dependency collection
     */
    public DependencyVisitorState(String repositoryPath, String repositoryRoot, BaseTypeProcessor typeProcessor) {
        this.repositoryPath = repositoryPath;
        this.repositoryRoot = repositoryRoot;
        this.typeProcessor = typeProcessor;
    }
}
