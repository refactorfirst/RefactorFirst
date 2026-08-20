package org.hjug.graphbuilder.metrics;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.Javadoc;

/**
 * Java source-file metrics-collecting visitor.
 *
 * <p>Carries a {@link MetricsVisitorState} and forwards each J-level
 * {@code visitXxx} override to the shared {@link MetricsVisitorLogic}
 * helpers. The helpers contain the actual metric-recording algorithm;
 * this class is the thin language-specific wiring.
 *
 * <p>{@link KotlinMetricsCollectingVisitor} mirrors this delegation
 * pattern against the Kotlin {@code K.CompilationUnit} so the
 * metric-recording algorithm is single-sourced.
 */
public class MetricsCollectingVisitor extends JavaIsoVisitor<ExecutionContext> {

    private final GraphMetricsCollector metricsCollector;
    private final MetricsVisitorState state = new MetricsVisitorState();

    public MetricsCollectingVisitor(GraphMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    /**
     * Returns a JavadocVisitor that does nothing. This is done to prevent the
     * visitor from including references in Javadocs as metric counts.
     */
    @Override
    protected JavadocVisitor<ExecutionContext> getJavadocVisitor() {
        return new JavadocVisitor<>(this) {
            @Override
            public Javadoc visitDocComment(Javadoc.DocComment docComment, ExecutionContext ctx) {
                return docComment;
            }
        };
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
        MetricsVisitorLogic.enterCompilationUnit(state, cu.getSourcePath().toString());
        return super.visitCompilationUnit(cu, ctx);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        MetricsVisitorLogic.ClassStateSnapshot snapshot =
                MetricsVisitorLogic.enterClass(state, metricsCollector, classDecl);
        J.ClassDeclaration result = super.visitClassDeclaration(classDecl, ctx);
        MetricsVisitorLogic.leaveClass(state, metricsCollector, classDecl, snapshot);
        return result;
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        MetricsVisitorLogic.MethodStateSnapshot snapshot =
                MetricsVisitorLogic.enterMethod(state, method, this::isOverrideAnnotation);
        J.MethodDeclaration result = super.visitMethodDeclaration(method, ctx);
        MetricsVisitorLogic.leaveMethod(state, metricsCollector, snapshot);
        return result;
    }

    @Override
    public J.VariableDeclarations visitVariableDeclarations(
            J.VariableDeclarations multiVariable, ExecutionContext ctx) {
        MetricsVisitorLogic.handleVariableDeclarations(state, getCursor(), multiVariable);
        return super.visitVariableDeclarations(multiVariable, ctx);
    }

    @Override
    public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
        MetricsVisitorLogic.handleIdentifier(state, identifier);
        return super.visitIdentifier(identifier, ctx);
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
        MetricsVisitorLogic.handleMethodInvocation(state, metricsCollector, method);
        return super.visitMethodInvocation(method, ctx);
    }

    @Override
    public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
        MetricsVisitorLogic.handleFieldAccess(state, fieldAccess);
        return super.visitFieldAccess(fieldAccess, ctx);
    }

    @Override
    public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
        MetricsVisitorLogic.handleMemberReference(state, metricsCollector, memberRef);
        return super.visitMemberReference(memberRef, ctx);
    }

    /**
     * Java recognises the {@code @Override} marker only.
     */
    protected boolean isOverrideAnnotation(String simpleName) {
        return "Override".equals(simpleName);
    }
}
