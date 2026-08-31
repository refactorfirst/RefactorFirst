package com.ideacrest.parser.kotlin.disharmony.parity.external

class PaymentService {
    var paymentId: String = "PAY-001"
    var paymentMethod: String = "CARD"

    fun getPaymentId(): String = paymentId

    fun getPaymentMethod(): String = paymentMethod
}
