package org.hjug.graphbuilder.metrics.testclasses;

import org.hjug.graphbuilder.metrics.testclasses.external.CustomerService;
import org.hjug.graphbuilder.metrics.testclasses.external.ExternalDataService;
import org.hjug.graphbuilder.metrics.testclasses.external.InventoryService;
import org.hjug.graphbuilder.metrics.testclasses.external.NotificationService;
import org.hjug.graphbuilder.metrics.testclasses.external.OrderService;
import org.hjug.graphbuilder.metrics.testclasses.external.PaymentService;
import org.hjug.graphbuilder.metrics.testclasses.external.ProductService;
import org.hjug.graphbuilder.metrics.testclasses.external.ShippingService;

public class DispersedCouplingExample {

    private String localData;

    public void methodWithDispersedCoupling() {
        ExternalDataService dataService = new ExternalDataService();
        CustomerService customerService = new CustomerService();
        OrderService orderService = new OrderService();
        ProductService productService = new ProductService();
        InventoryService inventoryService = new InventoryService();
        PaymentService paymentService = new PaymentService();
        ShippingService shippingService = new ShippingService();
        NotificationService notificationService = new NotificationService();

        String result = dataService.name
                + customerService.customerId
                + orderService.orderId
                + productService.productId
                + inventoryService.warehouseId
                + paymentService.paymentId
                + shippingService.trackingNumber
                + notificationService.notificationId;

        localData = result;
    }

    public void simpleMethod() {
        localData = "simple";
    }
}
