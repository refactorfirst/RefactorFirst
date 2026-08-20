package com.ideacrest.parser.kotlin.disharmony.parity

import com.ideacrest.parser.kotlin.disharmony.parity.external.CustomerService
import com.ideacrest.parser.kotlin.disharmony.parity.external.OrderService

/**
 * Kotlin disharmony parity fixture — Kotlin twin of `IntensiveCouplingExample`.
 *
 * `methodWithIntensiveCoupling` calls 8 distinct methods on 2 classes:
 *   CustomerService: getCustomerId, getCustomerName, getEmail, getPhone, getAddress, getCreditLimit (6 calls)
 *   OrderService: getOrderId, getAmount (2 calls)
 *   CINT=8, CDISP=2/8=0.25 (< HALF), MAXNESTING=2 (> SHALLOW)
 * Branch 1 of Fig. 6.3: CINT > SHORT_MEMORY_CAP AND CDISP < HALF AND MAXNESTING > SHALLOW.
 */
class IntensiveCouplingKt {

    private var localData: String = ""

    fun methodWithIntensiveCoupling(customer: CustomerService, order: OrderService) {
        val customerId = customer.getCustomerId()
        if (customerId != null) {
            val name = customer.getCustomerName()
            if (name != null) {
                val email = customer.getEmail()
                val phone = customer.getPhone()
                val address = customer.getAddress()
                val credit = customer.getCreditLimit()
                val orderId = order.getOrderId()
                val amount = order.getAmount()
                localData = "$customerId|$name|$email|$phone|$address|$credit|$orderId|$amount"
            }
        }
    }

    fun simpleMethod() {
        localData = "simple"
    }
}
