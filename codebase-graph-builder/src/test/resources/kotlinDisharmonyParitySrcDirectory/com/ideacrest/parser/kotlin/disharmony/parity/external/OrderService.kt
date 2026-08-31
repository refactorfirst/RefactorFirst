package com.ideacrest.parser.kotlin.disharmony.parity.external

class OrderService {
    var orderId: String = "ORD-001"
    var amount: Double = 100.0

    fun getOrderId(): String = orderId

    fun getAmount(): Double = amount
}
