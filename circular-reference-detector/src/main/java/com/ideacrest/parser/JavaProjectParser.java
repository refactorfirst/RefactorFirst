package com.ideacrest.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;

public class JavaProjectParser {

	/**
	 * Given a java source directory return a graph of class references
	 * @param srcDirectory
	 * @return
	 * @throws IOException
	 */
	public Graph<String, DefaultEdge> getClassReferences(String srcDirectory) throws IOException {
		Graph<String, DefaultEdge> classReferencesGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
		if (srcDirectory == null || srcDirectory.isEmpty()) {
			throw new IllegalArgumentException();
		} else {
			List<String> classNames = getClassNames(srcDirectory);
			try (Stream<Path> filesStream = Files.walk(Paths.get(srcDirectory))) {
				filesStream				
				.filter(path -> path.getFileName().toString().endsWith(".java"))
				.forEach(path ->{
					Set<String> instanceVarTypes = getInstanceVarTypes(classNames, path.toFile());
					if(!instanceVarTypes.isEmpty()) {						
						String className = getClassName(path.getFileName().toString());
						classReferencesGraph.addVertex(className);
						instanceVarTypes.forEach(classReferencesGraph::addVertex);
						instanceVarTypes.forEach( var -> classReferencesGraph.addEdge(className, var));
					}
				});
			}
			catch(FileNotFoundException e) {
				e.printStackTrace();
			}
			
		}
		return classReferencesGraph;
	}

	/**
	 * Get instance variables types of a java source file using java parser
	 * @param classNamesToFilterBy - only add instance variables which have these class names as type
	 * @param file
	 * @return
	 */
	private Set<String> getInstanceVarTypes(List<String> classNamesToFilterBy, File javaSrcFile) {
		CompilationUnit compilationUnit;
		try {
			compilationUnit = StaticJavaParser.parse(javaSrcFile);		
			return compilationUnit.findAll(FieldDeclaration.class)
					.stream()
					.map(f -> f.getVariables().get(0).getType())
					.filter(v -> !v.isPrimitiveType())
					.map( Object::toString)
					.filter(classNamesToFilterBy::contains)
					.collect(Collectors.toSet());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return new HashSet<>();
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
