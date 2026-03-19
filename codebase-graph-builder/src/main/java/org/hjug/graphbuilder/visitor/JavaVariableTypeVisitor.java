package org.hjug.graphbuilder.visitor;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.DependencyCollector;
import org.openrewrite.java.tree.*;

@Slf4j
public class JavaVariableTypeVisitor<P> extends BaseCodebaseVisitor<P> {

    private final BaseTypeProcessor typeProcessor;

    public JavaVariableTypeVisitor(DependencyCollector dependencyCollector) {
        super(dependencyCollector);
        this.typeProcessor = new BaseTypeProcessor() {
            @Override
            protected DependencyCollector getDependencyCollector() {
                return dependencyCollector;
            }
        };
    }

    @Override
    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, P p) {
        J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, p);

        List<J.VariableDeclarations.NamedVariable> variables = variableDeclarations.getVariables();
        if (null == variables || variables.isEmpty() || null == variables.get(0).getVariableType()) {
            log.debug("Skipping variable declaration with null variable type");
            return variableDeclarations;
        }

        JavaType owner = variables.get(0).getVariableType().getOwner();
        String ownerFqn = "";

        if (owner instanceof JavaType.Method) {
            JavaType.Method m = (JavaType.Method) owner;
            if (m.getDeclaringType() == null) {
                log.warn("Method owner has null declaring type, skipping variable declaration");
                return variableDeclarations;
            }
            ownerFqn = m.getDeclaringType().getFullyQualifiedName();
        } else if (owner instanceof JavaType.Class) {
            JavaType.Class c = (JavaType.Class) owner;
            ownerFqn = c.getFullyQualifiedName();
        } else {
            log.debug("Unknown owner type: {}", owner != null ? owner.getClass() : "null");
            return variableDeclarations;
        }

        log.debug("Processing variable declaration in: {}", ownerFqn);

        TypeTree typeTree = variableDeclarations.getTypeExpression();

        JavaType javaType;
        if (null != typeTree) {
            javaType = typeTree.getType();
        } else {
            return variableDeclarations;
        }

        typeProcessor.processAnnotations(ownerFqn, getCursor());

        if (javaType instanceof JavaType.Primitive) {
            return variableDeclarations;
        }

        typeProcessor.processType(ownerFqn, javaType);

        return variableDeclarations;
    }

    @Override
    protected String getCurrentOwnerFqn() {
        return null;
    }
}
