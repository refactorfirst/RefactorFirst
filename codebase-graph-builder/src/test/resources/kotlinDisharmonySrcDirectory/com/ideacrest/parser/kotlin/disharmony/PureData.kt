package com.ideacrest.parser.kotlin.disharmony

/**
 * Kotlin disharmony fixture CONTROL: a Kotlin `data class` that does NOT declare
 * any explicit logic. Used to verify the `hasExplicitLogic`
 * detection does NOT flag pure data classes.
 *
 * Plain-text Kotlin fixture for the OpenRewrite Kotlin parser; never
 * compiled by the Maven build.
 */
data class PureData(val a: Int, val b: String)
