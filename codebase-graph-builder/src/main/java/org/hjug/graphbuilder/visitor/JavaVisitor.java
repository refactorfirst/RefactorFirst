package org.hjug.graphbuilder.visitor;

import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.DependencyCollector;

/**
 * Java-specific dependency visitor. Thin wrapper around
 * {@link AbstractDependencyVisitor} whose sole responsibility is to be
 * {@link org.openrewrite.java.JavaIsoVisitor}-typed for parsing
 * {@link org.openrewrite.java.tree.J.CompilationUnit}s.
 *
 * @param <P> the visitor context type
 */
@Slf4j
public class JavaVisitor<P> extends AbstractDependencyVisitor<P> {

    public JavaVisitor(String repositoryPath, String repositoryRoot, DependencyCollector dependencyCollector) {
        super(repositoryPath, repositoryRoot, dependencyCollector);
    }
}
