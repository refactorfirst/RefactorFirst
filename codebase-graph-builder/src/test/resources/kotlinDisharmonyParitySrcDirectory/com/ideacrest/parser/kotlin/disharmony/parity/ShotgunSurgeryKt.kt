package com.ideacrest.parser.kotlin.disharmony.parity

/**
 * Kotlin disharmony parity fixture — Kotlin twin of `ShotgunSurgeryExample`.
 *
 * `performService` is called by 8 distinct methods in 8 distinct
 * caller classes (ShotgunCaller1Kt..ShotgunCaller8Kt), so:
 *   CM = 8 > SHORT_MEMORY_CAP(7)
 *   CC = 8 > MANY(7)
 */
class ShotgunSurgeryKt {

    private var result: String = ""

    fun performService(input: String): String {
        result = input
        return result
    }
}
