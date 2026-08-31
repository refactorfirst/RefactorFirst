package com.ideacrest.parser.kotlin.disharmony

/**
 * Kotlin disharmony fixture: declares ≥10 extension functions across ≥5
 * distinct foreign receiver types — matches the
 * `EXCESSIVE_EXTENSIONS` disharmony criterion
 * (≥10 functions && ≥5 receiver types).
 *
 * Plain-text Kotlin fixture for the OpenRewrite Kotlin parser;
 * never compiled by the Maven build.
 */
class ExtensionHost {

    fun String.repeatTwice(): String = this + this

    fun Int.doubled(): Int = this * 2

    fun List<Int>.sumAll(): Int = this.sum()

    fun String.shout(): String = this.uppercase() + "!"

    fun Int.isEven(): Boolean = this % 2 == 0

    fun Double.squared(): Double = this * this

    fun String.reversed(): String = this.reversed()

    fun Boolean.toggle(): Boolean = !this

    fun Long.incremented(): Long = this + 1L

    fun Float.halved(): Float = this / 2f

    fun Char.toHexString(): String = this.code.toString(16)

    fun Set<Int>.maxOrZero(): Int = if (isEmpty()) 0 else maxOrNull() ?: 0
}
