package org.hjug.graphbuilder;

/**
 * Rich graph vertex captured when graph dependency information is collected.
 *
 * <p>Replaces the bare class/package name String that previously served as the
 * graph vertex so the renderer can build a composed codebase map from the
 * graph image. Carries:
 *
 * <ul>
 *   <li>{@link #getName()} — the vertex name: the fully qualified class name
 *       (class graph) or the package name (package graph).</li>
 *   <li>{@link #getLocationOnDisk()} — the location on disk of the vertex
 *       relative to the repository root (source file path for a class;
 *       member-file directory for a package). Populated by hydration after the
 *       visitor walk; may be {@code null} when no location is known.</li>
 *   <li>{@link #getParentNamespace()} — the parent namespace: the parent
 *       package if this vertex is a package, or the enclosing construct
 *       (package or class) if the vertex is not a package and contains code.
 *       Derived from the name at creation time.</li>
 * </ul>
 *
 * <p>Identity is {@code name}-based ({@code equals}/{@code hashCode}) so
 * JGraphT vertex deduplication behaves exactly like the previous String
 * vertices and code can probe graphs with cheap {@link #forName(String)}
 * lookups. {@code toString()} returns the name so edge rendering
 * ({@code "(source : target)"}) is unchanged.
 */
public class VertexInfo {

    private final String name;
    private final String parentNamespace;
    private String locationOnDisk;

    /** Creates a vertex with the supplied identity and parent namespace, leaving its disk location unset. */
    private VertexInfo(String name, String parentNamespace) {
        this.name = name;
        this.parentNamespace = parentNamespace;
    }

    /**
     * Creates a vertex for the given class or package name with the parent
     * namespace derived from the name.
     *
     * @param name the class FQN or package name; must not be null or empty
     * @return a VertexInfo whose parent namespace is the enclosing package
     *         (for a top-level class or a package) or the enclosing class (for
     *         a nested, anonymous, or synthetic class); {@code null} when no
     *         enclosing construct can be derived
     * @throws NullPointerException if {@code name} is null
     * @throws IllegalArgumentException if {@code name} is empty
     */
    public static VertexInfo forName(String name) {
        if (name == null) {
            throw new NullPointerException("name must not be null");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        return new VertexInfo(name, deriveParentNamespace(name));
    }

    /**
     * Derives the enclosing construct of a vertex from its name.
     *
     * <p>Rules:
     * <ul>
     *   <li>Kotlin anonymous vertex ({@code pkg.Outer.<anonymous>}, trailing
     *       segment starts with {@code <}): the text before the last dot; a
     *       packageless literal {@code <anonymous>} has no parent.</li>
     *   <li>Nested/anonymous/synthetic class ({@code $} in the simple name):
     *       the enclosing class (text before the last {@code $}).</li>
     *   <li>Top-level class or package: the text before the last dot (the
     *       containing package / parent package); {@code null} when the name
     *       has no dot (default package).</li>
     * </ul>
     *
     * @param name the class FQN or package name
     * @return the parent namespace, or {@code null} when none can be derived
     */
    static String deriveParentNamespace(String name) {
        int lastDot = name.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? name.substring(lastDot + 1) : name;

        if (simpleName.startsWith("<")) {
            // Kotlin anonymous: "pkg.Outer.<anonymous>" -> "pkg.Outer";
            // packageless "<anonymous>" has no enclosing construct
            return lastDot > 0 ? name.substring(0, lastDot) : null;
        }

        int lastDollar = simpleName.lastIndexOf('$');
        if (lastDollar >= 0) {
            // nested / anonymous / synthetic: enclosing class before the last $
            String enclosingSimple = simpleName.substring(0, lastDollar);
            if (enclosingSimple.isEmpty()) {
                return null;
            }
            return lastDot >= 0 ? name.substring(0, lastDot + 1) + enclosingSimple : enclosingSimple;
        }

        // top-level class or package: containing / parent package
        return lastDot >= 0 ? name.substring(0, lastDot) : null;
    }

    /** Returns the vertex name (class FQN or package name). */
    public String getName() {
        return name;
    }

    /**
     * Returns the location on disk of this vertex relative to the repository
     * root, or {@code null} when not (yet) known.
     */
    public String getLocationOnDisk() {
        return locationOnDisk;
    }

    /** Sets the repository-relative location on disk (used during hydration). */
    void setLocationOnDisk(String locationOnDisk) {
        this.locationOnDisk = locationOnDisk;
    }

    /**
     * Returns the parent namespace: the parent package if this vertex is a
     * package, or the enclosing construct (package or class) if the vertex is
     * not a package and contains code; {@code null} when none can be derived.
     */
    public String getParentNamespace() {
        return parentNamespace;
    }

    /**
     * Compares vertex names; parent namespaces and hydrated disk locations do not affect identity.
     *
     * @param o the object to compare
     * @return whether the other object is a vertex with the same name
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VertexInfo)) {
            return false;
        }
        return name.equals(((VertexInfo) o).name);
    }

    /**
     * Returns a name-based hash that remains stable when the disk location is hydrated.
     *
     * @return the vertex name's hash code
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Returns the vertex name for graph edge rendering.
     *
     * @return the fully qualified class name or package name
     */
    @Override
    public String toString() {
        return name;
    }
}
