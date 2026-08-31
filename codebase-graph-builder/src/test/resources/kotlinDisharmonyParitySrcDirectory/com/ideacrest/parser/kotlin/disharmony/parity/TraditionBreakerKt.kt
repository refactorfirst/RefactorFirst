package com.ideacrest.parser.kotlin.disharmony.parity

/**
 * Kotlin disharmony parity fixture — Kotlin twin of the Java `TraditionBreakerExample`.
 *
 * Overrides 3 of `BaseServiceKt`'s 10 methods and adds 9 new methods.
 * Detection criteria (Lanza & Marinescu Fig. 7.9):
 *
 *   Condition 1: Excessive interface increase.
 *     NAS = 12 - 3 = 9 >= NOM_AVERAGE(7); PNAS = 9/12 = 0.75 >= TWO_THIRDS(0.67)
 *
 *   Condition 2: Child substantial size and complexity.
 *     NOM = 12 >= NOM_HIGH(12); AMW > 2.0 OR WMC >= 47
 *
 *   Condition 3: Parent non-dumb (BaseServiceKt):
 *     AMW > 2.0 AND NOM > NOM_HIGH/2(6) AND WMC >= VERY_HIGH/2(23)
 *     BaseServiceKt: NOM=10, WMC=23, AMW=2.3
 */
class TraditionBreakerKt : BaseServiceKt() {

    private var feature1: String = ""
    private var feature2: Int = 0
    private var feature3: Boolean = false
    private var feature4: Double = 0.0
    private var feature5: String = ""

    override fun initialize() {
        serviceName = "TraditionBreaker"
    }

    override fun configure(config: String?) {
        configuration = "${config}_tb"
    }

    override fun start() {
        isActive = true
        feature1 = "started"
    }

    fun processFeature1(input: String?): String {
        if (input == null) {
            feature1 = ""
            return ""
        }
        feature1 = input.trim()
        return feature1
    }

    fun processFeature2(value: Int): Int {
        if (value > 0) {
            feature2 = value * 2
        } else {
            feature2 = 0
        }
        return feature2
    }

    fun processFeature3(key: String?): Boolean {
        if (key != null) {
            if (key.isNotEmpty()) {
                feature3 = true
                feature1 = key
            } else {
                feature3 = false
            }
        } else {
            feature3 = false
        }
        return feature3
    }

    fun processFeature4(amount: Double): Double {
        if (amount > 0.0) {
            feature4 = amount * 1.1
        } else {
            feature4 = 0.0
        }
        return feature4
    }

    fun processFeature5(a: String?, b: String?): String {
        if (a != null) {
            if (b != null) {
                feature5 = "$a:$b"
            } else {
                feature5 = a
            }
        } else {
            feature5 = b ?: ""
        }
        return feature5
    }

    fun processFeature6(x: Int, y: Int): Int {
        if (x > y) {
            feature2 = x - y
        } else if (x < y) {
            feature2 = y - x
        } else {
            feature2 = 0
        }
        return feature2
    }

    fun getFeatureSummary(): String {
        return "$feature1:$feature2:$feature3:$feature4:$feature5"
    }

    // CC=3 — brings NOM to 11
    fun processFeature7(count: Int, label: String): String {
        if (count > 0) {
            feature1 = "$label:$count"
        } else if (count < 0) {
            feature1 = "$label:negative"
        } else {
            feature1 = "$label:zero"
        }
        return feature1
    }

    // CC=3 — brings NOM to 12; total WMC sufficient for AMW > 2.0
    fun processFeature8(key: String?, flag: Boolean): Boolean {
        if (key == null) {
            feature3 = false
        } else if (flag) {
            feature3 = true
        } else {
            feature3 = false
        }
        return feature3
    }
}
