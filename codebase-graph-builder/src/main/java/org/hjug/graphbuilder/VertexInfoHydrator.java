package org.hjug.graphbuilder;

import java.util.HashMap;
import java.util.Map;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 * Populates {@link VertexInfo#getLocationOnDisk()} on class- and package-graph
 * vertices after the visitor walk, using the canonical repository-relative
 * class-to-source-file mapping maintained by the visitors.
 *
 * <p>Class vertices get the repository-relative path of the source file that
 * declares them; nested ({@code Outer$Inner}), anonymous/synthetic
 * ({@code Outer$1}/{@code Outer$}) and Kotlin ({@code pkg.Outer.<anonymous>})
 * vertices fall back to the mapping entry of their enclosing class. Package
 * vertices get the directory of the shortest mapped <em>direct-member</em>
 * source file (subpackage members do not contribute).
 * The parent namespace is already derived from the name at vertex creation
 * ({@link VertexInfo#forName(String)}) and is left untouched.
 */
public final class VertexInfoHydrator {

    private VertexInfoHydrator() {}

    /**
     * Hydrates every vertex of the supplied class and package graphs.
     *
     * @param classGraph  the class reference graph
     * @param packageGraph the package reference graph
     * @param classToSourceFilePathMapping repository-relative class FQN to source file path mapping
     */
    public static void hydrate(
            Graph<VertexInfo, DefaultWeightedEdge> classGraph,
            Graph<VertexInfo, DefaultWeightedEdge> packageGraph,
            Map<String, String> classToSourceFilePathMapping) {
        if (classGraph != null) {
            for (VertexInfo vertex : classGraph.vertexSet()) {
                vertex.setLocationOnDisk(resolveClassLocation(vertex.getName(), classToSourceFilePathMapping));
            }
        }
        if (packageGraph != null) {
            // index of package name -> shortest mapped direct-member path, built once
            // per hydration so every package vertex reuses it
            Map<String, String> packagePathIndex = buildPackagePathIndex(classToSourceFilePathMapping);
            for (VertexInfo vertex : packageGraph.vertexSet()) {
                vertex.setLocationOnDisk(resolvePackageLocation(vertex.getName(), packagePathIndex));
            }
        }
    }

    private static String resolveClassLocation(String fqn, Map<String, String> mapping) {
        String direct = mapping.get(fqn);
        if (direct != null) {
            return direct;
        }

        // nested / anonymous / synthetic classes (Outer$Inner, Outer$1, Outer$)
        // share the enclosing (outer) class's source file
        int dollarIndex = fqn.indexOf('$');
        if (dollarIndex > 0) {
            String enclosing = mapping.get(fqn.substring(0, dollarIndex));
            if (enclosing != null) {
                return enclosing;
            }
        }

        // Kotlin anonymous (pkg.Outer.<anonymous>) shares the enclosing class's source file
        int lastDot = fqn.lastIndexOf('.');
        if (lastDot > 0 && fqn.charAt(lastDot + 1) == '<') {
            return mapping.get(fqn.substring(0, lastDot));
        }

        return null;
    }

    /**
     * Builds the package name to shortest mapped direct-member path index used
     * for package-location resolution. Only <em>direct</em> members of a package
     * contribute: a nested {@code $} class name belongs to its outer class's
     * package, and a Kotlin {@code pkg.Outer.<anonymous>} member belongs to
     * {@code pkg}; a class in a subpackage never contributes to its ancestor
     * packages.
     *
     * @param mapping repository-relative class FQN to source file path mapping
     * @return package name to shortest direct-member path; ties resolve to the
     *         lexicographically first FQN for determinism
     */
    private static Map<String, String> buildPackagePathIndex(Map<String, String> mapping) {
        Map<String, String> index = new HashMap<>();
        for (String fqn : new java.util.TreeSet<>(mapping.keySet())) {
            String packageName = directMemberPackageName(fqn);
            if (packageName == null) {
                continue;
            }
            String candidate = mapping.get(fqn);
            String current = index.get(packageName);
            if (candidate != null && (current == null || candidate.length() < current.length())) {
                index.put(packageName, candidate);
            }
        }
        return index;
    }

    /**
     * Returns the name of the package of which the given class FQN is a direct
     * member, or {@code null} when it is a subpackage member or has no package.
     * The remainder after the package may contain {@code $} (nested classes)
     * or a trailing Kotlin {@code .<anonymous>} segment, but no further
     * package dots.
     */
    private static String directMemberPackageName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        if (lastDot < 0) {
            return null; // packageless class: no package vertex to locate
        }
        String packageName;
        String remainder;
        if (fqn.charAt(lastDot + 1) == '<') {
            // Kotlin anonymous (pkg.Outer.<anonymous>): member of the package
            // enclosing the anonymous's outer class
            int outerDot = fqn.lastIndexOf('.', lastDot - 1);
            if (outerDot < 0) {
                return null; // packageless outer class
            }
            packageName = fqn.substring(0, outerDot);
            remainder = fqn.substring(outerDot + 1, lastDot);
        } else {
            packageName = fqn.substring(0, lastDot);
            remainder = fqn.substring(lastDot + 1);
        }
        if (remainder.indexOf('.') >= 0) {
            return null; // lives in a subpackage
        }
        return packageName;
    }

    private static String resolvePackageLocation(String packageName, Map<String, String> packagePathIndex) {
        String memberPath = packagePathIndex.get(packageName);
        if (memberPath == null) {
            return null;
        }
        int lastSlash = memberPath.lastIndexOf('/');
        return lastSlash >= 0 ? memberPath.substring(0, lastSlash) : memberPath;
    }
}
