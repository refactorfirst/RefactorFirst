package com.ideacrest.parser.kotlin.disharmony.parity.external

class ExternalDataService {
    var name: String = "data"
    var value: Int = 42
    var description: String = "external data"

    fun getName(): String = name

    fun getValue(): Int = value

    fun getDescription(): String = description
}
