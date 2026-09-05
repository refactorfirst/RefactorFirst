package org.hjug.graphbuilder.visitor;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.openrewrite.java.tree.*;

/**
 * Static dependency-extraction logic shared between
 * {@link AbstractDependencyVisitor} (Java) and
 * {@link KotlinDependencyVisitor} (Kotlin).
 *
 * <p>Both visitors keep their own {@link DependencyVisitorState} and call into
 * these helpers from their J-level {@code visitXxx} overrides. State is
 * threaded in/out via the {@code state} parameter so the helpers can mutate
 * {@code currentOwnerFqn}, {@code classToSourceFilePathMapping}, etc. and have
 * the caller observe the new values.
 *
 * <p>This shared static surface supports the RefactorFirst design decision
 * "Refactor J-level logic into protected hooks on abstract bases — no
 * fork-and-drift". Java's single inheritance prohibits a single abstract
 * visitor that both {@code JavaIsoVisitor} and {@code KotlinIsoVisitor}
 * extend (they share {@code JavaVisitor} as ancestor but not the J-level
 * {@code JavaIsoVisitor} overrides), so composition is used here
 * instead of inheritance.
 */
@Slf4j
public final class DependencyVisitorLogic {

    private DependencyVisitorLogic() {}

    // ===================== Compilation Unit =====================

    /**
     * Called on entering a compilation unit. Records the package name and source path.
     *
     * @param state       the visitor state
     * @param packageName the package name
     * @param sourcePath  the source path
     */
    public static void enterCompilationUnit(DependencyVisitorState state, String packageName, String sourcePath) {
        state.setOwningPackageName(packageName);
        // sourcePath could be stored if needed for recordClassLocation
    }

    /**
     * Registers a package with the dependency collector.
     *
     * @param state       the visitor state
     * @param packageName the package name
     */
    public static void registerPackage(DependencyVisitorState state, String packageName) {
        state.getTypeProcessor().getDependencyCollector().registerPackage(packageName);
    }

    // ===================== Class Declaration =====================

    /**
     * Snapshot for nested class enter/leave.
     */
    public static class ClassSnapshot {
        final String previousOwnerFqn;
        final String currentOwnerFqn;

        ClassSnapshot(String previousOwnerFqn, String currentOwnerFqn) {
            this.previousOwnerFqn = previousOwnerFqn;
            this.currentOwnerFqn = currentOwnerFqn;
        }
    }

    /**
     * Called when entering a class declaration. Processes the class type, extends,
     * implements, annotations, type parameters, and record components.
     * Saves the previous owner FQN and sets the new one.
     *
     * @param state the visitor state
     * @param classDecl the class declaration node
     * @param processRecordComponents whether to process record component types (Java=true, Kotlin=false)
     * @param sourcePathResolver strategy for resolving inner class source paths
     * @return snapshot to be passed to {@link #leaveClassDeclaration}
     */
    public static ClassSnapshot enterClassDeclaration(
            DependencyVisitorState state,
            J.ClassDeclaration classDecl,
            boolean processRecordComponents,
            SourcePathResolver sourcePathResolver) {
        JavaType.FullyQualified type = classDecl.getType();
        if (type == null) {
            log.warn("ClassDeclaration has null type, skipping: {}", classDecl.getSimpleName());
            return null;
        }

        boolean isInner = state.getCursor().firstEnclosing(J.ClassDeclaration.class) != null;
        if (isInner) {
            String classFqn = type.getFullyQualifiedName();
            String sourcePath = sourcePathResolver.resolveSourcePath(state.getCursor());
            if (sourcePath != null) {
                log.debug("Inner Class FQN: {}, Source Path: {}", classFqn, sourcePath);
                recordClassLocation(state, classFqn, sourcePath);
            }
        }

        String owningFqn = type.getFullyQualifiedName();
        String previousOwnerFqn = state.getCurrentOwnerFqn();
        state.setCurrentOwnerFqn(owningFqn);

        try {
            state.getTypeProcessor().processType(owningFqn, type);

            TypeTree extendsTypeTree = classDecl.getExtends();
            if (extendsTypeTree != null) {
                state.getTypeProcessor().processType(owningFqn, extendsTypeTree.getType());
            }

            List<TypeTree> implementsList = classDecl.getImplements();
            if (implementsList != null) {
                for (TypeTree typeTree : implementsList) {
                    state.getTypeProcessor().processType(owningFqn, typeTree.getType());
                }
            }

            for (J.Annotation annotation : classDecl.getLeadingAnnotations()) {
                state.getTypeProcessor().processAnnotation(owningFqn, annotation, state.getCursor());
            }

            if (classDecl.getTypeParameters() != null) {
                for (J.TypeParameter typeParameter : classDecl.getTypeParameters()) {
                    state.getTypeProcessor().processTypeParameter(owningFqn, typeParameter, state.getCursor());
                }
            }

            // Handle record components (record header parameters)
            if (processRecordComponents && classDecl.getKind() == J.ClassDeclaration.Kind.Type.Record) {
                List<Statement> primaryConstructor = classDecl.getPrimaryConstructor();
                if (primaryConstructor != null) {
                    for (Statement stmt : primaryConstructor) {
                        if (stmt instanceof J.VariableDeclarations varDecl) {
                            TypeTree typeExpression = varDecl.getTypeExpression();
                            if (typeExpression != null) {
                                state.getTypeProcessor().processType(owningFqn, typeExpression.getType());
                            }
                            for (J.Annotation annotation : varDecl.getLeadingAnnotations()) {
                                state.getTypeProcessor().processAnnotation(owningFqn, annotation, state.getCursor());
                            }
                        }
                    }
                }
            }

            return new ClassSnapshot(previousOwnerFqn, owningFqn);
        } catch (Exception e) {
            // If anything fails, restore owner and rethrow
            state.setCurrentOwnerFqn(previousOwnerFqn);
            throw e;
        }
    }

