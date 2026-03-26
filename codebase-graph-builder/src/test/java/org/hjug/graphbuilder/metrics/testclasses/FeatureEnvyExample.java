package org.hjug.graphbuilder.metrics.testclasses;

import org.hjug.graphbuilder.metrics.testclasses.external.CustomerService;
import org.hjug.graphbuilder.metrics.testclasses.external.ExternalDataService;
import org.hjug.graphbuilder.metrics.testclasses.external.InventoryService;
import org.hjug.graphbuilder.metrics.testclasses.external.NotificationService;
import org.hjug.graphbuilder.metrics.testclasses.external.OrderService;
import org.hjug.graphbuilder.metrics.testclasses.external.PaymentService;
import org.hjug.graphbuilder.metrics.testclasses.external.ProductService;
import org.hjug.graphbuilder.metrics.testclasses.external.ShippingService;

public class FeatureEnvyExample {

    private String localData;
    private int localCounter;

    public void methodWithFeatureEnvy() {
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

    public void anotherMethodWithFeatureEnvy() {
        ExternalDataService dataService = new ExternalDataService();
        CustomerService customerService = new CustomerService();
        OrderService orderService = new OrderService();
        ProductService productService = new ProductService();
        InventoryService inventoryService = new InventoryService();
        PaymentService paymentService = new PaymentService();
        ShippingService shippingService = new ShippingService();
        NotificationService notificationService = new NotificationService();

        String combined = dataService.description
                + customerService.customerName
                + productService.productName
                + paymentService.paymentMethod
                + shippingService.carrier
                + notificationService.message;

        int total = dataService.value + inventoryService.stockLevel;
        double amount = orderService.amount;

        localData = combined;
        localCounter = total + (int) amount;
    }

    public void simpleMethod() {
        localData = "simple";
        localCounter++;
    }
}
