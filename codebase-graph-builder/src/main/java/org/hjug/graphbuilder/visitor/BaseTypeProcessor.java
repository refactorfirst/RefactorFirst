package org.hjug.graphbuilder.visitor;

import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.DependencyCollector;
import org.openrewrite.Cursor;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;

/**
 * Base type processor that provides common type-processing logic for
 * dependency extraction. Subclasses implement {@link #getDependencyCollector()}
 * to provide the dependency collector instance.
 */
@Slf4j
public abstract class BaseTypeProcessor {

    private final TypeDependencyExtractor typeDependencyExtractor = new TypeDependencyExtractor();

    /**
     * Returns the dependency collector to use for recording class dependencies.
     *
     * @return the dependency collector
     */
    protected abstract DependencyCollector getDependencyCollector();

    /**
     * Processes a Java type and extracts class dependencies.
     *
     * @param ownerFqn the fully qualified name of the type owner
     * @param javaType the Java type to process
     */
    protected void processType(String ownerFqn, JavaType javaType) {
        if (javaType == null || javaType instanceof JavaType.Unknown) {
            return;
        }

        for (String dependency : typeDependencyExtractor.extractDependencies(javaType)) {
            getDependencyCollector().addClassDependency(ownerFqn, dependency);
        }
    }

    /**
     * Processes an annotation and records its class and argument type dependencies.
     *
     * @param ownerFqn   the fully qualified name of the owning type
     * @param annotation the annotation to process
     * @param cursor     the cursor providing processing context
     */
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

    /**
     * Processes a type parameter and extracts class dependencies from its bounds
     * and annotations.
     *
     * @param ownerFqn      the fully qualified name of the type owner
     * @param typeParameter the type parameter to process
     * @param cursor        the cursor for context
     */
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

    /**
     * Processes all annotations at the given cursor position.
     *
     * @param ownerFqn the fully qualified name of the type owner
     * @param cursor   the cursor for context
     */
    protected void processAnnotations(String ownerFqn, Cursor cursor) {
        AnnotationService annotationService = new AnnotationService();
        for (J.Annotation annotation : annotationService.getAllAnnotations(cursor)) {
            processAnnotation(ownerFqn, annotation, cursor);
        }
    }
}