    /**
     * Called when leaving a class declaration. Restores the previous owner FQN.
     *
     * @param state    the visitor state
     * @param snapshot the class snapshot
     */
    public static void leaveClassDeclaration(DependencyVisitorState state, ClassSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        state.setCurrentOwnerFqn(snapshot.previousOwnerFqn);
    }

    // ===================== Method Declaration =====================

    /**
     * Called when visiting a method declaration. Processes return type, annotations,
     * type parameters, throws clauses.
     *
     * @param state  the visitor state
     * @param method the method declaration
     */
    public static void handleMethodDeclaration(DependencyVisitorState state, J.MethodDeclaration method) {
        J.MethodDeclaration methodDeclaration = method;

        JavaType.Method methodType = methodDeclaration.getMethodType();
        if (null == methodType) {
            log.warn("MethodDeclaration has null methodType, skipping: {}", methodDeclaration.getSimpleName());
            return;
        }

        if (methodType.getDeclaringType() == null) {
            log.warn("MethodDeclaration has null declaring type, skipping: {}", methodDeclaration.getSimpleName());
            return;
        }

        String owner = methodType.getDeclaringType().getFullyQualifiedName();

        TypeTree returnTypeExpression = methodDeclaration.getReturnTypeExpression();
        if (returnTypeExpression != null) {
            JavaType returnType = returnTypeExpression.getType();
            if (!(returnType instanceof JavaType.Primitive)) {
                state.getTypeProcessor().processType(owner, returnType);
            }
        }

        for (J.Annotation leadingAnnotation : methodDeclaration.getLeadingAnnotations()) {
            state.getTypeProcessor().processAnnotation(owner, leadingAnnotation, state.getCursor());
        }

        if (null != methodDeclaration.getTypeParameters()) {
            for (J.TypeParameter typeParameter : methodDeclaration.getTypeParameters()) {
                state.getTypeProcessor().processTypeParameter(owner, typeParameter, state.getCursor());
            }
        }

        List<NameTree> throwz = methodDeclaration.getThrows();
        if (null != throwz && !throwz.isEmpty()) {
            for (NameTree thrown : throwz) {
                state.getTypeProcessor().processType(owner, thrown.getType());
            }
        }
    }

    // ===================== Variable Declarations =====================

    /**
     * Called when visiting variable declarations. Processes the type and annotations.
     * Falls back to UnattributedTypeFqnResolver when the type is not attributed.
     *
     * @param state         the visitor state
     * @param multiVariable the variable declarations
     */
    public static void handleVariableDeclarations(DependencyVisitorState state, J.VariableDeclarations multiVariable) {
        if (state.getCurrentOwnerFqn() == null) {
            return;
        }

        TypeTree typeTree = multiVariable.getTypeExpression();
        if (null == typeTree) {
            return;
        }

        JavaType javaType = typeTree.getType();

        state.getTypeProcessor().processAnnotations(state.getCurrentOwnerFqn(), state.getCursor());

        if (javaType instanceof JavaType.Primitive) {
            return;
        }

        if (javaType != null && !(javaType instanceof JavaType.Unknown)) {
            state.getTypeProcessor().processType(state.getCurrentOwnerFqn(), javaType);
        } else {
            // The parser could not attribute the type (typically because the
            // referenced class lives in a source file of the *other* language
            // that was not on this parser's classpath). Fall back to the
            // surface name resolved against the surrounding compilation unit's
            // package so cross language references still emit edges.
            // Pass cursor to enable import-based resolution.
            String resolvedFqn =
                    UnattributedTypeFqnResolver.resolve(typeTree, state.getOwningPackageName(), state.getCursor());
            if (resolvedFqn != null) {
                state.getTypeProcessor()
                        .getDependencyCollector()
                        .addClassDependency(state.getCurrentOwnerFqn(), resolvedFqn);
            }
        }
    }

