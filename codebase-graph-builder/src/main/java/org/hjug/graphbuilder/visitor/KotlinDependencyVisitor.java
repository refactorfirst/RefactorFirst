package org.hjug.graphbuilder.visitor;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.DependencyCollector;
import org.openrewrite.SourceFile;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.kotlin.KotlinIsoVisitor;
import org.openrewrite.kotlin.tree.K;

/**
 * Kotlin dependency visitor. Extends {@link KotlinIsoVisitor} so that K-level
 * compilation-unit entry point works, and overrides both the K-level entry
 * points (for Kotlin-specific nodes like {@link K.Property},
 * {@link K.TypeAlias}) and the J-level overrides inherited from
 * {@link KotlinIsoVisitor} — the latter are dispatched when walking the
 * inner {@code J.ClassDeclaration} / {@code J.VariableDeclarations} /
 * {@code J.MethodInvocation} nodes that the Kotlin parser wraps inside
 * {@code K.*} containers.
 *
 * <p>The J-level overrides delegate to {@link DependencyVisitorLogic} so the same
 * dependency-extraction logic records class/field/method dependencies for
 * Kotlin source as for Java.
 *
 * @param <P> the visitor context type
 */
@Slf4j
public class KotlinDependencyVisitor<P> extends KotlinIsoVisitor<P> {

    private final DependencyCollector dependencyCollector;

    private final String repositoryPath;

    private final BaseTypeProcessor typeProcessor;

    private final DependencyVisitorState state;

    public KotlinDependencyVisitor(
            String repositoryPath, String repositoryRoot, DependencyCollector dependencyCollector) {
        this.dependencyCollector = dependencyCollector;
        this.repositoryPath = repositoryPath;
        this.typeProcessor = new BaseTypeProcessor() {
            @Override
            protected DependencyCollector getDependencyCollector() {
                return dependencyCollector;
            }
        };
        this.state = new DependencyVisitorState(repositoryPath, repositoryRoot, typeProcessor);
        this.state.setSourceFileExtension(sourceFileExtension());
    }

    @Override
    public boolean isAcceptable(SourceFile sourceFile, P p) {
        return sourceFile instanceof K.CompilationUnit;
    }

    // ===================== K-level overrides =====================

