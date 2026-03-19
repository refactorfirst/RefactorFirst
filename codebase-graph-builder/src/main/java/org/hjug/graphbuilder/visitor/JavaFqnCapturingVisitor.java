package org.hjug.graphbuilder.visitor;

import java.util.*;
import lombok.Getter;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

/**
 * Captures Fully Qualified Names (FQN) of classes as they will be imported in import statements.
 * fqns map that is populated by this visitor is used to resolve Generic types.
 *
 * @param <P>
 */
@Getter
public class JavaFqnCapturingVisitor<P> extends JavaIsoVisitor<P> {

    // consider using ConcurrentHashMap to scale performance
    // package -> name, FQN
    private final Map<String, Map<String, String>> fqnMap = new HashMap<>();
    private final Set<String> fqns = new HashSet<>();

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
        captureClassDeclarations(classDecl, fqnMap);
        return classDecl;
    }

    J.ClassDeclaration captureClassDeclarations(J.ClassDeclaration classDecl, Map<String, Map<String, String>> fqnMap) {
        String fqn = classDecl.getType().getFullyQualifiedName();
        fqns.add(fqn);
        return classDecl;
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
