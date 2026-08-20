package org.hjug.graphbuilder.visitor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.openrewrite.Cursor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.kotlin.tree.K;

/**
 * Best-effort fallback for type references whose {@link JavaType} could not be
 * attributed by the OpenRewrite parser. This happens when a Java source file
 * references a Kotlin class (or vice versa) that lives outside the parser's
 * current parse batch and classpath.
 *
 * <p>The resolver extracts the surface text of the {@link TypeTree} (e.g.
 * {@code KotlinClass}, {@code SharedTarget}), unwraps Kotlin
 * {@link J.NullableType} markers, and combines the resulting simple name with
 * the current compilation unit's package to derive a fully qualified name.
 *
 * <p>If the compilation unit has an import matching the simple name, that
 * import's FQN is used instead of fabricating from the caller's package.
 * This handles cross-language references where the imported class is not
 * on the parser's classpath.
 *
 * <p>The result is suitable for {@code addClassDependency} calls so cross
 * language references still produce class-relationship edges between
 * same package classes.
 */
final class UnattributedTypeFqnResolver {

    private UnattributedTypeFqnResolver() {}

    /**
     * Attempts to derive a fully qualified class name from a {@link TypeTree}
     * whose {@link TypeTree#getType()} returned {@code null} or
     * {@link JavaType.Unknown}.
     *
     * @param typeTree the type tree to resolve; may be wrapped in a
     *                 {@link J.NullableType} which is unwrapped automatically
     * @param owningPackageName the package of the enclosing compilation unit;
     *                          may be empty for the default package
     * @return the resolved fully qualified name, or {@code null} when the FQN
     *         cannot be derived
     */
    static String resolve(TypeTree typeTree, String owningPackageName) {
        return resolve(typeTree, owningPackageName, null);
    }

    /**
     * Attempts to derive a fully qualified class name from a {@link TypeTree}
     * whose {@link TypeTree#getType()} returned {@code null} or
     * {@link JavaType.Unknown}, with access to the compilation unit's imports.
     *
     * @param typeTree the type tree to resolve; may be wrapped in a
     *                 {@link J.NullableType} which is unwrapped automatically
     * @param owningPackageName the package of the enclosing compilation unit;
     *                          may be empty for the default package
     * @param cursor the cursor for accessing the enclosing compilation unit;
     *               may be {@code null}
     * @return the resolved fully qualified name, or {@code null} when the FQN
     *         cannot be derived
     */
    static String resolve(TypeTree typeTree, String owningPackageName, Cursor cursor) {
        if (typeTree == null) {
            return null;
        }
        TypeTree unwrapped = unwrapNullable(typeTree);

        // For parameterized types (e.g., List<Target>), check type arguments
        if (unwrapped instanceof J.ParameterizedType pt) {
            String importFqn = resolveParameterizedType(pt, owningPackageName, cursor);
            if (importFqn != null) {
                return importFqn;
            }
        }

        String simpleName = extractSimpleName(unwrapped);
        if (simpleName == null || simpleName.isEmpty()) {
            return null;
        }
        if (!simpleName.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return null;
        }
        // Reject keyword-shaped fallback names (e.g., 'var', 'val') and
        // lowercase identifiers which are not valid Java/Kotlin type names.
        // Valid type names conventionally start with uppercase, '_', or '$'.
        char firstChar = simpleName.charAt(0);
        if (!(Character.isUpperCase(firstChar) || firstChar == '_' || firstChar == '$')) {
            return null;
        }

        // Check imports in compilation unit if cursor is available
        if (cursor != null) {
            String importFqn = findMatchingImport(cursor, simpleName);
            if (importFqn != null) {
                return importFqn;
            }
        }

        // Fallback to package-based fabrication
        return owningPackageName == null || owningPackageName.isEmpty()
                ? simpleName
                : owningPackageName + "." + simpleName;
    }

