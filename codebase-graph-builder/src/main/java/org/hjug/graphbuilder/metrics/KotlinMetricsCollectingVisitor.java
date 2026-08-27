package org.hjug.graphbuilder.metrics;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.Javadoc;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;

/**
 * Kotlin source-file metrics-collecting visitor.
 *
 * <p>Carries a {@link MetricsVisitorState} and forwards each J-level
 * {@code visitXxx} override to the shared {@link MetricsVisitorLogic}
 * helpers, exactly as {@link MetricsCollectingVisitor} does for Java
 * sources. The Kotlin parser wraps inner {@code J.*} nodes inside
 * {@code K.*} containers, but the J-level overrides on this class are
 * dispatched when walking a {@link K.CompilationUnit} (because
 * {@code KotlinIsoVisitor} inherits them from {@code JavaIsoVisitor}).
 *
 * <p>The only Kotlin-specific differences surface as:
 *
 * <ul>
 *   <li>{@link #visitCompilationUnit(K.CompilationUnit, ExecutionContext)}
 *       tracks {@code currentSourcePath} from the {@code K.CompilationUnit}'s
 *       source path (so {@link ClassMetrics#setSourceFilePath(String)})
 *       receives a {@code .kt} path instead of a {@code .java} path).
 *   <li>{@link #isOverrideAnnotation(String)} additionally recognises
 *       Kotlin's {@code @JvmOverride} marker alongside standard
 *       {@code @Override}.
 * </ul>
 */
public class KotlinMetricsCollectingVisitor extends KotlinIsoVisitor<ExecutionContext> {

    private final GraphMetricsCollector metricsCollector;
    private final MetricsVisitorState state = new MetricsVisitorState();

