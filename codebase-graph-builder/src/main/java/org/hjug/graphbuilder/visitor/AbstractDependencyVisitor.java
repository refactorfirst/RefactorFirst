package org.hjug.graphbuilder.visitor;

import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.DependencyCollector;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Javadoc;

/**
 * Shared dependency-extraction visitor logic that operates on the J-level
 * (Java) AST nodes common to both Java and Kotlin source trees parsed by
 * OpenRewrite. Both {@link JavaVisitor} (for {@code J.CompilationUnit}) and
 * {@code KotlinDependencyVisitor} (for {@code K.CompilationUnit}) derive from
 * this base so that the same J-level overrides record class/method/field
 * dependencies regardless of the source language.
 *
 * <p>This class delegates all J-level logic to {@link DependencyVisitorLogic},
 * ensuring a single source of truth and eliminating fork-and-drift between
 * the Java and Kotlin visitors.
 *
 * @param <P> the visitor context type
 */
@Slf4j
public abstract class AbstractDependencyVisitor<P> extends JavaIsoVisitor<P> {

    @Getter
    private final DependencyVisitorState state;

    protected AbstractDependencyVisitor(
            String repositoryPath, String repositoryRoot, DependencyCollector dependencyCollector) {
        BaseTypeProcessor typeProcessor = new BaseTypeProcessor() {
            @Override
            protected DependencyCollector getDependencyCollector() {
                return dependencyCollector;
            }
        };
        this.state = new DependencyVisitorState(repositoryPath, repositoryRoot, typeProcessor);
        this.state.setSourceFileExtension(sourceFileExtension());
    }

    /**
     * Returns a JavadocVisitor that does nothing.  This is done to prevent the visitor from including references in
     * Javadocs as members of cycles
     * @return JavadocVisitor that does nothing.
     */
    @Override
    protected JavadocVisitor<P> getJavadocVisitor() {
        return new JavadocVisitor<>(this) {
            @Override
            public Javadoc visitDocComment(Javadoc.DocComment docComment, P p) {
                return docComment;
            }
        };
    }

    /**
     * Source-file extension used when synthetic source paths are produced for junit-based
     * tests (where the parser's URI is not usable as a repo path). Java returns {@code ".java"},
     * Kotlin returns {@code ".kt"}.
     */
    protected String sourceFileExtension() {
        return ".java";
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
        state.setCursor(getCursor());
        var snapshot = DependencyVisitorLogic.enterClassDeclaration(
                state,
                classDecl,
                true, // processRecordComponents = true for Java
                cursor -> {
                    J.CompilationUnit cu = cursor.firstEnclosing(J.CompilationUnit.class);
                    return cu != null ? cu.getSourcePath().toUri().toString() : null;
                });
        try {
            return super.visitClassDeclaration(classDecl, p);
        } finally {
            DependencyVisitorLogic.leaveClassDeclaration(state, snapshot);
        }
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, P p) {
        J.Package packageDeclaration = compilationUnit.getPackageDeclaration();
        if (null == packageDeclaration) {
            return compilationUnit;
        }

        state.setCursor(getCursor());
        String packageName = packageDeclaration.getPackageName();
        DependencyVisitorLogic.registerPackage(state, packageName);
        DependencyVisitorLogic.enterCompilationUnit(
                state, packageName, compilationUnit.getSourcePath().toUri().toString());

        for (J.ClassDeclaration aClass : compilationUnit.getClasses()) {
            JavaType.FullyQualified type = aClass.getType();
            if (type == null) {
                log.warn("ClassDeclaration has null type, skipping: {}", aClass.getSimpleName());
                continue;
            }
            String classFqn = type.getFullyQualifiedName();
            String sourcePath = compilationUnit.getSourcePath().toUri().toString();
            log.debug("Class FQN: {}, Source Path: {}", classFqn, sourcePath);

            // Ensure the class is registered as a vertex even if it has no dependencies.
            state.getTypeProcessor().getDependencyCollector().registerClassVertex(classFqn);

            DependencyVisitorLogic.recordClassLocation(state, classFqn, sourcePath);
        }

        return super.visitCompilationUnit(compilationUnit, p);
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, P p) {
        state.setCursor(getCursor());
        J.MethodInvocation result = super.visitMethodInvocation(method, p);
        DependencyVisitorLogic.handleMethodInvocation(state, result);
        return result;
    }

    @Override
    public J.NewClass visitNewClass(J.NewClass newClass, P p) {
        state.setCursor(getCursor());
        J.NewClass result = super.visitNewClass(newClass, p);
        DependencyVisitorLogic.handleNewClass(state, result);
        return result;
    }

    @Override
    public J.Lambda visitLambda(J.Lambda lambda, P p) {
        state.setCursor(getCursor());
        J.Lambda result = super.visitLambda(lambda, p);
        DependencyVisitorLogic.handleLambda(state, result);
        return result;
    }

    @Override
    public J.InstanceOf visitInstanceOf(J.InstanceOf instanceOf, P p) {
        state.setCursor(getCursor());
        J.InstanceOf result = super.visitInstanceOf(instanceOf, p);
        DependencyVisitorLogic.handleInstanceOf(state, result);
        return result;
    }

    @Override
    public J.TypeCast visitTypeCast(J.TypeCast typeCast, P p) {
        state.setCursor(getCursor());
        J.TypeCast result = super.visitTypeCast(typeCast, p);
        DependencyVisitorLogic.handleTypeCast(state, result);
        return result;
    }

    @Override
    public J.MemberReference visitMemberReference(J.MemberReference memberRef, P p) {
        state.setCursor(getCursor());
        J.MemberReference result = super.visitMemberReference(memberRef, p);
        DependencyVisitorLogic.handleMemberReference(state, result);
        return result;
    }

    @Override
    public J.NewArray visitNewArray(J.NewArray newArray, P p) {
        state.setCursor(getCursor());
        J.NewArray result = super.visitNewArray(newArray, p);
        DependencyVisitorLogic.handleNewArray(state, result);
        return result;
    }

    @Override
    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, P p) {
        state.setCursor(getCursor());
        J.VariableDeclarations result = super.visitVariableDeclarations(multiVariable, p);
        DependencyVisitorLogic.handleVariableDeclarations(state, result);
        return result;
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, P p) {
        state.setCursor(getCursor());
        J.MethodDeclaration result = super.visitMethodDeclaration(method, p);
        DependencyVisitorLogic.handleMethodDeclaration(state, result);
        return result;
    }

    /**
     * Returns the class-to-source-file-path mapping collected during the visit.
     * Delegates to the internal state.
     */
    public Map<String, String> getClassToSourceFilePathMapping() {
        return state.getClassToSourceFilePathMapping();
    }
}