    @Override
    public K.CompilationUnit visitCompilationUnit(K.CompilationUnit cu, P p) {
        J.Package packageDeclaration = cu.getPackageDeclaration();
        if (packageDeclaration != null) {
            dependencyCollector.registerPackage(packageDeclaration.getPackageName());
        }

        state.setCursor(getCursor());
        String packageName = packageDeclaration != null ? packageDeclaration.getPackageName() : "";
        DependencyVisitorLogic.registerPackage(state, packageName);
        DependencyVisitorLogic.enterCompilationUnit(
                state, packageName, cu.getSourcePath().toUri().toString());

        K.CompilationUnit c = super.visitCompilationUnit(cu, p);

        for (Statement statement : c.getStatements()) {
            // The Kotlin parser may surface top-level classes either wrapped in
            // K.ClassDeclaration or as bare J.ClassDeclaration elements
            // (depending on Kotlin language level / parsing code path). Handle
            // both shapes so every class declared in the CU is registered as a
            // graph vertex, even when its type dependencies could not be
            // attributed (e.g. references to Java files outside the Kotlin
            // parse batch).
            log.debug(
                    "CU Statement: {} - {}",
                    statement.getClass().getSimpleName(),
                    statement
                            .toString()
                            .substring(0, Math.min(100, statement.toString().length())));
            J.ClassDeclaration jcd = null;
            if (statement instanceof K.ClassDeclaration kcd) {
                jcd = kcd.getClassDeclaration();
            } else if (statement instanceof J.ClassDeclaration cd) {
                jcd = cd;
            }

            if (jcd != null && jcd.getType() != null) {
                String classFqn = jcd.getType().getFullyQualifiedName();
                String sourcePath = cu.getSourcePath().toUri().toString();
                log.debug("Kotlin Class FQN: {}, Source Path: {}", classFqn, sourcePath);

                dependencyCollector.registerClassVertex(classFqn);
                DependencyVisitorLogic.recordClassLocation(state, classFqn, sourcePath);
            } else if (jcd != null && jcd.getType() == null) {
                // Type attribution may fail when the referencing Java/Kotlin
                // classes are outside this Kotlin parse batch. Fall back to a
                // package + simple name derived FQN so the class still appears
                // as a graph vertex.
                String simpleName = jcd.getSimpleName();
                String pkg = packageDeclaration == null ? "" : packageDeclaration.getPackageName();
                String classFqn = pkg.isEmpty() ? simpleName : pkg + "." + simpleName;
                String sourcePath = cu.getSourcePath().toUri().toString();
                log.debug("Kotlin Class FQN (un-attributed): {}, Source Path: {}", classFqn, sourcePath);

                dependencyCollector.registerClassVertex(classFqn);
                DependencyVisitorLogic.recordClassLocation(state, classFqn, sourcePath);
            }
        }

        return c;
    }

    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, P p) {
        throw new UnsupportedOperationException("Kotlin compilation unit should be visited via K.CompilationUnit.");
    }

    @Override
    public K.ClassDeclaration visitClassDeclaration(K.ClassDeclaration classDeclaration, P p) {
        J.ClassDeclaration jcd = classDeclaration.getClassDeclaration();
        if (jcd == null) {
            return super.visitClassDeclaration(classDeclaration, p);
        }

        JavaType.FullyQualified type = jcd.getType();
        if (type == null) {
            log.warn("Kotlin ClassDeclaration has null type, skipping: {}", jcd.getSimpleName());
            return super.visitClassDeclaration(classDeclaration, p);
        }

        String owningFqn = type.getFullyQualifiedName();

        state.setCursor(getCursor());

        K.ClassDeclaration result = super.visitClassDeclaration(classDeclaration, p);

        // Get source path for this class
        String sourcePath = null;
        J.CompilationUnit enclosingCu = getCursor().firstEnclosing(J.CompilationUnit.class);
        if (enclosingCu != null) {
            sourcePath = enclosingCu.getSourcePath().toUri().toString();
        } else {
            K.CompilationUnit kcu = getCursor().firstEnclosing(K.CompilationUnit.class);
            sourcePath = kcu != null ? kcu.getSourcePath().toUri().toString() : null;
        }

        // Record source location for both top-level and inner classes
        if (sourcePath != null) {
            log.debug("Kotlin Class FQN (from K.ClassDeclaration): {}, Source Path: {}", owningFqn, sourcePath);
            dependencyCollector.registerClassVertex(owningFqn);
            DependencyVisitorLogic.recordClassLocation(state, owningFqn, sourcePath);
        }

        // Process Kotlin-specific: type constraints
        if (classDeclaration.getTypeConstraints() != null) {
            for (J.TypeParameter typeParameter :
                    classDeclaration.getTypeConstraints().getConstraints()) {
                typeProcessor.processTypeParameter(owningFqn, typeParameter, getCursor());
            }
        }

        return result;
    }

    @Override
    public K.MethodDeclaration visitMethodDeclaration(K.MethodDeclaration methodDeclaration, P p) {
        J.MethodDeclaration jmd = methodDeclaration.getMethodDeclaration();
        if (jmd == null) {
            return super.visitMethodDeclaration(methodDeclaration, p);
        }

        K.MethodDeclaration result = super.visitMethodDeclaration(methodDeclaration, p);

        JavaType.Method methodType = jmd.getMethodType();
        if (methodType != null && methodType.getDeclaringType() != null) {
            String owner = methodType.getDeclaringType().getFullyQualifiedName();

            state.setCursor(getCursor());
            DependencyVisitorLogic.handleMethodDeclaration(state, jmd);

            // Process Kotlin-specific: type constraints
            if (methodDeclaration.getTypeConstraints() != null) {
                for (J.TypeParameter typeParameter :
                        methodDeclaration.getTypeConstraints().getConstraints()) {
                    typeProcessor.processTypeParameter(owner, typeParameter, getCursor());
                }
            }
        }

        return result;
    }

    @Override
    public K.Property visitProperty(K.Property property, P p) {
        K.Property result = super.visitProperty(property, p);
        if (state.getCurrentOwnerFqn() == null) {
            return result;
        }

        J.VariableDeclarations variableDeclarations = property.getVariableDeclarations();
        if (variableDeclarations == null) {
            return result;
        }

        TypeTree typeTree = variableDeclarations.getTypeExpression();
        if (typeTree == null) {
            return result;
        }

        JavaType javaType = typeTree.getType();
        if (javaType instanceof JavaType.Primitive) {
            return result;
        }

        state.setCursor(getCursor());
        typeProcessor.processType(state.getCurrentOwnerFqn(), javaType);

        if (property.getTypeParameters() != null) {
            for (J.TypeParameter typeParameter : property.getTypeParameters()) {
                typeProcessor.processTypeParameter(state.getCurrentOwnerFqn(), typeParameter, getCursor());
            }
        }

        if (property.getReceiver() != null) {
            typeProcessor.processType(
                    state.getCurrentOwnerFqn(), property.getReceiver().getType());
        }

        return result;
    }

    @Override
    public K.TypeAlias visitTypeAlias(K.TypeAlias typeAlias, P p) {
        K.TypeAlias result = super.visitTypeAlias(typeAlias, p);
        if (state.getCurrentOwnerFqn() == null) {
            return result;
        }

        if (typeAlias.getTypeParameters() != null) {
            for (J.TypeParameter typeParameter : typeAlias.getTypeParameters()) {
                typeProcessor.processTypeParameter(state.getCurrentOwnerFqn(), typeParameter, getCursor());
            }
        }

        if (typeAlias.getPadding().getInitializer() != null) {
            Expression init = typeAlias.getPadding().getInitializer().getElement();
            if (init != null) {
                typeProcessor.processType(state.getCurrentOwnerFqn(), init.getType());
            }
        }

        return result;
    }

    // ===================== J-level overrides (delegate to DependencyVisitorLogic) =====================
    // These are needed because KotlinIsoVisitor inherits from OpenRewrite's JavaIsoVisitor,
    // NOT from our AbstractDependencyVisitor. When the Kotlin AST's inner J.* nodes are
    // visited, these J-level methods are dispatched.

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
        state.setCursor(getCursor());

        // Get source path for this class
        String sourcePath = null;
        J.CompilationUnit enclosingCu = getCursor().firstEnclosing(J.CompilationUnit.class);
        if (enclosingCu != null) {
            sourcePath = enclosingCu.getSourcePath().toUri().toString();
        } else {
            K.CompilationUnit kcu = getCursor().firstEnclosing(K.CompilationUnit.class);
            sourcePath = kcu != null ? kcu.getSourcePath().toUri().toString() : null;
        }

        // Record source location for both top-level and inner classes
        if (sourcePath != null) {
            JavaType.FullyQualified type = classDecl.getType();
            if (type != null) {
                String classFqn = type.getFullyQualifiedName();
                log.debug("Kotlin Class FQN (from J.ClassDeclaration): {}, Source Path: {}", classFqn, sourcePath);
                dependencyCollector.registerClassVertex(classFqn);
                DependencyVisitorLogic.recordClassLocation(state, classFqn, sourcePath);
            }
        }

        var snapshot = DependencyVisitorLogic.enterClassDeclaration(
                state,
                classDecl,
                false, // Kotlin doesn't process record components in J-level visit
                cursor -> {
                    J.CompilationUnit cu = cursor.firstEnclosing(J.CompilationUnit.class);
                    if (cu != null) {
                        return cu.getSourcePath().toUri().toString();
                    }
                    K.CompilationUnit kcu = cursor.firstEnclosing(K.CompilationUnit.class);
                    return kcu != null ? kcu.getSourcePath().toUri().toString() : null;
                });
        try {
            return super.visitClassDeclaration(classDecl, p);
        } finally {
            DependencyVisitorLogic.leaveClassDeclaration(state, snapshot);
        }
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

    // ============ internal helpers ============

    /**
     * Source-file extension used when synthetic source paths are produced for
     * junit-based tests where the parser's URI is not usable as a repo path.
     * Mirrors {@link AbstractDependencyVisitor#sourceFileExtension()} — the two
     * visitors cannot share a common base because {@code KotlinIsoVisitor}
     * extends {@code KotlinVisitor} (not {@code JavaIsoVisitor}), so the hook is
     * duplicated on each visitor. Kotlin returns {@code ".kt"}.
     */
    protected String sourceFileExtension() {
        return ".kt";
    }

    /**
     * Returns the class-to-source-file-path mapping collected during the visit.
     * Delegates to the internal state.
     */
    public Map<String, String> getClassToSourceFilePathMapping() {
        return state.getClassToSourceFilePathMapping();
    }
}
