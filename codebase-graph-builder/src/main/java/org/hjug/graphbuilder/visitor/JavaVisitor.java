package org.hjug.graphbuilder.visitor;

import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.DependencyCollector;
import org.openrewrite.java.tree.*;

/**
 * BUG: Static method calls and definitions are not being captured, but were previously being captured.
 * Classes with static methods are also not being captured in the graph.
 * Will take this as a bug for now and address this issue ASAP.
 * @param <P>
 */
@Slf4j
public class JavaVisitor<P> extends BaseCodebaseVisitor<P> {

    @Getter
    private final Map<String, String> classToSourceFilePathMapping = new HashMap<>();

    private final JavaClassDeclarationVisitor<P> javaClassDeclarationVisitor;

    private String repositoryPath;

    public JavaVisitor(String repositoryPath, DependencyCollector dependencyCollector) {
        super(dependencyCollector);
        this.repositoryPath = repositoryPath;
        javaClassDeclarationVisitor = new JavaClassDeclarationVisitor<>(dependencyCollector);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
        // return javaClassDeclarationVisitor.visitClassDeclaration(classDecl, p);
        boolean isInner = getCursor().firstEnclosing(J.ClassDeclaration.class) != null;

        if (!isInner) {
            javaClassDeclarationVisitor.visitClassDeclaration(classDecl, p);
        } else {
            JavaType.FullyQualified type = classDecl.getType();
            J.ClassDeclaration innerClassDecl = getCursor().firstEnclosing(J.ClassDeclaration.class);
            if (type != null) {
                String classFqn = type.getFullyQualifiedName();
                J.CompilationUnit cu = getCursor().firstEnclosing(J.CompilationUnit.class);
                if (cu != null) {
                    String sourcePath = cu.getSourcePath().toUri().toString();
                    log.info("Inner Class FQN: {}, Source Path: {}", classFqn, sourcePath);
                    if (repositoryPath.contains("junit-")) {
                        String outerFqn =
                                classFqn.contains("$") ? classFqn.substring(0, classFqn.indexOf('$')) : classFqn;
                        classToSourceFilePathMapping.put(classFqn, outerFqn.replace(".", "/") + ".java");
                    } else {
                        classToSourceFilePathMapping.put(
                                classFqn, canonicaliseURIStringForRepoLookup(repositoryPath, sourcePath));
                    }
                    dependencyCollector.recordClassLocation(classFqn, sourcePath);
                    javaClassDeclarationVisitor.visitClassDeclaration(innerClassDecl, p);
                }
            }
        }

        return super.visitClassDeclaration(classDecl, p);
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
            log.info("Class FQN: {}, Source Path: {}", classFqn, sourcePath);

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
    protected String getCurrentOwnerFqn() {
        return null;
    }

    private String canonicaliseURIStringForRepoLookup(String repositoryPath, String uriString) {
        if (repositoryPath.startsWith("/") || repositoryPath.startsWith("\\")) {
            return uriString.replace("file://" + repositoryPath.replace("\\", "/") + "/", "");
        }

        return uriString.replace("file:///" + repositoryPath.replace("\\", "/") + "/", "");
    }
}
