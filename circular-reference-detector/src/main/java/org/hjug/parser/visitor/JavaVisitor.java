package org.hjug.parser.visitor;

import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;

@Slf4j
public class JavaVisitor<P> extends JavaIsoVisitor<P> {

    @Getter
    private final Set<String> packages = new HashSet<>();

    // used for looking up files where classes reside
    @Getter
    private final Map<String, String> classToSourceFileMapping = new HashMap<>();

    private JavaVariableTypeVisitor<P> javaVariableTypeVisitor;
    private JavaClassDeclarationVisitor<P> javaClassDeclarationVisitor;

    Graph<String, DefaultWeightedEdge> classReferencesGraph =
            new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

    public JavaVisitor() {}

    public JavaVisitor(Graph<String, DefaultWeightedEdge> classReferencesGraph) {
        this.classReferencesGraph = classReferencesGraph;
        javaVariableTypeVisitor = new JavaVariableTypeVisitor<>(classReferencesGraph);
        javaClassDeclarationVisitor = new JavaClassDeclarationVisitor<>(classReferencesGraph);
    }

    @Override
    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, P p) {
        return javaVariableTypeVisitor.visitVariableDeclarations(multiVariable, p);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
        return javaClassDeclarationVisitor.visitClassDeclaration(classDecl, p);
    }

    // Get imported classes to check declared generics
    @Override
    public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, P p) {
        J.CompilationUnit compilationUnit = super.visitCompilationUnit(cu, p);

        packages.add(compilationUnit.getPackageDeclaration().getPackageName());

        for (J.ClassDeclaration aClass : compilationUnit.getClasses()) {
            classToSourceFileMapping.put(
                    aClass.getType().getFullyQualifiedName(),
                    compilationUnit.getSourcePath().toUri().toString());
        }

        return compilationUnit;
    }
}
