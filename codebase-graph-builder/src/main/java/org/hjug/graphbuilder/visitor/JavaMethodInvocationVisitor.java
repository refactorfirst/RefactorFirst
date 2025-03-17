package org.hjug.graphbuilder.visitor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

// See RemoveMethodInvocationsVisitor for other visitor methods to override
// Custom visitor - not extending IsoVisitor on purpose since it does not provide caller information

@RequiredArgsConstructor
@Getter
public class JavaMethodInvocationVisitor implements TypeProcessor {

    private final Graph<String, DefaultWeightedEdge> classReferencesGraph;

    public J.MethodInvocation visitMethodInvocation(String invokingFqn, J.MethodInvocation methodInvocation) {
        // getDeclaringType() returns the type that declared the method being invoked
        JavaType.Method methodType = methodInvocation.getMethodType();
        // sometimes methodType is null - not sure why
        if (null != methodType && null != methodType.getDeclaringType()) {
            processType(invokingFqn, methodType.getDeclaringType());
        }

        if (null != methodInvocation.getTypeParameters()
                && !methodInvocation.getTypeParameters().isEmpty()) {
            for (Expression typeParameter : methodInvocation.getTypeParameters()) {
                processType(invokingFqn, typeParameter.getType());
            }
        }

        return methodInvocation;
    }
}
