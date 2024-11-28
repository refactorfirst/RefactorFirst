package org.hjug.parser;

import java.util.List;
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
    //    Collection<String> fqns;

    public JavaVariableVisitor() {}

    public JavaVariableVisitor(Graph<String, DefaultWeightedEdge> classReferencesGraph) {
        this.classReferencesGraph = classReferencesGraph;
        //        this.fqns = fqns;
    }

    @Override
    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, P p) {
        J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, p);

        /*
         * Handles
         * java.lang.NullPointerException: Cannot invoke "org.openrewrite.java.tree.JavaType$Variable.getOwner()"
         * because the return value of
         * "org.openrewrite.java.tree.J$VariableDeclarations$NamedVariable.getVariableType()" is null
         */
        List<J.VariableDeclarations.NamedVariable> variables = variableDeclarations.getVariables();
        if (null == variables || variables.isEmpty() || null == variables.get(0).getVariableType()) {
            return variableDeclarations;
        }

        JavaType owner = variables.get(0).getVariableType().getOwner();
        String ownerFqn = "";

        if (owner instanceof JavaType.Method) {
            JavaType.Method m = (JavaType.Method) owner;
            //            log.debug("Method owner: " + m.getDeclaringType().getFullyQualifiedName());
            ownerFqn = m.getDeclaringType().getFullyQualifiedName();
        } else if (owner instanceof JavaType.Class) {
            JavaType.Class c = (JavaType.Class) owner;
            //            log.debug("Method owner: " + c.getFullyQualifiedName());
            ownerFqn = c.getFullyQualifiedName();
        }

        log.debug("*************************");
        log.debug("Processing " + ownerFqn + ":" + variableDeclarations);
        log.debug("*************************");

        TypeTree typeTree = variableDeclarations.getTypeExpression();

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
            if (annotation.getType() instanceof JavaType.Unknown) {
                continue;
            }

            JavaType.Class type = (JavaType.Class) annotation.getType();
            if (null != type) {
                String annotationFqn = type.getFullyQualifiedName();
                log.debug("Variable Annotation FQN: " + annotationFqn);
                addType(ownerFqn, annotationFqn);

                if (null != annotation.getArguments()) {
                    for (Expression argument : annotation.getArguments()) {
                        processType(argument.getType(), ownerFqn);
                    }
                }
            }
        }

        // skip primitive variable declarations
        if (javaType instanceof JavaType.Primitive) {
            return variableDeclarations;
        }

        processType(javaType, ownerFqn);

        return variableDeclarations;
    }

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
        log.debug("Parameterized type FQN : " + parameterized.getFullyQualifiedName());
        addType(ownerFqn, parameterized.getFullyQualifiedName());

        log.debug("Nested Parameterized type parameters: " + parameterized.getTypeParameters());
        for (JavaType parameter : parameterized.getTypeParameters()) {
            processType(parameter, ownerFqn);
        }
    }

    private void processType(JavaType.Array arrayType, String ownerFqn) {
        // D[] --> D
        log.debug("Array Element type: " + arrayType.getElemType());
        processType(arrayType.getElemType(), ownerFqn);
    }

    private void processType(JavaType.GenericTypeVariable typeVariable, String ownerFqn) {
        log.debug("Type parameter type name: " + typeVariable.getName());

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
        log.debug("Class type FQN: " + classType.getFullyQualifiedName());
        addType(ownerFqn, classType.getFullyQualifiedName());
    }

    void processAnnotations(JavaType.FullyQualified fullyQualified, String ownerFqn) {
        if (!fullyQualified.getAnnotations().isEmpty()) {
            for (JavaType.FullyQualified annotation : fullyQualified.getAnnotations()) {
                String annotationFqn = annotation.getFullyQualifiedName();
                log.debug("Extra Annotation FQN: " + annotationFqn);
                addType(ownerFqn, annotationFqn);
            }
        }
    }

    void addType(String ownerFqn, String typeFqn) {
        if (!ownerFqn.contains("java.lang") && !typeFqn.contains("java.lang")) {
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
}
