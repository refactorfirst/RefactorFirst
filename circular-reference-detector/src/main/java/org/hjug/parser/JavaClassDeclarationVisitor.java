package org.hjug.parser;

import lombok.Getter;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;

import java.util.List;

public class JavaClassDeclarationVisitor <P> extends JavaIsoVisitor<P> implements TypeProcessor{


    @Getter
    private Graph<String, DefaultWeightedEdge> classReferencesGraph =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

    public JavaClassDeclarationVisitor() {
    }

    public JavaClassDeclarationVisitor(Graph<String, DefaultWeightedEdge> classReferencesGraph) {
        this.classReferencesGraph = classReferencesGraph;
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
        J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, p);

        JavaType.FullyQualified type = classDeclaration.getType();
        String owningFqn = type.getFullyQualifiedName();

        processType(type, owningFqn);

        TypeTree extendsTypeTree = classDeclaration.getExtends();
        if (null != extendsTypeTree) {
            processType(extendsTypeTree.getType(), owningFqn);
        }

        List<TypeTree> implementsTypeTree = classDeclaration.getImplements();
        if(null != implementsTypeTree) {
            for (TypeTree typeTree : implementsTypeTree) {
                processType(typeTree.getType(), owningFqn);
            }
        }

        for (J.Annotation leadingAnnotation : classDeclaration.getLeadingAnnotations()) {
            processAnnotation(leadingAnnotation, owningFqn);
        }

        return classDeclaration;
    }

    public void addType(String ownerFqn, String typeFqn) {
        if(ownerFqn.equals(typeFqn)) return;

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
