package org.hjug.graphbuilder.visitor;

import java.util.List;
import lombok.Getter;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.TypeTree;

public class JavaMethodDeclarationVisitor<P> extends JavaIsoVisitor<P> implements TypeProcessor {

    @Getter
    private Graph<String, DefaultWeightedEdge> classReferencesGraph =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

    @Getter
    private Graph<String, DefaultWeightedEdge> packageReferencesGraph =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

    public JavaMethodDeclarationVisitor() {}

    public JavaMethodDeclarationVisitor(
            Graph<String, DefaultWeightedEdge> classReferencesGraph,
            Graph<String, DefaultWeightedEdge> packageReferencesGraph) {
        this.classReferencesGraph = classReferencesGraph;
        this.packageReferencesGraph = packageReferencesGraph;
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, P p) {
        J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(method, p);

        String owner = methodDeclaration.getMethodType().getDeclaringType().getFullyQualifiedName();

        // if returnTypeExpression is null, a constructor declaration is being processed
        TypeTree returnTypeExpression = methodDeclaration.getReturnTypeExpression();
        if (returnTypeExpression != null) {
            JavaType returnType = returnTypeExpression.getType();

            // skip primitive variable declarations
            if (!(returnType instanceof JavaType.Primitive)) {
                processType(owner, returnType);
            }
        }

        for (J.Annotation leadingAnnotation : methodDeclaration.getLeadingAnnotations()) {
            processType(owner, leadingAnnotation.getType());
        }

        if (null != methodDeclaration.getTypeParameters()) {
            for (J.TypeParameter typeParameter : methodDeclaration.getTypeParameters()) {
                processTypeParameter(owner, typeParameter);
            }
        }

        // don't need to capture parameter declarations
        // they are captured in JavaVariableTypeVisitor

        List<NameTree> throwz = methodDeclaration.getThrows();
        if (null != throwz && !throwz.isEmpty()) {
            for (NameTree thrown : throwz) {
                processType(owner, thrown.getType());
            }
        }

        return methodDeclaration;
    }
}
