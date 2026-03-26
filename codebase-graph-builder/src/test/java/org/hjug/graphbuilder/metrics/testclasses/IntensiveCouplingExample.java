package org.hjug.graphbuilder.metrics.testclasses;

import org.hjug.graphbuilder.metrics.testclasses.external.CustomerService;
import org.hjug.graphbuilder.metrics.testclasses.external.ExternalDataService;
import org.hjug.graphbuilder.metrics.testclasses.external.OrderService;
import org.hjug.graphbuilder.metrics.testclasses.external.ProductService;

public class IntensiveCouplingExample {

    private String localData;

    public void methodWithIntensiveCoupling() {
        CustomerService customerService = new CustomerService();
        OrderService orderService = new OrderService();
        ProductService productService = new ProductService();
        ExternalDataService dataService = new ExternalDataService();

        String customerId = customerService.customerId;
        String customerName = customerService.customerName;
        String orderId = orderService.orderId;
        double amount = orderService.amount;
        String productId = productService.productId;
        String productName = productService.productName;
        String dataName = dataService.name;

        localData = customerId + customerName + orderId + amount + productId + productName + dataName;
    }

    public void anotherIntensiveMethod() {
        CustomerService customerService = new CustomerService();
        OrderService orderService = new OrderService();
        ProductService productService = new ProductService();
        ExternalDataService dataService = new ExternalDataService();

        localData = customerService.getCustomerId()
                + orderService.getOrderId()
                + productService.getProductId()
                + dataService.getName();
    }

    public void simpleMethod() {
        localData = "simple";
    }
}
