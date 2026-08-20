package com.ideacrest.parser.kotlin.disharmony.parity

/**
 * Kotlin disharmony parity fixture — Kotlin twin of the Java `DataClassExample`.
 *
 * Kotlin `class` (not `data class`) with public mutable properties +
 * explicit getters/setters, mirroring the Java Data Class shape so
 * that the existing `DisharmonyDetector.detectDataClasses()` detection
 * (WOC < 1/3 AND many public accessors AND low WMC) fires unchanged.
 *
 * NOTE: Plain-text fixture for OpenRewrite's Kotlin parser, NOT
 * compiled by the Maven build.
 */
class DataClassKt {
    var name: String = ""
    var age: Int = 0
    var email: String = ""
    var address: String = ""
    var phone: String = ""
    var city: String = ""

    private var internalId: String = ""

    fun getName(): String = name

    fun setName(name: String) {
        this.name = name
    }

    fun getAge(): Int = age

    fun setAge(age: Int) {
        this.age = age
    }

    fun getEmail(): String = email

    fun setEmail(email: String) {
        this.email = email
    }

    fun getAddress(): String = address

    fun setAddress(address: String) {
        this.address = address
    }

    fun getPhone(): String = phone

    fun setPhone(phone: String) {
        this.phone = phone
    }

    fun getCity(): String = city

    fun setCity(city: String) {
        this.city = city
    }

    fun getInternalId(): String = internalId

    fun setInternalId(internalId: String) {
        this.internalId = internalId
    }
}
