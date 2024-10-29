package org.hjug.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import java.io.File;
import java.io.FileNotFoundException;
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
            List<String> classNames = getClassNames(srcDirectory);
            try (Stream<Path> filesStream = Files.walk(Paths.get(srcDirectory))) {
                filesStream
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .forEach(path -> {
                            log.info("Parsing {}", path);
                            List<String> types = getInstanceVarTypes(classNames, path.toFile());
                            types.addAll(getMethodArgumentTypes(classNames, path.toFile()));
                            if (!types.isEmpty()) {
                                String className =
                                        getClassName(path.getFileName().toString());
                                classReferencesGraph.addVertex(className);
                                types.forEach(classReferencesGraph::addVertex);
                                types.forEach(type -> {
                                    if (!classReferencesGraph.containsEdge(className, type)) {
                                        classReferencesGraph.addEdge(className, type);
                                    } else {
                                        DefaultWeightedEdge edge = classReferencesGraph.getEdge(className, type);
                                        classReferencesGraph.setEdgeWeight(
                                                edge, classReferencesGraph.getEdgeWeight(edge) + 1);
                                    }
                                });
                            }
                        });
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        return classReferencesGraph;
    }

    List<String> getClassDeclarations(Path javaSrcFile, Path srcDirectory) {
        org.openrewrite.java.JavaParser javaParser =
                JavaParser.fromJavaVersion().build();
        ExecutionContext ctx = new InMemoryExecutionContext(Throwable::printStackTrace);

        CustomVisitor customVisitor = new CustomVisitor();

        List<String> fqdns = new ArrayList<>();

        javaParser.parse(List.of(javaSrcFile), srcDirectory, ctx).forEach(cu -> customVisitor.visit(cu, ctx));

        return fqdns;
    }

    /**
     * Get instance variables types of a java source file using java parser
     * @param classNamesToFilterBy - only add instance variable types which have these class names as type
     * @param file
     * @return
     */
    private List<String> getInstanceVarTypes(List<String> classNamesToFilterBy, File javaSrcFile) {
        CompilationUnit compilationUnit;
        try {
            compilationUnit = StaticJavaParser.parse(javaSrcFile);

            return compilationUnit.findAll(FieldDeclaration.class).stream()
                    .map(f -> f.getVariables().get(0).getType())
                    .filter(v -> !v.isPrimitiveType())
                    .map(Object::toString)
                    .filter(classNamesToFilterBy::contains)
                    .collect(Collectors.toList());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    /**
     * Get parameter types of methods declared in a java source file using java parser
     * @param classNamesToFilterBy - only add types which have these class names as type
     * @param file
     * @return
     */
    private List<String> getMethodArgumentTypes(List<String> classNamesToFilterBy, File javaSrcFile) {
        CompilationUnit compilationUnit;
        try {
            compilationUnit = StaticJavaParser.parse(javaSrcFile);
            return compilationUnit.findAll(MethodDeclaration.class).stream()
                    .flatMap(f -> f.getParameters().stream()
                            .map(Parameter::getType)
                            .filter(type -> !type.isPrimitiveType())
                            .collect(Collectors.toList())
                            .stream())
                    .map(Object::toString)
                    .filter(classNamesToFilterBy::contains)
                    .collect(Collectors.toList());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    /**
     * Get all java classes in a source directory
     *
     * @param srcDirectory
     * @return
     * @throws IOException
     */
    private List<String> getClassNames(String srcDirectory) throws IOException {
        try (Stream<Path> filesStream = Files.walk(Paths.get(srcDirectory))) {
            return filesStream
                    .map(path -> path.getFileName().toString())
                    .filter(fileName -> fileName.endsWith(".java"))
                    .map(this::getClassName)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Extract class name from java file name
     * Example : MyJavaClass.java becomes MyJavaClass
     *
     * @param javaFileName
     * @return
     */
    private String getClassName(String javaFileName) {
        return javaFileName.substring(0, javaFileName.indexOf('.'));
    }
}
