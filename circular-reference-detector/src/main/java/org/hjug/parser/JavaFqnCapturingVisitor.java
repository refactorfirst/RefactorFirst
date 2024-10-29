package org.hjug.parser;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

/**
 * Captures Fully Qualified Names (FQN) of classes as they will be imported in import statements.
 * Output used to resolve Generic types.
 *
 * @param <P>
 */
@Getter
public class JavaFqnCapturingVisitor<P> extends JavaIsoVisitor<P> {

    // package -> name, FQN
    private final Map<String, Map<String, String>> fqns = new HashMap<>();

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
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
        return super.visitClassDeclaration(classDecl, p);
    }


    String getPackage(String fqn) {
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
    String getClassName(String fqn) {
        // handle no package
        if (!fqn.contains(".")) {
            return fqn;
        }

        int lastIndex = fqn.lastIndexOf(".");
        return fqn.substring(lastIndex + 1);
    }
}
