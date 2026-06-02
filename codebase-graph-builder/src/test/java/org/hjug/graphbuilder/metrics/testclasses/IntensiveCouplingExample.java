package org.hjug.graphbuilder.metrics.testclasses;

import org.hjug.graphbuilder.metrics.testclasses.external.CustomerService;
import org.hjug.graphbuilder.metrics.testclasses.external.OrderService;

/**
 * Example class with Intensive Coupling disharmony.
 * Per Lanza & Marinescu "Object-Oriented Metrics in Practice" Figs. 6.3/6.4:
 * ((CINT > SHORT_MEMORY_CAP AND CDISP < HALF) OR (CINT > FEW AND CDISP < ONE_QUARTER))
 * AND MAXNESTING > SHALLOW
 *
 * methodWithIntensiveCoupling calls 8 distinct methods from exactly 2 classes:
 *   CustomerService: getCustomerId, getCustomerName, getEmail, getPhone, getAddress, getCreditLimit (6 calls)
 *   OrderService: getOrderId, getAmount (2 calls)
 *   CINT = 8, CDISP = 2/8 = 0.25
 *   Branch 1: CINT=8 > SHORT_MEMORY_CAP(7) AND CDISP=0.25 < HALF(0.5) → true
 *   MAXNESTING = 2 > SHALLOW(1) → true
 */
public class IntensiveCouplingExample {

    private String localData;

    public void methodWithIntensiveCoupling(CustomerService customer, OrderService order) {
        String customerId = customer.getCustomerId();
        if (customerId != null) {
            String name = customer.getCustomerName();
            if (name != null) {
                String email = customer.getEmail();
                String phone = customer.getPhone();
                String address = customer.getAddress();
                double credit = customer.getCreditLimit();
                String orderId = order.getOrderId();
                double amount = order.getAmount();
                localData = customerId + "|" + name + "|" + email + "|" + phone + "|" + address + "|" + credit + "|"
                        + orderId + "|" + amount;
            }
        }
    }

    public void simpleMethod() {
        localData = "simple";
    }
}
