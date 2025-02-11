package org.hjug.parser.visitor;

import java.util.List;
import lombok.Getter;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;

public class JavaClassDeclarationVisitor<P> extends JavaIsoVisitor<P> implements TypeProcessor {

    private final JavaMethodInvocationVisitor methodInvocationVisitor;
    private final JavaNewClassVisitor newClassVisitor;

    @Getter
    private Graph<String, DefaultWeightedEdge> classReferencesGraph =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

    @Getter
    private Graph<String, DefaultWeightedEdge> packageReferencesGraph =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

    public JavaClassDeclarationVisitor() {
        methodInvocationVisitor = new JavaMethodInvocationVisitor(classReferencesGraph, packageReferencesGraph);
        newClassVisitor = new JavaNewClassVisitor(classReferencesGraph, packageReferencesGraph);
    }

    public JavaClassDeclarationVisitor(
            Graph<String, DefaultWeightedEdge> classReferencesGraph,
            Graph<String, DefaultWeightedEdge> packageReferencesGraph) {
        this.classReferencesGraph = classReferencesGraph;
        this.packageReferencesGraph = packageReferencesGraph;
        methodInvocationVisitor = new JavaMethodInvocationVisitor(classReferencesGraph, packageReferencesGraph);
        newClassVisitor = new JavaNewClassVisitor(classReferencesGraph, packageReferencesGraph);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
        J.ClassDeclaration classDeclaration = super.visitClassDeclaration(classDecl, p);

        JavaType.FullyQualified type = classDeclaration.getType();
        String owningFqn = type.getFullyQualifiedName();

        processType(owningFqn, type);

        TypeTree extendsTypeTree = classDeclaration.getExtends();
        if (null != extendsTypeTree) {
            processType(owningFqn, extendsTypeTree.getType());
        }

        List<TypeTree> implementsTypeTree = classDeclaration.getImplements();
        if (null != implementsTypeTree) {
            for (TypeTree typeTree : implementsTypeTree) {
                processType(owningFqn, typeTree.getType());
            }
        }

        for (J.Annotation leadingAnnotation : classDeclaration.getLeadingAnnotations()) {
            processAnnotation(owningFqn, leadingAnnotation);
        }

        if (null != classDeclaration.getTypeParameters()) {
            for (J.TypeParameter typeParameter : classDeclaration.getTypeParameters()) {
                processTypeParameter(owningFqn, typeParameter);
            }
        }

        // process method invocations and lambda invocations
        processInvocations(classDeclaration);

        return classDeclaration;
    }

    private void processInvocations(J.ClassDeclaration classDeclaration) {
        JavaType.FullyQualified type = classDeclaration.getType();
        String owningFqn = type.getFullyQualifiedName();

        for (Statement statement : classDeclaration.getBody().getStatements()) {
            if (statement instanceof J.Block) {
                processBlock((J.Block) statement, owningFqn);
            }
            if (statement instanceof J.MethodDeclaration) {
                J.MethodDeclaration methodDeclaration = (J.MethodDeclaration) statement;
                processBlock(methodDeclaration.getBody(), owningFqn);
            }
        }
    }

    private void processBlock(J.Block block, String owningFqn) {
        if (null != block && null != block.getStatements()) {
            for (Statement statementInBlock : block.getStatements()) {
                if (statementInBlock instanceof J.MethodInvocation) {
                    J.MethodInvocation methodInvocation = (J.MethodInvocation) statementInBlock;
                    methodInvocationVisitor.visitMethodInvocation(owningFqn, methodInvocation);
                } else if (statementInBlock instanceof J.Lambda) {
                    J.Lambda lambda = (J.Lambda) statementInBlock;
                    processType(owningFqn, lambda.getType());
                } else if (statementInBlock instanceof J.NewClass) {
                    J.NewClass newClass = (J.NewClass) statementInBlock;
                    newClassVisitor.visitNewClass(owningFqn, newClass);
                } else if (statementInBlock instanceof J.Return) {
                    J.Return returnStmt = (J.Return) statementInBlock;
                    visitReturn(owningFqn, returnStmt);
                }
            }
        }
    }

    public J.Return visitReturn(String owningFqn, J.Return visitedReturn) {
        Expression expression = visitedReturn.getExpression();
        if (expression instanceof J.MethodInvocation) {
            J.MethodInvocation methodInvocation = (J.MethodInvocation) expression;
            methodInvocationVisitor.visitMethodInvocation(owningFqn, methodInvocation);
        } else if (expression instanceof J.NewClass) {
            J.NewClass newClass = (J.NewClass) expression;
            newClassVisitor.visitNewClass(owningFqn, newClass);
        } else if (expression instanceof J.Lambda) {
            J.Lambda lambda = (J.Lambda) expression;
            processType(owningFqn, lambda.getType());
        }

        return visitedReturn;
    }
}
