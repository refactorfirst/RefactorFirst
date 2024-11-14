package org.hjug.parser;

import java.util.HashMap;
import java.util.Map;
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
public class JavaFqnCapturingVisitor<P> extends JavaIsoVisitor<P> implements FqnCapturingProcessor {

    // consider using ConcurrentHashMap to scale performance
    // package -> name, FQN
    private final Map<String, Map<String, String>> fqns = new HashMap<>();

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, P p) {
        return super.visitClassDeclaration(captureClassDeclarations(classDecl, fqns), p);
    }
}