    public KotlinMetricsCollectingVisitor(GraphMetricsCollector metricsCollector) {
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
    public K.CompilationUnit visitCompilationUnit(K.CompilationUnit cu, ExecutionContext ctx) {
        MetricsVisitorLogic.enterCompilationUnit(state, cu.getSourcePath().toString());
        return super.visitCompilationUnit(cu, ctx);
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
        // Kotlin source files surface as K.CompilationUnit; this method is a safety net
        // for any unexpected J.CompilationUnit dispatch paths.
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
        // Kotlin's `override` keyword is surfaced by the OpenRewrite parser as a
        // J.Modifier of type LanguageExtension with keyword "override" (it is NOT
        // an annotation — nothing lands on getLeadingAnnotations()). The shared
        // enterMethod only inspects annotations, so we record the override here
        // on the owning class's overriddenMethods set when the modifier is
        // present. The tradition-breaker / refused-parent-bequest detectors
        // (which gate on numberOfOverriddenMethods / NAS / PNAS) depend on this
        // record being present for Kotlin.
        if (snapshot != null && state.currentClassMetrics != null && hasKotlinOverrideModifier(method)) {
            state.currentClassMetrics.addOverriddenMethod(state.currentMethodSignature);
        }
        J.MethodDeclaration result = super.visitMethodDeclaration(method, ctx);
        boolean hasExtensionMarker = method.getMarkers().getMarkers().stream()
                .anyMatch(m -> "org.openrewrite.kotlin.marker.Extension"
                        .equals(m.getClass().getName()));
        MetricsVisitorLogic.handleKotlinExtensionFunction(state, method, hasExtensionMarker);
        MetricsVisitorLogic.leaveMethod(state, metricsCollector, snapshot);
        return result;
    }

    /**
     * Kotlin's {@code override} keyword is surfaced by the OpenRewrite parser
     * as a {@link J.Modifier} whose {@code getKeyword()} returns
     * {@code "override"} (the modifier lives in the
     * {@code LanguageExtension} subtype, not the standard Java modifier enum).
     * Returns {@code true} when the method declaration carries that keyword
     * on any of its modifiers.
     */
    private boolean hasKotlinOverrideModifier(J.MethodDeclaration method) {
        for (J.Modifier mod : method.getModifiers()) {
            if ("override".equals(mod.getKeyword())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kotlin wraps the inner {@link J.MethodDeclaration} in a
     * {@link K.MethodDeclaration}. The K-level surface exposes the method's
     * <em>type constraints</em> (the Kotlin generalization of Java's
     * {@code J.TypeParameter.getBounds()}) via
     * {@link K.MethodDeclaration#getTypeConstraints()}. Record those bounds'
     * FQNs on the method and owning class metrics so Kotlin generic method
     * syntax that the J-level walk would miss is covered by the
     * type-parameter metric collection.
     *
     * <p>Extension-function bookkeeping is intentionally handled in
     * {@link #visitMethodDeclaration(J.MethodDeclaration, ExecutionContext)}
     * rather than here: the K-level override is dispatched in some (but not
     * all) OpenRewrite parser code paths, while the J-level override is
     * always dispatched for both Java and Kotlin methods. The
     * {@code org.openrewrite.kotlin.marker.Extension} marker is observable
     * on the inner {@link J.MethodDeclaration}, so the J-level hook is
     * sufficient.
     */
    @Override
    public K.MethodDeclaration visitMethodDeclaration(K.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
        if (state.currentMethodMetrics != null && methodDeclaration.getTypeConstraints() != null) {
            MetricsVisitorLogic.collectTypeParameterFqns(
                    methodDeclaration.getTypeConstraints().getConstraints(),
                    state.currentMethodMetrics,
                    state.currentClassMetrics);
        }
        K.MethodDeclaration result = super.visitMethodDeclaration(methodDeclaration, ctx);
        return result;
    }

    @Override
    public J.VariableDeclarations visitVariableDeclarations(
            J.VariableDeclarations multiVariable, ExecutionContext ctx) {
        // Kotlin has no language-level default visibility; properties
        // declared without an explicit modifier are `public` (unlike Java's
        // package-private default). The shared metrics helper therefore gets
        // `defaultPublicWhenAbsent=true` so Kotlin `var x: Int` at class scope
        // is recorded with numberOfPublicAttributes (used by the Data Class
        // detector's public-accessors gate).
        MetricsVisitorLogic.handleVariableDeclarations(state, getCursor(), multiVariable, true);
        return super.visitVariableDeclarations(multiVariable, ctx);
    }

    /**
     * Kotlin-property-shape override. Class-level {@code val}/{@code var}
     * declarations surface as {@link K.Property} nodes whose inner
     * {@link J.VariableDeclarations} is walked by {@link KotlinIsoVisitor}'s
     * default implementation, in turn dispatching
     * {@link #visitVariableDeclarations(J.VariableDeclarations, ExecutionContext)}
     * — which records the property as an attribute via
     * {@link MetricsVisitorLogic#handleVariableDeclarations}.
     *
     * <p>This override also records type-parameter FQNs from Kotlin property
     * shapes that the J-level walk does not surface:
     * <ul>
     *   <li>{@link K.Property#getTypeParameters()} — generic property
     *       declarations (rare, but supported by the Kotlin grammar);
     *       bounded type-parameter FQNs land on the owning class metrics.</li>
     *   <li>{@link K.Property#getReceiver()} — extension-property receiver
     *       type FQN, recorded on the owning class metrics.</li>
     * </ul>
     *
     * <p>It is also the future hook site for extension-property counting via
     * {@link K.Property#getReceiver()} (a non-null receiver on a Kotlin
     * property denotes an extension property).
     *
     * <p>Top-level properties (those declared at file scope, outside any
     * class) also enter here. They have {@code state.currentClassName == null};
     * {@link MetricsVisitorLogic#handleVariableDeclarations} no-ops in that
     * case, so top-level properties are safely ignored by the metrics
     * collector (they are not tied to any class).
     */
    @Override
    public K.Property visitProperty(K.Property property, ExecutionContext ctx) {
        K.Property result = super.visitProperty(property, ctx);
        if (state.currentClassMetrics == null) {
            return result;
        }
        MetricsVisitorLogic.collectTypeParameterFqns(property.getTypeParameters(), state.currentClassMetrics);
        if (property.getReceiver() != null && property.getReceiver().getType() != null) {
            recordBoundFqn(property.getReceiver().getType(), state.currentClassMetrics);
        }
        return result;
    }

    private void recordBoundFqn(JavaType type, ClassMetrics classMetrics) {
        MetricsVisitorLogic.collectTypeParameterFqnsFromType(type, classMetrics);
    }

    /**
     * Kotlin {@code typealias} declarations surface as
     * {@link K.TypeAlias}. Top-level type aliases have no owning class
     * (the visitor's {@code state.currentClassName} is {@code null}) and
     * are intentionally no-ops for metric collection. When the parser
     * does surface a typealias inside a class body (e.g. nested classes
     * via a different mechanism), the type-alias's type-parameter bounds
     * and the initializer's referenced classes get recorded on the
     * owning class's {@code typeParameterFqns} set.
     */
    @Override
    public K.TypeAlias visitTypeAlias(K.TypeAlias typeAlias, ExecutionContext ctx) {
        K.TypeAlias result = super.visitTypeAlias(typeAlias, ctx);
        if (state.currentClassMetrics == null) {
            return result;
        }
        MetricsVisitorLogic.collectTypeParameterFqns(typeAlias.getTypeParameters(), state.currentClassMetrics);
        if (typeAlias.getPadding() != null
                && typeAlias.getPadding().getInitializer() != null
                && typeAlias.getPadding().getInitializer().getElement() != null) {
            JavaType initType =
                    typeAlias.getPadding().getInitializer().getElement().getType();
            MetricsVisitorLogic.collectTypeParameterFqnsFromType(initType, state.currentClassMetrics);
        }
        return result;
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
     * Recognises Java's {@code @Override} annotation.
     * Kotlin's {@code override} modifier is handled separately via
     * {@link #hasKotlinOverrideModifier(org.openrewrite.java.tree.J.MethodDeclaration)}.
     */
    protected boolean isOverrideAnnotation(String simpleName) {
        return "Override".equals(simpleName);
    }
}
