package org.hjug.parser.visitor;

import lombok.Getter;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

// TODO: See RemoveMethodInvocationsVisitor for other visitor methods to override
// Custom visitor - not extending IsoVisitor on purpose since it does not provide caller information
public class JavaMethodInvocationVisitor implements TypeProcessor {

    @Getter
    private Graph<String, DefaultWeightedEdge> classReferencesGraph;

    public JavaMethodInvocationVisitor(Graph<String, DefaultWeightedEdge> classReferencesGraph) {
        this.classReferencesGraph = classReferencesGraph;
    }

    public J.MethodInvocation visitMethodInvocation(String invokingFqn, J.MethodInvocation methodInvocation) {
        processType(invokingFqn, methodInvocation.getMethodType().getDeclaringType());

        if(null != methodInvocation.getTypeParameters() && !methodInvocation.getTypeParameters().isEmpty()) {
            for (Expression typeParameter : methodInvocation.getTypeParameters()) {
                processType(invokingFqn, typeParameter.getType());
            }
        }

        for (Expression argument : methodInvocation.getArguments()) {
            processType(invokingFqn, argument.getType());
        }

        return methodInvocation;
    }

    public void addType(String invokingFqn, String typeFqn) {
        if (invokingFqn.equals(typeFqn)) return;

        classReferencesGraph.addVertex(invokingFqn);
        classReferencesGraph.addVertex(typeFqn);

        if (!classReferencesGraph.containsEdge(invokingFqn, typeFqn)) {
            classReferencesGraph.addEdge(invokingFqn, typeFqn);
        } else {
            DefaultWeightedEdge edge = classReferencesGraph.getEdge(invokingFqn, typeFqn);
            classReferencesGraph.setEdgeWeight(edge, classReferencesGraph.getEdgeWeight(edge) + 1);
        }
    }
}
