package org.hjug.graphbuilder.visitor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.openrewrite.java.tree.J;

@RequiredArgsConstructor
@Getter
public class JavaNewClassVisitor implements TypeProcessor {

    private final Graph<String, DefaultWeightedEdge> classReferencesGraph;

    public J.NewClass visitNewClass(String invokingFqn, J.NewClass newClass) {
        processType(invokingFqn, newClass.getType());
        // TASK: process initializer block???
        return newClass;
    }
}
