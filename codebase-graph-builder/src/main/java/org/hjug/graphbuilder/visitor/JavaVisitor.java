package org.hjug.graphbuilder.visitor;

import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.DependencyCollector;
import org.openrewrite.java.tree.*;

@Slf4j
public class JavaVisitor<P> extends BaseCodebaseVisitor<P> {

    @Getter
    private final Map<String, String> classToSourceFilePathMapping = new HashMap<>();

    private final JavaClassDeclarationVisitor<P> javaClassDeclarationVisitor;

    public JavaVisitor(DependencyCollector dependencyCollector) {
        super(dependencyCollector);
        javaClassDeclarationVisitor = new JavaClassDeclarationVisitor<>(dependencyCollector);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
        return javaClassDeclarationVisitor.visitClassDeclaration(classDecl, p);
    }

    // Map each class to its source file
    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, P p) {
        J.CompilationUnit compilationUnit = super.visitCompilationUnit(cu, p);

        J.Package packageDeclaration = compilationUnit.getPackageDeclaration();
        if (null == packageDeclaration) {
            return compilationUnit;
        }

        dependencyCollector.registerPackage(packageDeclaration.getPackageName());

        for (J.ClassDeclaration aClass : compilationUnit.getClasses()) {
            String classFqn = aClass.getType().getFullyQualifiedName();
            String sourcePath = compilationUnit.getSourcePath().toUri().toString();
            classToSourceFilePathMapping.put(classFqn, sourcePath);
            dependencyCollector.recordClassLocation(classFqn, sourcePath);
        }

        return compilationUnit;
    }

    @Override
    protected String getCurrentOwnerFqn() {
        return null;
    }
}
