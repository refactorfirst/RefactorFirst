package com.ideacrest.parser.proptests

/**
 * Kotlin property-shapes fixture for validating that
 * {@code KotlinMetricsCollectingVisitor.visitProperty} overrides are dispatched
 * for class-level and top-level Kotlin property declarations.
 *
 * NOTE: Plain-text fixture for the OpenRewrite Kotlin parser; never compiled.
 */

// Top-level property (outside any class)
val topLevelGreeting: String = "hello"
var topLevelCounter: Int = 0

class PropertyHolder {

    // Class-level immutable property (val)
    val name: String = "default"

    // Class-level mutable nullable property (var)
    var count: Int? = null

    // Class-level property with inferred type
    val flag = true

    // Class-level custom-getter property
    val computed: String
        get() = "${name}-${count}"

    // Late-init mutable property
    lateinit var buffer: String
}

class PropertyUser {
    private val holder = PropertyHolder()

    fun describe(): String {
        val greeting = topLevelGreeting
        val n = holder.name
        return "$greeting-$n"
    }
}
