package org.hjug.parser;

import lombok.extern.slf4j.Slf4j;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;

@Slf4j
public class JavaVariableVisitor<P> extends JavaIsoVisitor<P> {

    Graph<String, DefaultWeightedEdge> classReferencesGraph =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

    /**
     * Variable type is B
     * Owning type is A
     * <p>
     * class A {
     * B b1, b2;
     * }
     */
    @Override
    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, P p) {
        J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, p);

        System.out.println("*************************");
        System.out.println(variableDeclarations);
        System.out.println("*************************");

        JavaType owner =
                variableDeclarations.getVariables().get(0).getVariableType().getOwner();
        String ownerFqn = "";

        if (owner instanceof JavaType.Method) {
            JavaType.Method m = (JavaType.Method) owner;
            //            System.out.println("Method owner: " + m.getDeclaringType().getFullyQualifiedName());
            ownerFqn = m.getDeclaringType().getFullyQualifiedName();
        } else if (owner instanceof JavaType.Class) {
            JavaType.Class c = (JavaType.Class) owner;
            //            System.out.println("Method owner: " + c.getFullyQualifiedName());
            ownerFqn = c.getFullyQualifiedName();
        }

        TypeTree typeTree = variableDeclarations.getTypeExpression();

        // skip class definition to prevent all types from self-referencing
        if (typeTree instanceof J.Identifier) {
            return variableDeclarations;
        }

        JavaType javaType;
        if (null != typeTree) {
            javaType = typeTree.getType();
        } else {
            return variableDeclarations;
        }

        // TODO: getAllAnnotations() is deprecated - need to call
        // AnnotationService.getAllAnnotations() but not sure which one yet
        // but I'm not sure how to get a cursor
        // All types, including primitives can be annotated
        for (J.Annotation annotation : variableDeclarations.getAllAnnotations()) {
            JavaType.Class type = (JavaType.Class) annotation.getType();
            if (null != type) {
                String annotationFqn = type.getFullyQualifiedName();
                System.out.println("Variable Annotation FQN: " + annotationFqn);
                addType(ownerFqn, annotationFqn);
            }
        }

        // skip primitive variable declarations
        if (javaType instanceof JavaType.Primitive) {
            return variableDeclarations;
        }

        processType(javaType, ownerFqn);

        return variableDeclarations;
    }

    // processType methods are mutually recursive
    void processType(JavaType javaType, String ownerFqn) {
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
        System.out.println("Parameterized type FQN : " + parameterized.getFullyQualifiedName());
        addType(ownerFqn, parameterized.getFullyQualifiedName());

        System.out.println("Nested Parameterized type parameters: " + parameterized.getTypeParameters());
        for (JavaType parameter : parameterized.getTypeParameters()) {
            processType(parameter, ownerFqn);
        }
    }

    private void processType(JavaType.Array arrayType, String ownerFqn) {
        // D[] --> D
        System.out.println("Array Element type: " + arrayType.getElemType());
        processType(arrayType.getElemType(), ownerFqn);
    }

    private void processType(JavaType.GenericTypeVariable typeVariable, String ownerFqn) {
        System.out.println("Type parameter type name: " + typeVariable.getName());

        for (JavaType bound : typeVariable.getBounds()) {
            processType(bound, ownerFqn);
        }
    }

    private void processType(JavaType.Class classType, String ownerFqn) {
        processAnnotations(classType, ownerFqn);
        System.out.println("Class type FQN: " + classType.getFullyQualifiedName());
        addType(ownerFqn, classType.getFullyQualifiedName());
    }

    void processAnnotations(JavaType.FullyQualified fullyQualified, String ownerFqn) {
        if (!fullyQualified.getAnnotations().isEmpty()) {
            for (JavaType.FullyQualified annotation : fullyQualified.getAnnotations()) {
                String annotationFqn = annotation.getFullyQualifiedName();
                System.out.println("Extra Annotation FQN: " + annotationFqn);
                addType(ownerFqn, annotationFqn);
            }
        }
    }

    void addType(String ownerFqn, String typeFqn) {
        classReferencesGraph.addVertex(ownerFqn);
        classReferencesGraph.addVertex(typeFqn);

        if (!classReferencesGraph.containsEdge(ownerFqn, typeFqn)) {
            classReferencesGraph.addEdge(ownerFqn, typeFqn);
        } else {
            DefaultWeightedEdge edge = classReferencesGraph.getEdge(ownerFqn, typeFqn);
            classReferencesGraph.setEdgeWeight(edge, classReferencesGraph.getEdgeWeight(edge) + 1);
        }
    }
}
