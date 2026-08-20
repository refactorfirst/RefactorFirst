package com.ideacrest.parser.kotlin.disharmony.parity

/**
 * Kotlin disharmony parity fixture — Kotlin twin of the Java `RefusedBequestExample`.
 *
 * Extends `BaseServiceKt` (giving it a `parentClass`) but never
 * uses the parent's protected members and never overrides anything.
 * With 8 methods (NOM > NOM_AVERAGE=7) and sufficient WMC, satisfies
 * the Refused Parent Bequest detector (Fig. 7.3):
 *   BOvR = 0 / 8 = 0 < 1/3
 *   NProtM = 15 > 5
 *   BUR = 0 / 15 < 1/3
 *   NOM=8 > 7 AND (AMW > 2 OR WMC > 14)
 *
 * Plain-text fixture, NOT compiled by the Maven build.
 */
class RefusedBequestKt : BaseServiceKt() {

    private var customData: String = ""
    private var customValue: Int = 0

    fun doCustomWork() {
        customData = "custom"
        customValue = 42
    }

    fun processData() {
        customData = "${customData}_processed"
    }

    fun getCustomData(): String = customData

    fun getCustomValue(): Int = customValue

    // CC=3 (if + else-if + else)
    fun evaluateStatus(value: Int): String {
        if (value > 100) {
            customData = "high:$value"
            return "high"
        } else if (value > 50) {
            customData = "mid:$value"
            return "mid"
        } else {
            customData = "low:$value"
            return "low"
        }
    }

    // CC=3 (for + if)
    fun countItems(values: IntArray): Int {
        var count = 0
        for (v in values) {
            if (v > 0) {
                count++
                customValue += v
            }
        }
        return count
    }

    // CC=5 (if + 3 else-if + else)
    fun processCustomData(input: String?, mode: Int): String {
        if (input == null) {
            return ""
        } else if (mode == 1) {
            customData = input.uppercase()
            return customData
        } else if (mode == 2) {
            customData = input.lowercase()
            customValue = input.length
            return customData
        } else if (mode == 3) {
            customValue = input.length
            customData = input.trim()
            return customData
        } else {
            return input
        }
    }

    // CC=2 (if + else)
    fun validateData(input: String?): Boolean {
        if (input == null || input.isEmpty()) {
            customData = "invalid"
            return false
        } else {
            customData = input
            return true
        }
    }
}
