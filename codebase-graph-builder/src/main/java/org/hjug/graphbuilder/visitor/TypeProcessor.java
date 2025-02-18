package org.hjug.graphbuilder.visitor;

import lombok.extern.slf4j.Slf4j;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;

public interface TypeProcessor {

    @Slf4j
    final class LogHolder {}

    /**
     * @param ownerFqn The FQN that is the source of the relationship
     * @param javaType The type that is used/referenced by the source of the relationship
     */
    default void processType(String ownerFqn, JavaType javaType) {
        if (javaType instanceof JavaType.Class) {
            processType(ownerFqn, (JavaType.Class) javaType);
        } else if (javaType instanceof JavaType.Parameterized) {
            // A<E> --> A
            processType(ownerFqn, (JavaType.Parameterized) javaType);
        } else if (javaType instanceof JavaType.GenericTypeVariable) {
            // T t;
            processType(ownerFqn, (JavaType.GenericTypeVariable) javaType);
        } else if (javaType instanceof JavaType.Array) {
            processType(ownerFqn, (JavaType.Array) javaType);
        }
    }

    private void processType(String ownerFqn, JavaType.Parameterized parameterized) {
        // List<A<E>> --> A
        processAnnotations(ownerFqn, parameterized);
        LogHolder.log.debug("Parameterized type FQN : " + parameterized.getFullyQualifiedName());
        addType(ownerFqn, parameterized.getFullyQualifiedName());

        LogHolder.log.debug("Nested Parameterized type parameters: " + parameterized.getTypeParameters());
        for (JavaType parameter : parameterized.getTypeParameters()) {
            processType(ownerFqn, parameter);
        }
    }

    private void processType(String ownerFqn, JavaType.Array arrayType) {
        // D[] --> D
        LogHolder.log.debug("Array Element type: " + arrayType.getElemType());
        processType(ownerFqn, arrayType.getElemType());
    }

    private void processType(String ownerFqn, JavaType.GenericTypeVariable typeVariable) {
        LogHolder.log.debug("Type parameter type name: " + typeVariable.getName());

        for (JavaType bound : typeVariable.getBounds()) {
            if (bound instanceof JavaType.Class) {
                addType(((JavaType.Class) bound).getFullyQualifiedName(), ownerFqn);
            } else if (bound instanceof JavaType.Parameterized) {
                addType(((JavaType.Parameterized) bound).getFullyQualifiedName(), ownerFqn);
            }
        }
    }

    private void processType(String ownerFqn, JavaType.Class classType) {
        processAnnotations(ownerFqn, classType);
        LogHolder.log.debug("Class type FQN: " + classType.getFullyQualifiedName());
        addType(ownerFqn, classType.getFullyQualifiedName());
    }

    private void processAnnotations(String ownerFqn, JavaType.FullyQualified fullyQualified) {
        if (!fullyQualified.getAnnotations().isEmpty()) {
            for (JavaType.FullyQualified annotation : fullyQualified.getAnnotations()) {
                String annotationFqn = annotation.getFullyQualifiedName();
                LogHolder.log.debug("Extra Annotation FQN: " + annotationFqn);
                addType(ownerFqn, annotationFqn);
            }
        }
    }

    default void processAnnotation(String ownerFqn, J.Annotation annotation) {
        if (annotation.getType() instanceof JavaType.Unknown) {
            return;
        }

        JavaType.Class type = (JavaType.Class) annotation.getType();
        if (null != type) {
            String annotationFqn = type.getFullyQualifiedName();
            LogHolder.log.debug("Variable Annotation FQN: " + annotationFqn);
            addType(ownerFqn, annotationFqn);

            if (null != annotation.getArguments()) {
                for (Expression argument : annotation.getArguments()) {
                    processType(ownerFqn, argument.getType());
                }
            }
        }
    }

    default void processTypeParameter(String ownerFqn, J.TypeParameter typeParameter) {

        if (null != typeParameter.getBounds()) {
            for (TypeTree bound : typeParameter.getBounds()) {
                processType(ownerFqn, bound.getType());
            }
        }

        if (!typeParameter.getAnnotations().isEmpty()) {
            for (J.Annotation annotation : typeParameter.getAnnotations()) {
                processAnnotation(ownerFqn, annotation);
            }
        }
    }

    default Graph<String, DefaultWeightedEdge> getPackageReferencesGraph() {
        return null;
    }

    Graph<String, DefaultWeightedEdge> getClassReferencesGraph();

    /**
     *
     * @param ownerFqn The FQN that is the source of the relationship
     * @param typeFqn The FQN of the type that is being used by the source
     */
    default void addType(String ownerFqn, String typeFqn) {
        if (ownerFqn.equals(typeFqn)) return;

        Graph<String, DefaultWeightedEdge> classReferencesGraph = getClassReferencesGraph();

        classReferencesGraph.addVertex(ownerFqn);
        classReferencesGraph.addVertex(typeFqn);

        if (!classReferencesGraph.containsEdge(ownerFqn, typeFqn)) {
            classReferencesGraph.addEdge(ownerFqn, typeFqn);
        } else {
            DefaultWeightedEdge edge = classReferencesGraph.getEdge(ownerFqn, typeFqn);
            classReferencesGraph.setEdgeWeight(edge, classReferencesGraph.getEdgeWeight(edge) + 1);
        }
    }

    // TODO: process packages
}
