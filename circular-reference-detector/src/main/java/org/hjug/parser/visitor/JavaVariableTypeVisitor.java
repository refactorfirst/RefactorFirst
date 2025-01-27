package org.hjug.parser.visitor;

import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;

@Slf4j
public class JavaVariableTypeVisitor<P> extends JavaIsoVisitor<P> implements TypeProcessor{

    @Getter
    private Graph<String, DefaultWeightedEdge> classReferencesGraph =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

    public JavaVariableTypeVisitor() {}

    public JavaVariableTypeVisitor(Graph<String, DefaultWeightedEdge> classReferencesGraph) {
        this.classReferencesGraph = classReferencesGraph;
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
            processAnnotation(ownerFqn, annotation);
        }

        // skip primitive variable declarations
        if (javaType instanceof JavaType.Primitive) {
            return variableDeclarations;
        }

        processType(ownerFqn, javaType);

        return variableDeclarations;
    }



    public void addType(String ownerFqn, String typeFqn) {
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
