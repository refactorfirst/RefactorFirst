package org.hjug.parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;

@Slf4j
public class JavaProjectParser {

    /**
     * Given a java source directory return a graph of class references
     *
     * @param srcDirectory
     * @return
     * @throws IOException
     */
    public Graph<String, DefaultWeightedEdge> getClassReferences(String srcDirectory) throws IOException {
        Graph<String, DefaultWeightedEdge> classReferencesGraph =
                new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        if (srcDirectory == null || srcDirectory.isEmpty()) {
            throw new IllegalArgumentException();
        } else {
            processWithOpenRewrite(srcDirectory, classReferencesGraph);
        }

        return classReferencesGraph;
    }

    private void processWithOpenRewrite(String srcDir, Graph<String, DefaultWeightedEdge> classReferencesGraph)
            throws IOException {
        File srcDirectory = new File(srcDir);

        org.openrewrite.java.JavaParser javaParser =
                JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        JavaVariableTypeVisitor<ExecutionContext> javaVariableTypeVisitor =
                new JavaVariableTypeVisitor<>(classReferencesGraph);

        try (Stream<Path> walk = Files.walk(Paths.get(srcDirectory.getAbsolutePath()))) {
            List<Path> list = walk.collect(Collectors.toList());
            javaParser
                    .parse(list, Paths.get(srcDirectory.getAbsolutePath()), ctx)
                    .forEach(cu -> javaVariableTypeVisitor.visit(cu, ctx));
        }

        //TODO: classReferencesGraph.removeAllVertices(); if vertex package not in codebase
    }
}