    /**
     * Resolves a parameterized type by checking its type arguments against imports.
     * Returns the FQN of the first type argument that matches an import.
     */
    private static String resolveParameterizedType(J.ParameterizedType pt, String owningPackageName, Cursor cursor) {
        TypeTree[] typeArguments = null;
        try {
            Method m = pt.getClass().getMethod("getTypeArguments");
            Object result = m.invoke(pt);
            if (result instanceof TypeTree[]) {
                typeArguments = (TypeTree[]) result;
            } else if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<TypeTree> list = (List<TypeTree>) result;
                typeArguments = list.toArray(new TypeTree[0]);
            }
        } catch (Exception e) {
            // Ignore and try field access
        }
        if (typeArguments == null) {
            try {
                Field f = pt.getClass().getDeclaredField("typeArguments");
                f.setAccessible(true);
                Object result = f.get(pt);
                if (result instanceof TypeTree[]) {
                    typeArguments = (TypeTree[]) result;
                } else if (result instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<TypeTree> list = (List<TypeTree>) result;
                    typeArguments = list.toArray(new TypeTree[0]);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        if (typeArguments == null || typeArguments.length == 0) {
            return null;
        }

        // Check each type argument for a matching import
        for (TypeTree arg : typeArguments) {
            TypeTree unwrappedArg = unwrapNullable(arg);
            String simpleName = extractSimpleName(unwrappedArg);
            if (simpleName == null || simpleName.isEmpty()) {
                continue;
            }
            if (!simpleName.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
                continue;
            }
            char firstChar = simpleName.charAt(0);
            if (!(Character.isUpperCase(firstChar) || firstChar == '_' || firstChar == '$')) {
                continue;
            }

            if (cursor != null) {
                String importFqn = findMatchingImport(cursor, simpleName);
                if (importFqn != null) {
                    return importFqn;
                }
            }
        }
        return null;
    }

    /**
     * Finds a non-static import matching the given simple name in the compilation unit.
     * Checks both Java and Kotlin compilation units.
     */
    private static String findMatchingImport(Cursor cursor, String simpleName) {
        // Try Java compilation unit first
        J.CompilationUnit jcu = cursor.firstEnclosing(J.CompilationUnit.class);
        if (jcu != null) {
            for (J.Import imp : jcu.getImports()) {
                if (!imp.isStatic()) {
                    String importFqn = imp.getQualid().toString();
                    String importSimpleName = importFqn.substring(importFqn.lastIndexOf('.') + 1);
                    if (importSimpleName.equals(simpleName)) {
                        return importFqn;
                    }
                }
            }
        }

        // Try Kotlin compilation unit
        try {
            K.CompilationUnit kcu = cursor.firstEnclosing(K.CompilationUnit.class);
            if (kcu != null) {
                for (K.Import imp : kcu.getImports()) {
                    if (!imp.isStatic()) {
                        String importFqn = imp.getQualid().toString();
                        String importSimpleName = importFqn.substring(importFqn.lastIndexOf('.') + 1);
                        if (importSimpleName.equals(simpleName)) {
                            return importFqn;
                        }
                    }
                }
            }
        } catch (NoClassDefFoundError | NoSuchMethodError e) {
            // Kotlin parser not available, ignore
        }

        return null;
    }

    private static TypeTree unwrapNullable(TypeTree typeTree) {
        TypeTree current = typeTree;
        while (current instanceof J.NullableType nt) {
            current = nt.getTypeTree();
        }
        return current;
    }

    private static String extractSimpleName(TypeTree typeTree) {
        if (typeTree instanceof J.Identifier id) {
            return id.getSimpleName();
        }
        if (typeTree instanceof J.FieldAccess fa) {
            return fa.getSimpleName();
        }
        if (typeTree instanceof J.ParameterizedType pt) {
            // For parameterized types, return the raw type name (e.g., "List" from "List<Target>")
            // Type arguments are handled separately in resolveParameterizedType
            if (pt.getClazz() instanceof J.Identifier id) {
                return id.getSimpleName();
            }
            if (pt.getClazz() instanceof J.FieldAccess fa) {
                return fa.getSimpleName();
            }
        }
        String rendered = typeTree.toString();
        if (rendered == null || rendered.isEmpty()) {
            return null;
        }
        return rendered.replaceAll("[<>().?\\s].*", "").replace("?", "");
    }
}
