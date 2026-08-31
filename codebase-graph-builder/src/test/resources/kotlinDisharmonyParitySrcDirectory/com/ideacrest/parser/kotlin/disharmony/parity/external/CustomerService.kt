package com.ideacrest.parser.kotlin.disharmony.parity.external

/**
 * Kotlin disharmony parity — Kotlin twin of the Java `external.CustomerService`.
 * Provides 6 public mutable String/Double fields for the Feature
 * Envy fixture to access via foreign attribute access.
 *
 * Plain-text fixture for OpenRewrite's Kotlin parser, NOT compiled
 * by the Maven build.
 */
class CustomerService {
    var customerId: String = "CUST-001"
    var customerName: String = "Alice"
    var email: String = "alice@example.com"
    var phone: String = "555-0100"
    var address: String = "123 Main St"
    var creditLimit: Double = 1000.0

    fun getCustomerId(): String = customerId

    fun getCustomerName(): String = customerName

    fun getEmail(): String = email

    fun getPhone(): String = phone

    fun getAddress(): String = address

    fun getCreditLimit(): Double = creditLimit
}