    // ===================== Method Invocation =====================

    /**
     * Called when visiting a method invocation. Records the declaring type and type parameters.
     *
     * @param state  the visitor state
     * @param method the method invocation
     */
    public static void handleMethodInvocation(DependencyVisitorState state, J.MethodInvocation method) {
        if (state.getCurrentOwnerFqn() == null) {
            return;
        }

        JavaType.Method methodType = method.getMethodType();
        if (null != methodType && null != methodType.getDeclaringType()) {
            state.getTypeProcessor().processType(state.getCurrentOwnerFqn(), methodType.getDeclaringType());
        }

        if (null != method.getTypeParameters() && !method.getTypeParameters().isEmpty()) {
            for (Expression typeParameter : method.getTypeParameters()) {
                state.getTypeProcessor().processType(state.getCurrentOwnerFqn(), typeParameter.getType());
            }
        }
    }

    // ===================== New Class =====================

    /**
     * Called when visiting a new class instantiation. Records the instantiated type.
     *
     * @param state    the visitor state
     * @param newClass the new class expression
     */
    public static void handleNewClass(DependencyVisitorState state, J.NewClass newClass) {
        if (state.getCurrentOwnerFqn() != null) {
            state.getTypeProcessor().processType(state.getCurrentOwnerFqn(), newClass.getType());
        }
    }

    // ===================== Lambda =====================

    /**
     * Called when visiting a lambda expression. Records the lambda's type.
     *
     * @param state  the visitor state
     * @param lambda the lambda expression
     */
    public static void handleLambda(DependencyVisitorState state, J.Lambda lambda) {
        if (state.getCurrentOwnerFqn() != null && lambda.getType() != null) {
            state.getTypeProcessor().processType(state.getCurrentOwnerFqn(), lambda.getType());
        }
    }

    // ===================== InstanceOf =====================

    /**
     * Called when visiting an instanceof expression. Records the checked type.
     *
     * @param state      the visitor state
     * @param instanceOf the instanceof expression
     */
    public static void handleInstanceOf(DependencyVisitorState state, J.InstanceOf instanceOf) {
        if (state.getCurrentOwnerFqn() != null && instanceOf.getClazz() instanceof TypeTree) {
            state.getTypeProcessor()
                    .processType(state.getCurrentOwnerFqn(), ((TypeTree) instanceOf.getClazz()).getType());
        }
    }

    // ===================== Type Cast =====================

    /**
     * Called when visiting a type cast. Records the cast type.
     *
     * @param state    the visitor state
     * @param typeCast the type cast
     */
    public static void handleTypeCast(DependencyVisitorState state, J.TypeCast typeCast) {
        if (state.getCurrentOwnerFqn() != null && typeCast.getClazz() != null) {
            state.getTypeProcessor()
                    .processType(
                            state.getCurrentOwnerFqn(),
                            typeCast.getClazz().getTree().getType());
        }
    }

    // ===================== New Array =====================

    /**
     * Called when visiting a new array expression. Records the array element type.
     *
     * @param state    the visitor state
     * @param newArray the new array expression
     */
    public static void handleNewArray(DependencyVisitorState state, J.NewArray newArray) {
        if (state.getCurrentOwnerFqn() != null && newArray.getType() != null) {
            state.getTypeProcessor().processType(state.getCurrentOwnerFqn(), newArray.getType());
        }
    }

    // ===================== Member Reference =====================

    /**
     * Called when visiting a method/field reference. Records the declaring type.
     *
     * @param state      the visitor state
     * @param memberRef  the member reference
     */
    public static void handleMemberReference(DependencyVisitorState state, J.MemberReference memberRef) {
        if (state.getCurrentOwnerFqn() == null) {
            return;
        }

        if (memberRef.getType() != null) {
            state.getTypeProcessor().processType(state.getCurrentOwnerFqn(), memberRef.getType());

            if (memberRef.getType() instanceof JavaType.Method methodType && methodType.getDeclaringType() != null) {
                state.getTypeProcessor().processType(state.getCurrentOwnerFqn(), methodType.getDeclaringType());
            }
        }
    }

    // ===================== Class Location Recording =====================

