package org.hjug.graphbuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hjug.graphbuilder.visitor.JavaMethodDeclarationVisitor;
import org.hjug.graphbuilder.visitor.JavaVariableTypeVisitor;
import org.hjug.graphbuilder.visitor.JavaVisitor;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

@Slf4j
public class JavaGraphBuilder {

    /**
     * Given a java source directory return a graph of class references
     *
     * @param srcDirectory
     * @return
     * @throws IOException
     */
    public Graph<String, DefaultWeightedEdge> getClassReferences(
            String srcDirectory, boolean excludeTests, String testSourceDirectory) throws IOException {
        Graph<String, DefaultWeightedEdge> classReferencesGraph;
        if (srcDirectory == null || srcDirectory.isEmpty()) {
            throw new IllegalArgumentException();
        } else {
            classReferencesGraph = processWithOpenRewrite(srcDirectory, excludeTests, testSourceDirectory)
                    .getClassReferencesGraph();
        }

        return classReferencesGraph;
    }

    private CodebaseGraphDTO processWithOpenRewrite(String srcDir, boolean excludeTests, String testSourceDirectory)
            throws IOException {
        File srcDirectory = new File(srcDir);

        JavaParser javaParser = JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        final Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        final Graph<String, DefaultWeightedEdge> packageReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        final JavaVisitor<ExecutionContext> javaVisitor =
                new JavaVisitor<>(classReferencesGraph, packageReferencesGraph);
        final JavaVariableTypeVisitor<ExecutionContext> javaVariableTypeVisitor =
                new JavaVariableTypeVisitor<>(classReferencesGraph, packageReferencesGraph);
        final JavaMethodDeclarationVisitor<ExecutionContext> javaMethodDeclarationVisitor =
                new JavaMethodDeclarationVisitor<>(classReferencesGraph, packageReferencesGraph);

        try (Stream<Path> pathStream = Files.walk(Paths.get(srcDirectory.getAbsolutePath()))) {
            List<Path> list;
            if (excludeTests) {
                list = pathStream
                        .filter(file -> !file.toString().contains(testSourceDirectory))
                        .collect(Collectors.toList());
            } else {
                list = pathStream.collect(Collectors.toList());
            }

            javaParser
                    .parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx)
                    .forEach(cu -> {
                        javaVisitor.visit(cu, ctx);
                        javaVariableTypeVisitor.visit(cu, ctx);
                        javaMethodDeclarationVisitor.visit(cu, ctx);
                    });
        }

        removeClassesNotInCodebase(javaVisitor.getPackagesInCodebase(), classReferencesGraph);

        return new CodebaseGraphDTO(
                classReferencesGraph, packageReferencesGraph, javaVisitor.getClassToSourceFilePathMapping());
    }

    // remove node if package not in codebase
    void removeClassesNotInCodebase(
            Set<String> packagesInCodebase, Graph<String, DefaultWeightedEdge> classReferencesGraph) {

        // collect nodes to remove
        Set<String> classesToRemove = new HashSet<>();
        for (String classFqn : classReferencesGraph.vertexSet()) {
            if (!packagesInCodebase.contains(getPackage(classFqn))) {
                classesToRemove.add(classFqn);
            }
        }

        classReferencesGraph.removeAllVertices(classesToRemove);
    }

    String getPackage(String fqn) {
        // handle no package
        if (!fqn.contains(".")) {
            return "";
        }

        int lastIndex = fqn.lastIndexOf(".");
        return fqn.substring(0, lastIndex);
    }
}
