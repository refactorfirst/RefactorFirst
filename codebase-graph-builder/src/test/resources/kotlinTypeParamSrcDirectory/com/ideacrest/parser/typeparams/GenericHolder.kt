package com.ideacrest.parser.typeparams

/**
 * Kotlin type-parameter-bounds fixture: exercises every Kotlin site that constructs a
 * generic type parameter whose bound references another class in the same
 * parse batch.
 *
 * Each shape (class, method, property) is expected to produce a graph
 * dependency edge `GenericHolder -> MetaClassA`. The top-level
 * `typealias MetaList = List<MetaClassA>` is parsed as `K.TypeAlias` but
 * has no class owner, so it is intentionally a no-op for graph-edge
 * creation (it just proves the visitor doesn't crash on it).
 *
 * NOTE: a class-scoped `typealias` (declared inside a class body) is NOT
 * supported by the OpenRewrite Kotlin parser — the enclosing class
 * becomes a `J.Unknown` and disappears entirely from the AST. Such a
 * fixture would never produce a class vertex, so it is intentionally
 * omitted here.
 */
class GenericHolder<T : MetaClassA> {

    /**
     * Generic method whose type parameter bound is `MetaClassA`. The Kotlin
     * parser wraps the underlying `J.MethodDeclaration` in
     * `K.MethodDeclaration`, whose `getTypeConstraints().getConstraints()`
     * surface the type-parameter bound we extract as a dependency edge.
     */
    fun <U : MetaClassA> process(item: U): Int = 0

    /**
     * Generic method whose type-parameter bound is `MetaClassA`.
     * Surfaced via `K.MethodDeclaration.getTypeConstraints()` — same code
     * path as the method above (kept here so the parsing of generic
     * methods with nullable upper bounds is exercised too).
     */
    fun <V : MetaClassA> wrapped(): V? = null

    /**
     * Class-level property whose declared type is `MetaClassA`. Surfaced
     * via `K.Property` — extracted as a graph dependency edge through
     * `KotlinDependencyVisitor.visitProperty`.
     */
    val bound: MetaClassA = MetaClassA()
}

/**
 * Top-level generic type alias. The OpenRewrite Kotlin parser surfaces
 * type aliases via `K.TypeAlias`; the initializer (`List<MetaClassA>`)
 * is walked for type dependencies via
 * `K.TypeAlias.getPadding().getInitializer()`.
 *
 * Note: top-level — `currentOwnerFqn` is null when this is visited. The
 * visitor's `visitTypeAlias` initialiser/parameter extraction only
 * records edges when there's an owner, so top-level aliases are
 * no-ops for graph edges but should not break the parser/visitor.
 */
typealias MetaList = List<MetaClassA>
