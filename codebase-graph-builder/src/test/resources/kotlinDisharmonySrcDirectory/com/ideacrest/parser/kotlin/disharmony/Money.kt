package com.ideacrest.parser.kotlin.disharmony

/**
 * Kotlin disharmony fixture: a Kotlin `data class` that ALSO declares a
 * non-accessor method — matches the DATA_CLASS_WITH_LOGIC disharmony
 * criterion (`isDataClass && hasExplicitLogic`).
 *
 * Plain-text Kotlin fixture for the OpenRewrite Kotlin parser; never
 * compiled by the Maven build.
 */
data class Money(val amount: Int, val currency: String) {

    /**
     * Non-accessor method on a data class. Its body has branching
     * logic (≥2 cyclomatic complexity), which trips the
     * `hasExplicitLogic` flag set in
     * `GraphMetricsCollector.computeKotlinDerivedMetrics`.
     */
    fun add(other: Money): Money {
        if (other.currency != currency) {
            throw IllegalArgumentException("currency mismatch: $currency vs ${other.currency}")
        }
        return Money(amount + other.amount, currency)
    }

    fun subtract(other: Money): Money {
        if (other.currency != currency) {
            throw IllegalArgumentException("currency mismatch: $currency vs ${other.currency}")
        }
        return Money(amount - other.amount, currency)
    }
}
