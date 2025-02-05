package org.hjug.parser.visitor;

import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;

@Slf4j
public class JavaVisitor<P> extends JavaIsoVisitor<P> implements TypeProcessor {

    @Getter
    private final Set<String> packagesInCodebase = new HashSet<>();

    // used for looking up files where classes reside
    @Getter
    private final Map<String, String> classToSourceFileMapping = new HashMap<>();

    @Getter
    private Graph<String, DefaultWeightedEdge> classReferencesGraph =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

    @Getter
    private Graph<String, DefaultWeightedEdge> packageReferencesGraph =
            new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

    private final JavaClassDeclarationVisitor<P> javaClassDeclarationVisitor;

    public JavaVisitor() {
        javaClassDeclarationVisitor = new JavaClassDeclarationVisitor<>(classReferencesGraph, packageReferencesGraph);
    }

    public JavaVisitor(
            Graph<String, DefaultWeightedEdge> classReferencesGraph,
            Graph<String, DefaultWeightedEdge> packageReferencesGraph) {
        this.classReferencesGraph = classReferencesGraph;
        this.packageReferencesGraph = packageReferencesGraph;
        javaClassDeclarationVisitor = new JavaClassDeclarationVisitor<>(classReferencesGraph, packageReferencesGraph);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
        return javaClassDeclarationVisitor.visitClassDeclaration(classDecl, p);
    }

    // Map each class to its source file
    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, P p) {
        J.CompilationUnit compilationUnit = super.visitCompilationUnit(cu, p);

        packagesInCodebase.add(compilationUnit.getPackageDeclaration().getPackageName());

        for (J.ClassDeclaration aClass : compilationUnit.getClasses()) {
            classToSourceFileMapping.put(
                    aClass.getType().getFullyQualifiedName(),
                    compilationUnit.getSourcePath().toUri().toString());
        }

        return compilationUnit;
    }
}
