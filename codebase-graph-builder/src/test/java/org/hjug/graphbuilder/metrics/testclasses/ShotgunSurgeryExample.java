package org.hjug.graphbuilder.metrics.testclasses;

import org.hjug.graphbuilder.metrics.testclasses.external.CustomerService;
import org.hjug.graphbuilder.metrics.testclasses.external.ExternalDataService;
import org.hjug.graphbuilder.metrics.testclasses.external.InventoryService;
import org.hjug.graphbuilder.metrics.testclasses.external.NotificationService;
import org.hjug.graphbuilder.metrics.testclasses.external.OrderService;
import org.hjug.graphbuilder.metrics.testclasses.external.PaymentService;
import org.hjug.graphbuilder.metrics.testclasses.external.ProductService;
import org.hjug.graphbuilder.metrics.testclasses.external.ShippingService;

public class ShotgunSurgeryExample {

    private String result;

    public void processOrder() {
        CustomerService customerService = new CustomerService();
        OrderService orderService = new OrderService();
        ProductService productService = new ProductService();
        result = customerService.customerId + orderService.orderId + productService.productId;
    }

    public void validateOrder() {
        InventoryService inventoryService = new InventoryService();
        ProductService productService = new ProductService();
        result = inventoryService.warehouseId + productService.productName;
    }

    public void completePayment() {
        PaymentService paymentService = new PaymentService();
        CustomerService customerService = new CustomerService();
        result = paymentService.paymentId + customerService.customerName;
    }

    public void shipOrder() {
        ShippingService shippingService = new ShippingService();
        OrderService orderService = new OrderService();
        result = shippingService.trackingNumber + orderService.orderId;
    }

    public void sendNotification() {
        NotificationService notificationService = new NotificationService();
        CustomerService customerService = new CustomerService();
        result = notificationService.message + customerService.customerId;
    }

    public void updateInventory() {
        InventoryService inventoryService = new InventoryService();
        ProductService productService = new ProductService();
        result = String.valueOf(inventoryService.stockLevel) + productService.productId;
    }

    public void logData() {
        ExternalDataService dataService = new ExternalDataService();
        result = dataService.name + dataService.description;
    }

    public void getCustomerInfo() {
        CustomerService customerService = new CustomerService();
        result = customerService.getCustomerId();
    }
}
