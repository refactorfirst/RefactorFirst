package com.ideacrest.parser.kotlin.disharmony

/**
 * Kotlin disharmony fixture: a sealed class `Shape` with 12 subtypes — matches
 * the LARGE_SEALED_HIERARCHY disharmony criterion (sealed type with
 * ≥12 permitted subtypes in the codebase).
 *
 * Plain-text Kotlin fixture for the OpenRewrite Kotlin parser; never
 * compiled by the Maven build.
 */
sealed class Shape {
    data class Circle(val radius: Double) : Shape()
    data class Square(val side: Double) : Shape()
    data class Triangle(val a: Double, val b: Double, val c: Double) : Shape()
    data class Rectangle(val w: Double, val h: Double) : Shape()
    data class Pentagon(val side: Double) : Shape()
    data class Hexagon(val side: Double) : Shape()
    data class Heptagon(val side: Double) : Shape()
    data class Octagon(val side: Double) : Shape()
    data class Rhombus(val side: Double) : Shape()
    data class Trapezoid(val a: Double, val b: Double, val h: Double) : Shape()
    data class Ellipse(val a: Double, val b: Double) : Shape()
    object NothingShape : Shape()
}
