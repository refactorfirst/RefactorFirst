package org.hjug.graphbuilder.visitor;

import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.DependencyCollector;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavadocVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.Javadoc;

/**
 * BUG: Static method calls and definitions are not being captured, but were previously being captured.
 * Classes with static methods are also not being captured in the graph.
 * Will take this as a bug for now and address this issue ASAP.
 * @param <P>
 */
@Slf4j
public class JavaVisitor<P> extends JavaIsoVisitor<P> {

    private final DependencyCollector dependencyCollector;

    @Getter
    private final Map<String, String> classToSourceFilePathMapping = new HashMap<>();

    private final String repositoryPath;

    private final BaseTypeProcessor typeProcessor;

    private String currentOwnerFqn;

    public JavaVisitor(String repositoryPath, DependencyCollector dependencyCollector) {
        this.dependencyCollector = dependencyCollector;
        this.repositoryPath = repositoryPath;
        this.typeProcessor = new BaseTypeProcessor() {
            @Override
            protected DependencyCollector getDependencyCollector() {
                return dependencyCollector;
            }
        };
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

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
        JavaType.FullyQualified type = classDecl.getType();
        if (type == null) {
            log.warn("ClassDeclaration has null type, skipping: {}", classDecl.getSimpleName());
            return super.visitClassDeclaration(classDecl, p);
        }

        boolean isInner = getCursor().firstEnclosing(J.ClassDeclaration.class) != null;
        if (isInner) {
            J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
            if (cu != null) {
                String classFqn = type.getFullyQualifiedName();
                String sourcePath = cu.getSourcePath().toUri().toString();
                log.debug("Inner Class FQN: {}, Source Path: {}", classFqn, sourcePath);
                if (repositoryPath.contains("junit-")) {
                    String outerFqn = classFqn.contains("$") ? classFqn.substring(0, classFqn.indexOf('$')) : classFqn;
                    classToSourceFilePathMapping.put(classFqn, outerFqn.replace(".", "/") + ".java");
                } else {
                    classToSourceFilePathMapping.put(
                            classFqn, canonicaliseURIStringForRepoLookup(repositoryPath, sourcePath));
                }
                dependencyCollector.recordClassLocation(classFqn, sourcePath);
            }
        }

        String owningFqn = type.getFullyQualifiedName();
        String previousOwner = currentOwnerFqn;
        currentOwnerFqn = owningFqn;

        try {
            typeProcessor.processType(owningFqn, type);

            TypeTree extendsTypeTree = classDecl.getExtends();
            if (extendsTypeTree != null) {
                typeProcessor.processType(owningFqn, extendsTypeTree.getType());
            }

            List<TypeTree> implementsList = classDecl.getImplements();
            if (implementsList != null) {
                for (TypeTree typeTree : implementsList) {
                    typeProcessor.processType(owningFqn, typeTree.getType());
                }
            }

            for (J.Annotation annotation : classDecl.getLeadingAnnotations()) {
                typeProcessor.processAnnotation(owningFqn, annotation, getCursor());
            }

            if (classDecl.getTypeParameters() != null) {
                for (J.TypeParameter typeParameter : classDecl.getTypeParameters()) {
                    typeProcessor.processTypeParameter(owningFqn, typeParameter, getCursor());
                }
            }

            return super.visitClassDeclaration(classDecl, p);
        } finally {
            currentOwnerFqn = previousOwner;
        }
    }

