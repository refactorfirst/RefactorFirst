package org.hjug.graphbuilder.visitor;

import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.DependencyCollector;
import org.openrewrite.Cursor;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;

@Slf4j
public abstract class BaseTypeProcessor {

    private final TypeDependencyExtractor typeDependencyExtractor = new TypeDependencyExtractor();

    protected abstract DependencyCollector getDependencyCollector();

    protected void processType(String ownerFqn, JavaType javaType) {
        if (javaType == null || javaType instanceof JavaType.Unknown) {
            return;
        }

        for (String dependency : typeDependencyExtractor.extractDependencies(javaType)) {
            getDependencyCollector().addClassDependency(ownerFqn, dependency);
        }
    }

    protected void processAnnotation(String ownerFqn, J.Annotation annotation, Cursor cursor) {
        if (annotation.getType() instanceof JavaType.Unknown) {
            return;
        }

        JavaType.Class type = (JavaType.Class) annotation.getType();
        if (null != type) {
            String annotationFqn = type.getFullyQualifiedName();
            log.debug("Variable Annotation FQN: {}", annotationFqn);
            getDependencyCollector().addClassDependency(ownerFqn, annotationFqn);

            if (null != annotation.getArguments()) {
                for (Expression argument : annotation.getArguments()) {
                    processType(ownerFqn, argument.getType());
                }
            }
        }
    }

    protected void processTypeParameter(String ownerFqn, J.TypeParameter typeParameter, Cursor cursor) {
        if (null != typeParameter.getBounds()) {
            for (TypeTree bound : typeParameter.getBounds()) {
                processType(ownerFqn, bound.getType());
            }
        }

        if (!typeParameter.getAnnotations().isEmpty()) {
            for (J.Annotation annotation : typeParameter.getAnnotations()) {
                processAnnotation(ownerFqn, annotation, cursor);
            }
        }
    }

    protected void processAnnotations(String ownerFqn, Cursor cursor) {
        AnnotationService annotationService = new AnnotationService();
        for (J.Annotation annotation : annotationService.getAllAnnotations(cursor)) {
            processAnnotation(ownerFqn, annotation, cursor);
        }
    }

    protected String getPackageFromFqn(String fqn) {
        if (!fqn.contains(".")) {
            return "";
        }
        int lastIndex = fqn.lastIndexOf(".");
        return fqn.substring(0, lastIndex);
    }
}
