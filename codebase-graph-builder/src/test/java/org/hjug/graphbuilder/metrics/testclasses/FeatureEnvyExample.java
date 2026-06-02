package org.hjug.graphbuilder.metrics.testclasses;

import org.hjug.graphbuilder.metrics.testclasses.external.CustomerService;

/**
 * Example class exhibiting Feature Envy disharmony.
 * Per Lanza & Marinescu "Object-Oriented Metrics in Practice" Fig. 5.4:
 * A method has Feature Envy when:
 *   ATFD > FEW (5)  — accesses more than 5 foreign attributes
 *   LAA < ONE_THIRD (0.33) — less than 1/3 of accessed attributes belong to its own class
 *   FDP <= FEW (5)  — foreign accesses are concentrated in at most 5 classes
 *
 * methodWithFeatureEnvy accesses all 6 public fields of CustomerService:
 *   ATFD = 6 > FEW(5)
 *   LAA = 0 own attributes / (0 + 6 foreign) = 0.0 < ONE_THIRD(0.33)
 *   FDP = 1 (only CustomerService) <= FEW(5)
 */
public class FeatureEnvyExample {

    private String localData;
    private int localCounter;

    public String methodWithFeatureEnvy(CustomerService customer) {
        // Access all 6 public fields of CustomerService (ATFD=6, FDP=1)
        String id = customer.customerId;
        String name = customer.customerName;
        String email = customer.email;
        String phone = customer.phone;
        String address = customer.address;
        double credit = customer.creditLimit;
        // No own-attribute accesses in this method → LAA = 0 < 1/3
        return id + "|" + name + "|" + email + "|" + phone + "|" + address + "|" + credit;
    }

    public void simpleMethod() {
        localData = "simple";
        localCounter++;
    }
}
