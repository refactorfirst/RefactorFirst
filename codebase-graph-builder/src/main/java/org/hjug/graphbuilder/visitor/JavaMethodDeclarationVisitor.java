package org.hjug.graphbuilder.visitor;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.DependencyCollector;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.TypeTree;

@Slf4j
public class JavaMethodDeclarationVisitor<P> extends BaseCodebaseVisitor<P> {

    private final BaseTypeProcessor typeProcessor;

    public JavaMethodDeclarationVisitor(DependencyCollector dependencyCollector) {
        super(dependencyCollector);
        this.typeProcessor = new BaseTypeProcessor() {
            @Override
            protected DependencyCollector getDependencyCollector() {
                return dependencyCollector;
            }
        };
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, P p) {
        J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(method, p);

        JavaType.Method methodType = methodDeclaration.getMethodType();
        if (null == methodType) {
            log.warn("MethodDeclaration has null methodType, skipping: {}", methodDeclaration.getSimpleName());
            return methodDeclaration;
        }

        if (methodType.getDeclaringType() == null) {
            log.warn("MethodDeclaration has null declaring type, skipping: {}", methodDeclaration.getSimpleName());
            return methodDeclaration;
        }

        String owner = methodType.getDeclaringType().getFullyQualifiedName();

        TypeTree returnTypeExpression = methodDeclaration.getReturnTypeExpression();
        if (returnTypeExpression != null) {
            JavaType returnType = returnTypeExpression.getType();

            if (!(returnType instanceof JavaType.Primitive)) {
                typeProcessor.processType(owner, returnType);
            }
        }

        for (J.Annotation leadingAnnotation : methodDeclaration.getLeadingAnnotations()) {
            typeProcessor.processAnnotation(owner, leadingAnnotation, getCursor());
        }

        if (null != methodDeclaration.getTypeParameters()) {
            for (J.TypeParameter typeParameter : methodDeclaration.getTypeParameters()) {
                typeProcessor.processTypeParameter(owner, typeParameter, getCursor());
            }
        }

        List<NameTree> throwz = methodDeclaration.getThrows();
        if (null != throwz && !throwz.isEmpty()) {
            for (NameTree thrown : throwz) {
                typeProcessor.processType(owner, thrown.getType());
            }
        }

        return methodDeclaration;
    }

    @Override
    protected String getCurrentOwnerFqn() {
        return null;
    }
}
