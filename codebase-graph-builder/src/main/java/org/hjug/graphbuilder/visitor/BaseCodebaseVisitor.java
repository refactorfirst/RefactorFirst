package org.hjug.graphbuilder.visitor;

import lombok.Getter;
import org.hjug.graphbuilder.DependencyCollector;
import org.openrewrite.java.JavaIsoVisitor;

@Getter
public abstract class BaseCodebaseVisitor<P> extends JavaIsoVisitor<P> {

    protected final DependencyCollector dependencyCollector;

    protected BaseCodebaseVisitor(DependencyCollector dependencyCollector) {
        this.dependencyCollector = dependencyCollector;
    }

    protected abstract String getCurrentOwnerFqn();
}
