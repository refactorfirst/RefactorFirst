package org.hjug.graphbuilder.metrics.testclasses;

import org.hjug.graphbuilder.metrics.testclasses.external.CustomerService;
import org.hjug.graphbuilder.metrics.testclasses.external.ExternalDataService;
import org.hjug.graphbuilder.metrics.testclasses.external.InventoryService;
import org.hjug.graphbuilder.metrics.testclasses.external.NotificationService;
import org.hjug.graphbuilder.metrics.testclasses.external.OrderService;
import org.hjug.graphbuilder.metrics.testclasses.external.PaymentService;
import org.hjug.graphbuilder.metrics.testclasses.external.ProductService;
import org.hjug.graphbuilder.metrics.testclasses.external.ShippingService;

/**
 * Example class with Dispersed Coupling disharmony.
 * Per Lanza & Marinescu "Object-Oriented Metrics in Practice" Figs. 6.9/6.10:
 * CINT > SHORT_MEMORY_CAP(7) AND CDISP >= HALF(0.5) AND MAXNESTING > SHALLOW(1)
 *
 * methodWithDispersedCoupling calls 1 distinct method from each of 8 different classes:
 *   CINT = 8 > SHORT_MEMORY_CAP(7)
 *   CDISP = 8/8 = 1.0 >= HALF(0.5)
 *   MAXNESTING = 2 > SHALLOW(1)
 */
public class DispersedCouplingExample {

    private String localData;

    public void methodWithDispersedCoupling(
            CustomerService customer,
            OrderService order,
            PaymentService payment,
            ProductService product,
            InventoryService inventory,
            ShippingService shipping,
            NotificationService notification,
            ExternalDataService data) {
        String customerId = customer.getCustomerId();
        if (customerId != null) {
            String orderId = order.getOrderId();
            if (orderId != null) {
                String paymentId = payment.getPaymentId();
                String productId = product.getProductId();
                int stockLevel = inventory.getStockLevel();
                String trackingNumber = shipping.getTrackingNumber();
                String notificationId = notification.getNotificationId();
                String dataName = data.getName();
                localData = customerId + "|" + orderId + "|" + paymentId + "|" + productId + "|" + stockLevel + "|"
                        + trackingNumber + "|" + notificationId + "|" + dataName;
            }
        }
    }

    public void simpleMethod() {
        localData = "simple";
    }
}
