package com.ideacrest.parser.kotlin.disharmony.parity

import com.ideacrest.parser.kotlin.disharmony.parity.external.CustomerService
import com.ideacrest.parser.kotlin.disharmony.parity.external.OrderService
import com.ideacrest.parser.kotlin.disharmony.parity.external.PaymentService
import com.ideacrest.parser.kotlin.disharmony.parity.external.ProductService
import com.ideacrest.parser.kotlin.disharmony.parity.external.InventoryService
import com.ideacrest.parser.kotlin.disharmony.parity.external.ShippingService
import com.ideacrest.parser.kotlin.disharmony.parity.external.NotificationService
import com.ideacrest.parser.kotlin.disharmony.parity.external.ExternalDataService

/**
 * Kotlin disharmony parity fixture — Kotlin twin of `DispersedCouplingExample`.
 *
 * `methodWithDispersedCoupling` calls 1 method on each of 8 different
 * foreign classes:
 *   CINT = 8 > SHORT_MEMORY_CAP(7)
 *   CDISP = 8/8 = 1.0 >= HALF(0.5)
 *   MAXNESTING = 2 > SHALLOW(1)
 * Satisfies Lanza & Marinescu Fig. 6.9.
 */
class DispersedCouplingKt {

    private var localData: String = ""

    fun methodWithDispersedCoupling(
        customer: CustomerService,
        order: OrderService,
        payment: PaymentService,
        product: ProductService,
        inventory: InventoryService,
        shipping: ShippingService,
        notification: NotificationService,
        data: ExternalDataService
    ) {
        val customerId = customer.getCustomerId()
        if (customerId != null) {
            val orderId = order.getOrderId()
            if (orderId != null) {
                val paymentId = payment.getPaymentId()
                val productId = product.getProductId()
                val stockLevel = inventory.getStockLevel()
                val trackingNumber = shipping.getTrackingNumber()
                val notificationId = notification.getNotificationId()
                val dataName = data.getName()
                localData = "$customerId|$orderId|$paymentId|$productId|$stockLevel|$trackingNumber|$notificationId|$dataName"
            }
        }
    }

    fun simpleMethod() {
        localData = "simple"
    }
}
