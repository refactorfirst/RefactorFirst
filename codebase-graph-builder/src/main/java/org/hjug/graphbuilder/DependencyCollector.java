package org.hjug.graphbuilder;

public interface DependencyCollector {

    /**
     * Records a dependency from one class to another
     *
     * @param fromClassFqn The fully qualified name of the class that depends on another
     * @param toClassFqn The fully qualified name of the class being depended upon
     */
    void addClassDependency(String fromClassFqn, String toClassFqn);

    /**
     * Records a dependency from one package to another
     *
     * @param fromPackageName The package that depends on another
     * @param toPackageName The package being depended upon
     */
    void addPackageDependency(String fromPackageName, String toPackageName);

    /**
     * Records the source file location for a class
     *
     * @param classFqn The fully qualified name of the class
     * @param sourceFilePath The path to the source file containing the class
     */
    void recordClassLocation(String classFqn, String sourceFilePath);

    /**
     * Registers a package as being part of the codebase
     *
     * @param packageName The package name to register
     */
    void registerPackage(String packageName);
}
