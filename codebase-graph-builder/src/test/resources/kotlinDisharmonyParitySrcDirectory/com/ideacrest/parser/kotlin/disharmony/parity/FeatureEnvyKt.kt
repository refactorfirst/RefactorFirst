package com.ideacrest.parser.kotlin.disharmony.parity

import com.ideacrest.parser.kotlin.disharmony.parity.external.CustomerService

/**
 * Kotlin disharmony parity fixture — Kotlin twin of the Java `FeatureEnvyExample`.
 *
 * `methodWithFeatureEnvy` accesses all 6 public fields of the foreign
 * `CustomerService` class (ATFD=6 > FEW=5), has no own-attribute
 * accesses (LAA=0 < 1/3), and is concentrated in a single foreign class
 * (FDP=1 <= FEW=5). Satisfies the Feature Envy detection criteria
 * (Lanza & Marinescu Fig. 5.4).
 *
 * Plain-text fixture, NOT compiled by the Maven build.
 */
class FeatureEnvyKt {

    private var localData: String = ""
    private var localCounter: Int = 0

    fun methodWithFeatureEnvy(customer: CustomerService): String {
        val id = customer.customerId
        val name = customer.customerName
        val email = customer.email
        val phone = customer.phone
        val address = customer.address
        val credit = customer.creditLimit
        return "$id|$name|$email|$phone|$address|$credit"
    }

    fun simpleMethod() {
        localData = "simple"
        localCounter++
    }
}
