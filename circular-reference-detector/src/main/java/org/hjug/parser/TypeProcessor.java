package org.hjug.parser;

import lombok.extern.slf4j.Slf4j;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;


public interface TypeProcessor {

    @Slf4j
    final class LogHolder{}

    default void processType(JavaType javaType, String ownerFqn) {
        if (javaType instanceof JavaType.Class) {
            processType((JavaType.Class) javaType, ownerFqn);
        } else if (javaType instanceof JavaType.Parameterized) {
            // A<E> --> A
            processType((JavaType.Parameterized) javaType, ownerFqn);
        } else if (javaType instanceof JavaType.GenericTypeVariable) {
            // T t;
            processType((JavaType.GenericTypeVariable) javaType, ownerFqn);
        } else if (javaType instanceof JavaType.Array) {
            processType((JavaType.Array) javaType, ownerFqn);
        }
    }

    private void processType(JavaType.Parameterized parameterized, String ownerFqn) {
        // List<A<E>> --> A
        processAnnotations(parameterized, ownerFqn);
        LogHolder.log.debug("Parameterized type FQN : " + parameterized.getFullyQualifiedName());
        addType(ownerFqn, parameterized.getFullyQualifiedName());

        LogHolder.log.debug("Nested Parameterized type parameters: " + parameterized.getTypeParameters());
        for (JavaType parameter : parameterized.getTypeParameters()) {
            processType(parameter, ownerFqn);
        }
    }

    private void processType(JavaType.Array arrayType, String ownerFqn) {
        // D[] --> D
        LogHolder.log.debug("Array Element type: " + arrayType.getElemType());
        processType(arrayType.getElemType(), ownerFqn);
    }

    private void processType(JavaType.GenericTypeVariable typeVariable, String ownerFqn) {
        LogHolder.log.debug("Type parameter type name: " + typeVariable.getName());

        for (JavaType bound : typeVariable.getBounds()) {
            if (bound instanceof JavaType.Class) {
                addType(((JavaType.Class) bound).getFullyQualifiedName(), ownerFqn);
            } else if (bound instanceof JavaType.Parameterized) {
                addType(((JavaType.Parameterized) bound).getFullyQualifiedName(), ownerFqn);
            }
        }
    }

    private void processType(JavaType.Class classType, String ownerFqn) {
        processAnnotations(classType, ownerFqn);
        LogHolder.log.debug("Class type FQN: " + classType.getFullyQualifiedName());
        addType(ownerFqn, classType.getFullyQualifiedName());
    }

    private void processAnnotations(JavaType.FullyQualified fullyQualified, String ownerFqn) {
        if (!fullyQualified.getAnnotations().isEmpty()) {
            for (JavaType.FullyQualified annotation : fullyQualified.getAnnotations()) {
                String annotationFqn = annotation.getFullyQualifiedName();
                LogHolder.log.debug("Extra Annotation FQN: " + annotationFqn);
                addType(ownerFqn, annotationFqn);
            }
        }
    }

    default void processAnnotation(J.Annotation annotation, String ownerFqn) {
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
                    processType(argument.getType(), ownerFqn);
                }
            }
        }
    }

    void addType(String ownerFqn, String typeFqn);

}
