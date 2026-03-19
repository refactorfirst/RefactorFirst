package org.hjug.graphbuilder.visitor;

import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.openrewrite.java.tree.JavaType;

@Slf4j
public class TypeDependencyExtractor {

    /**
     * Extracts all type dependencies from a JavaType
     *
     * @param javaType The type to extract dependencies from
     * @return Set of fully qualified type names that the given type depends on
     */
    public Set<String> extractDependencies(JavaType javaType) {
        Set<String> dependencies = new HashSet<>();
        if (javaType == null) {
            return dependencies;
        }

        extractDependenciesRecursive(javaType, dependencies);
        return dependencies;
    }

    private void extractDependenciesRecursive(JavaType javaType, Set<String> dependencies) {
        if (javaType instanceof JavaType.Class) {
            extractFromClass((JavaType.Class) javaType, dependencies);
        } else if (javaType instanceof JavaType.Parameterized) {
            extractFromParameterized((JavaType.Parameterized) javaType, dependencies);
        } else if (javaType instanceof JavaType.GenericTypeVariable) {
            extractFromGenericTypeVariable((JavaType.GenericTypeVariable) javaType, dependencies);
        } else if (javaType instanceof JavaType.Array) {
            extractFromArray((JavaType.Array) javaType, dependencies);
        }
    }

    private void extractFromClass(JavaType.Class classType, Set<String> dependencies) {
        log.debug("Class type FQN: {}", classType.getFullyQualifiedName());
        dependencies.add(classType.getFullyQualifiedName());
        extractAnnotations(classType, dependencies);
    }

    private void extractFromParameterized(JavaType.Parameterized parameterized, Set<String> dependencies) {
        log.debug("Parameterized type FQN: {}", parameterized.getFullyQualifiedName());
        dependencies.add(parameterized.getFullyQualifiedName());
        extractAnnotations(parameterized, dependencies);

        log.debug("Nested Parameterized type parameters: {}", parameterized.getTypeParameters());
        for (JavaType parameter : parameterized.getTypeParameters()) {
            extractDependenciesRecursive(parameter, dependencies);
        }
    }

    private void extractFromArray(JavaType.Array arrayType, Set<String> dependencies) {
        log.debug("Array Element type: {}", arrayType.getElemType());
        extractDependenciesRecursive(arrayType.getElemType(), dependencies);
    }

    private void extractFromGenericTypeVariable(JavaType.GenericTypeVariable typeVariable, Set<String> dependencies) {
        log.debug("Type parameter type name: {}", typeVariable.getName());

        for (JavaType bound : typeVariable.getBounds()) {
            if (bound instanceof JavaType.Class) {
                dependencies.add(((JavaType.Class) bound).getFullyQualifiedName());
            } else if (bound instanceof JavaType.Parameterized) {
                dependencies.add(((JavaType.Parameterized) bound).getFullyQualifiedName());
            } else {
                log.debug("Unknown type bound: {}", bound);
            }
        }
    }

    private void extractAnnotations(JavaType.FullyQualified fullyQualified, Set<String> dependencies) {
        if (!fullyQualified.getAnnotations().isEmpty()) {
            for (JavaType.FullyQualified annotation : fullyQualified.getAnnotations()) {
                String annotationFqn = annotation.getFullyQualifiedName();
                log.debug("Annotation FQN: {}", annotationFqn);
                dependencies.add(annotationFqn);
            }
        }
    }
}
