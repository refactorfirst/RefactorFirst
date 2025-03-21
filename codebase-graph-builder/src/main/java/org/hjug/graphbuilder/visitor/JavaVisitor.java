package org.hjug.graphbuilder.visitor;

import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;

@Slf4j
public class JavaVisitor<P> extends JavaIsoVisitor<P> implements TypeProcessor {

    // used to keep track of what packages are in the codebase
    // used to remove the nodes that are not in the codebase
    @Getter
    private final Set<String> packagesInCodebase = new HashSet<>();

    // used for looking up files where classes reside
    @Getter
    private final Map<String, String> classToSourceFilePathMapping = new HashMap<>();

    @Getter
    private final Graph<String, DefaultWeightedEdge> classReferencesGraph;

    @Getter
    private final Graph<String, DefaultWeightedEdge> packageReferencesGraph;

    private final JavaClassDeclarationVisitor<P> javaClassDeclarationVisitor;

    public JavaVisitor(
            Graph<String, DefaultWeightedEdge> classReferencesGraph,
            Graph<String, DefaultWeightedEdge> packageReferencesGraph) {
        this.classReferencesGraph = classReferencesGraph;
        this.packageReferencesGraph = packageReferencesGraph;
        javaClassDeclarationVisitor = new JavaClassDeclarationVisitor<>(classReferencesGraph);
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

        packagesInCodebase.add(packageDeclaration.getPackageName());

        for (J.ClassDeclaration aClass : compilationUnit.getClasses()) {
            classToSourceFilePathMapping.put(
                    aClass.getType().getFullyQualifiedName(),
                    compilationUnit.getSourcePath().toUri().toString());
        }

        return compilationUnit;
    }
}