    /**
     * Records a class's source file location. Handles the junit synthetic path branch.
     * For anonymous classes (FQN containing {@code <anonymous>}), the actual source file
     * path is used even in the junit branch, since synthetic paths derived from the
     * anonymous FQN are not meaningful.
     * <p>
     * For non-anonymous classes in the junit branch, we now also use the actual source
     * file name from the sourcePathUri rather than deriving a synthetic path from the
     * class FQN. This ensures that classes in files with different names (e.g.,
     * {@code GameSettings} in {@code Settings.kt}) map correctly.
     *
     * @param state         the visitor state
     * @param classFqn      the fully qualified class name
     * @param sourcePathUri the source path URI
     */
    public static void recordClassLocation(DependencyVisitorState state, String classFqn, String sourcePathUri) {
        boolean isAnonymous = isAnonymousFqn(classFqn);
        String repositoryRoot = state.getRepositoryRoot();
        String baseForCanonicalization = repositoryRoot.isEmpty() ? state.getRepositoryPath() : repositoryRoot;

        // The "junit-" heuristic detects synthetic source paths in JUnit test environments
        // where no explicit repositoryRoot is configured (falls back to repositoryPath).
        // Only apply it when repositoryRoot is empty (not explicitly configured by user).
        boolean isJUnitFallback = repositoryRoot.isEmpty() && baseForCanonicalization.contains("junit-");

        String canonicalPath;
        if (isJUnitFallback && !isAnonymous) {
            // For non-anonymous classes in junit tests: use actual file name from source URI
            // rather than synthetic path from class FQN. This handles cases where class name
            // != file name (e.g., GameSettings in Settings.kt).
            // sourcePathUri = "file:///real/path/to/SourceFile.kt"
            String fileName = extractFileNameFromUri(sourcePathUri);
            String packagePath = extractPackagePathFromFqn(classFqn);
            canonicalPath = packagePath + "/" + fileName;
        } else if (isJUnitFallback && isAnonymous) {
            // For anonymous classes in junit tests: construct path from package + actual file name
            // classFqn = "pkg.OuterClass.<anonymous>" or "pkg.<anonymous>"
            // sourcePathUri = "file:///real/path/to/SourceFile.kt"
            String fileName = extractFileNameFromUri(sourcePathUri);
            String packagePath = extractPackagePath(classFqn);
            canonicalPath = packagePath + "/" + fileName;
        } else {
            canonicalPath = canonicaliseUriStringForRepoLookup(baseForCanonicalization, sourcePathUri);
        }
        state.getClassToSourceFilePathMapping().put(classFqn, canonicalPath);
        state.getTypeProcessor().getDependencyCollector().recordClassLocation(classFqn, sourcePathUri);
    }

    /**
     * Returns true if the FQN represents an anonymous/synthetic class.
     * Mirrors {@link org.hjug.refactorfirst.report.HtmlReport#isAnonymousFqn(String)}.
     */
    private static boolean isAnonymousFqn(String classFqn) {
        return classFqn.contains("<anonymous>");
    }

    /**
     * Extracts the file name from a file:// URI.
     */
    private static String extractFileNameFromUri(String uri) {
        int lastSlash = uri.lastIndexOf('/');
        return lastSlash >= 0 ? uri.substring(lastSlash + 1) : uri;
    }

    /**
     * Extracts the package path from an anonymous class FQN.
     * E.g., "pkg.OuterClass.<anonymous>" -> "pkg/OuterClass"
     *       "pkg.<anonymous>" -> "pkg"
     */
    private static String extractPackagePath(String classFqn) {
        int anonIndex = classFqn.indexOf("<anonymous>");
        if (anonIndex > 0) {
            String beforeAnon = classFqn.substring(0, anonIndex - 1); // remove trailing "."
            return beforeAnon.replace(".", "/");
        }
        return classFqn.replace(".", "/");
    }

    /**
     * Extracts the package path from a regular (non-anonymous) class FQN.
     * E.g., "pkg.OuterClass" -> "pkg/OuterClass"
     *       "pkg.OuterClass$Inner" -> "pkg/OuterClass"
     *       "pkg.ClassName" -> "pkg"
     */
    private static String extractPackagePathFromFqn(String classFqn) {
        // For inner classes, get the outer class part
        String outerFqn = classFqn.contains("$") ? classFqn.substring(0, classFqn.indexOf('$')) : classFqn;
        // Remove the simple class name to get the package path
        int lastDot = outerFqn.lastIndexOf('.');
        if (lastDot > 0) {
            return outerFqn.substring(0, lastDot).replace(".", "/");
        }
        return ""; // default package
    }

    /**
     * Canonicalises a file:// URI against the repository path.
     *
     * @param repositoryPath the repository path
     * @param uriString      the URI string
     * @return the canonicalised path
     */
    public static String canonicaliseUriStringForRepoLookup(String repositoryPath, String uriString) {
        if (repositoryPath.startsWith("/") || repositoryPath.startsWith("\\")) {
            return uriString.replace("file://" + repositoryPath.replace("\\", "/") + "/", "");
        }
        return uriString.replace("file:///" + repositoryPath.replace("\\", "/") + "/", "");
    }
}
