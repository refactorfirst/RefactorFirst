package org.hjug.parser;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openrewrite.java.tree.J;

public interface FqnCapturingVisitor {

    default J.ClassDeclaration captureClassDeclarations(
            J.ClassDeclaration classDecl, Map<String, Map<String, String>> fqns) {
        // get class fqn (including "$")
        String fqn = classDecl.getType().getFullyQualifiedName();

        String currentPackage = getPackage(fqn);
        String className = getClassName(fqn);
        Map<String, String> classesInPackage = fqns.getOrDefault(currentPackage, new HashMap<>());

        if (className.contains("$")) {
            String normalizedClassName = className.replace('$', '.');
            List<String> parts = Arrays.asList(normalizedClassName.split("\\."));
            for (int i = 0; i < parts.size(); i++) {
                String key = String.join(".", parts.subList(i, parts.size()));
                classesInPackage.put(key, currentPackage + "." + normalizedClassName);
            }
        } else {
            classesInPackage.put(className, fqn);
        }

        fqns.put(currentPackage, classesInPackage);
        return classDecl;
    }

    default String getPackage(String fqn) {
        // handle no package
        if (!fqn.contains(".")) {
            return "";
        }

        int lastIndex = fqn.lastIndexOf(".");
        return fqn.substring(0, lastIndex);
    }

    /**
     *
     * @param fqn
     * @return Class name (including "$") after last period in FQN
     */
    default String getClassName(String fqn) {
        // handle no package
        if (!fqn.contains(".")) {
            return fqn;
        }

        int lastIndex = fqn.lastIndexOf(".");
        return fqn.substring(lastIndex + 1);
    }
}