    // Map each class to its source file
    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, P p) {

        J.Package packageDeclaration = compilationUnit.getPackageDeclaration();
        if (null == packageDeclaration) {
            return compilationUnit;
        }

        dependencyCollector.registerPackage(packageDeclaration.getPackageName());

        for (J.ClassDeclaration aClass : compilationUnit.getClasses()) {
            String classFqn = aClass.getType().getFullyQualifiedName();
            String sourcePath = compilationUnit.getSourcePath().toUri().toString();
            // looking for com.tonikelope.megabasterd.MegaProxyServer$Handler
            log.debug("Class FQN: {}, Source Path: {}", classFqn, sourcePath);

            // check for junit Temp directory being used as repo (for unit tests)
            if (repositoryPath.contains("junit-")) {
                classToSourceFilePathMapping.put(classFqn, classFqn.replace(".", "/") + ".java");
            } else {
                classToSourceFilePathMapping.put(
                        classFqn, canonicaliseURIStringForRepoLookup(repositoryPath, sourcePath));
            }
            dependencyCollector.recordClassLocation(classFqn, sourcePath);
        }

        return super.visitCompilationUnit(compilationUnit, p);
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, P p) {
        J.MethodInvocation methodInvocation = super.visitMethodInvocation(method, p);
        if (currentOwnerFqn == null) {
            return methodInvocation;
        }

        JavaType.Method methodType = methodInvocation.getMethodType();
        if (null != methodType && null != methodType.getDeclaringType()) {
            typeProcessor.processType(currentOwnerFqn, methodType.getDeclaringType());
        }

        if (null != methodInvocation.getTypeParameters()
                && !methodInvocation.getTypeParameters().isEmpty()) {
            for (Expression typeParameter : methodInvocation.getTypeParameters()) {
                typeProcessor.processType(currentOwnerFqn, typeParameter.getType());
            }
        }

        return methodInvocation;
    }

    @Override
    public J.NewClass visitNewClass(J.NewClass newClass, P p) {
        J.NewClass result = super.visitNewClass(newClass, p);
        if (currentOwnerFqn != null) {
            typeProcessor.processType(currentOwnerFqn, newClass.getType());
        }
        return result;
    }

    @Override
    public J.Lambda visitLambda(J.Lambda lambda, P p) {
        if (currentOwnerFqn != null && lambda.getType() != null) {
            typeProcessor.processType(currentOwnerFqn, lambda.getType());
        }

        // Recursively visit the lambda body to capture method invocations and type references
        // The super.visitLambda call will traverse into the lambda's body and parameters
        return super.visitLambda(lambda, p);
    }

    @Override
    public J.If visitIf(J.If iff, P p) {
        return super.visitIf(iff, p);
    }

    @Override
    public J.ForLoop visitForLoop(J.ForLoop forLoop, P p) {
        return super.visitForLoop(forLoop, p);
    }

    @Override
    public J.ForEachLoop visitForEachLoop(J.ForEachLoop forEachLoop, P p) {
        return super.visitForEachLoop(forEachLoop, p);
    }

    @Override
    public J.WhileLoop visitWhileLoop(J.WhileLoop whileLoop, P p) {
        return super.visitWhileLoop(whileLoop, p);
    }

    @Override
    public J.DoWhileLoop visitDoWhileLoop(J.DoWhileLoop doWhileLoop, P p) {
        return super.visitDoWhileLoop(doWhileLoop, p);
    }

    @Override
    public J.Switch visitSwitch(J.Switch switchStatement, P p) {
        return super.visitSwitch(switchStatement, p);
    }

    @Override
    public J.Try visitTry(J.Try tryStatement, P p) {
        return super.visitTry(tryStatement, p);
    }

    @Override
    public J.InstanceOf visitInstanceOf(J.InstanceOf instanceOf, P p) {
        J.InstanceOf result = super.visitInstanceOf(instanceOf, p);
        if (currentOwnerFqn != null && instanceOf.getClazz() != null && instanceOf.getClazz() instanceof TypeTree) {
            typeProcessor.processType(currentOwnerFqn, ((TypeTree) instanceOf.getClazz()).getType());
        }
        return result;
    }

    @Override
    public J.TypeCast visitTypeCast(J.TypeCast typeCast, P p) {
        J.TypeCast result = super.visitTypeCast(typeCast, p);
        if (currentOwnerFqn != null && typeCast.getClazz() != null) {
            typeProcessor.processType(
                    currentOwnerFqn, typeCast.getClazz().getTree().getType());
        }
        return result;
    }

    @Override
    public J.MemberReference visitMemberReference(J.MemberReference memberRef, P p) {
        J.MemberReference result = super.visitMemberReference(memberRef, p);
        if (currentOwnerFqn != null && memberRef.getType() != null) {
            typeProcessor.processType(currentOwnerFqn, memberRef.getType());
        }
        return result;
    }

    @Override
    public J.NewArray visitNewArray(J.NewArray newArray, P p) {
        J.NewArray result = super.visitNewArray(newArray, p);
        if (currentOwnerFqn != null && newArray.getType() != null) {
            typeProcessor.processType(currentOwnerFqn, newArray.getType());
        }
        return result;
    }

    @Override
    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, P p) {
        J.VariableDeclarations variableDeclarations = super.visitVariableDeclarations(multiVariable, p);

        if (currentOwnerFqn == null) {
            return variableDeclarations;
        }

        TypeTree typeTree = variableDeclarations.getTypeExpression();
        if (null == typeTree) {
            return variableDeclarations;
        }

        JavaType javaType = typeTree.getType();

        typeProcessor.processAnnotations(currentOwnerFqn, getCursor());

        if (javaType instanceof JavaType.Primitive) {
            return variableDeclarations;
        }

        typeProcessor.processType(currentOwnerFqn, javaType);

        return variableDeclarations;
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, P p) {
        J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(method, p);

        JavaType.Method methodType = methodDeclaration.getMethodType();
        if (null == methodType) {
            log.warn("MethodDeclaration has null methodType, skipping: {}", methodDeclaration.getSimpleName());
            return methodDeclaration;
        }

        if (methodType.getDeclaringType() == null) {
            log.warn("MethodDeclaration has null declaring type, skipping: {}", methodDeclaration.getSimpleName());
            return methodDeclaration;
        }

        String owner = methodType.getDeclaringType().getFullyQualifiedName();

        TypeTree returnTypeExpression = methodDeclaration.getReturnTypeExpression();
        if (returnTypeExpression != null) {
            JavaType returnType = returnTypeExpression.getType();

            if (!(returnType instanceof JavaType.Primitive)) {
                typeProcessor.processType(owner, returnType);
            }
        }

        for (J.Annotation leadingAnnotation : methodDeclaration.getLeadingAnnotations()) {
            typeProcessor.processAnnotation(owner, leadingAnnotation, getCursor());
        }

        if (null != methodDeclaration.getTypeParameters()) {
            for (J.TypeParameter typeParameter : methodDeclaration.getTypeParameters()) {
                typeProcessor.processTypeParameter(owner, typeParameter, getCursor());
            }
        }

        List<NameTree> throwz = methodDeclaration.getThrows();
        if (null != throwz && !throwz.isEmpty()) {
            for (NameTree thrown : throwz) {
                typeProcessor.processType(owner, thrown.getType());
            }
        }

        return methodDeclaration;
    }

    private String canonicaliseURIStringForRepoLookup(String repositoryPath, String uriString) {
        if (repositoryPath.startsWith("/") || repositoryPath.startsWith("\\")) {
            return uriString.replace("file://" + repositoryPath.replace("\\", "/") + "/", "");
        }

        return uriString.replace("file:///" + repositoryPath.replace("\\", "/") + "/", "");
    }
}
