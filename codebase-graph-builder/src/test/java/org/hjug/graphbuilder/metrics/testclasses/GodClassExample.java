package org.hjug.graphbuilder.metrics.testclasses;

import java.util.List;

/**
 * Example class exhibiting God Class disharmony.
 * Per Lanza & Marinescu "Object-Oriented Metrics in Practice":
 * ATFD > 5 (directly accesses fields of more than 5 foreign classes),
 * WMC >= 47 (sum of cyclomatic complexity), TCC < 1/3 (low cohesion).
 *
 * Each method handles a different unrelated concern (orders, payments,
 * shipping, inventory, customers, notifications, reports), so they share
 * few accessed variables, keeping TCC low.
 */
public class GodClassExample {

    private OrderService orderService = new OrderService();
    private PaymentService paymentService = new PaymentService();
    private ShippingService shippingService = new ShippingService();
    private InventoryService inventoryService = new InventoryService();
    private CustomerService customerService = new CustomerService();
    private NotificationService notificationService = new NotificationService();
    private ReportingService reportingService = new ReportingService();

    // --- Order concern ---

    // CC=3 (for + if)
    public String processOrder(int orderId, List<String> items) {
        String orderRef = orderService.orderId;
        int currentStatus = orderService.orderStatus;
        for (String orderItem : items) {
            if (orderItem != null) {
                currentStatus++;
            }
        }
        return orderRef + "-" + currentStatus;
    }

    // CC=4 (if + else-if + else-if + else)
    public String classifyOrder(int amount) {
        int orderCount = orderService.orderCount;
        if (amount > 1000) {
            return "enterprise-" + orderCount;
        } else if (amount > 500) {
            return "bulk-" + orderCount;
        } else if (amount > 100) {
            return "standard-" + orderCount;
        } else {
            return "small-" + orderCount;
        }
    }

    // --- Payment concern ---

    // CC=4 (if + else-if + if)
    public boolean processPayment(double amount, String currency) {
        String paymentRef = paymentService.paymentRef;
        double paymentAmount = paymentService.paymentBalance;
        if (paymentRef == null) {
            return false;
        } else if (amount > paymentAmount) {
            return false;
        } else {
            if (currency != null) {
                return true;
            }
            return false;
        }
    }

    // CC=3 (for + if)
    public int countPendingPayments(List<String> paymentIds) {
        int pendingCount = 0;
        String paymentStatus = paymentService.paymentStatus;
        for (String pid : paymentIds) {
            if (pid != null && !paymentStatus.isEmpty()) {
                pendingCount++;
            }
        }
        return pendingCount;
    }

    // --- Shipping concern ---

    // CC=5 (if + else-if + else-if + else-if + else)
    public double calculateShippingCost(int weight, String destination) {
        double baseCost = shippingService.shippingRate;
        if (weight > 50) {
            return baseCost * 5;
        } else if (weight > 20) {
            return baseCost * 3;
        } else if (weight > 10) {
            return baseCost * 2;
        } else if (weight > 5) {
            return baseCost * 1.5;
        } else {
            return baseCost;
        }
    }

    // CC=3 (while + if)
    public String trackShipment(String shipmentId) {
        String trackingNum = shippingService.trackingNumber;
        int shippingStatus = shippingService.shippingStatus;
        while (shippingStatus < 5) {
            if (trackingNum.equals(shipmentId)) {
                return "in-transit-" + shippingStatus;
            }
            shippingStatus++;
        }
        return "delivered";
    }

    // --- Inventory concern ---

    // CC=5 (if + else-if + else-if + else-if)
    public String getStockStatus(String productId) {
        int stockLevel = inventoryService.stockLevel;
        int reservedUnits = inventoryService.reservedUnits;
        int available = stockLevel - reservedUnits;
        if (available <= 0) {
            return "out-of-stock";
        } else if (available < 5) {
            return "critical";
        } else if (available < 20) {
            return "low";
        } else if (available < 100) {
            return "adequate";
        } else {
            return "well-stocked";
        }
    }

    // CC=4 (for + if + if)
    public int reserveStock(List<String> productIds, int quantity) {
        int warehouseCapacity = inventoryService.warehouseCapacity;
        int reservedCount = 0;
        for (String pid : productIds) {
            if (pid != null) {
                if (reservedCount < warehouseCapacity) {
                    reservedCount += quantity;
                }
            }
        }
        return reservedCount;
    }

    // --- Customer concern ---

    // CC=5 (if + else-if + else-if + else-if)
    public String determineCustomerTier(int totalSpend) {
        String customerId = customerService.customerId;
        if (totalSpend > 10000) {
            return customerId + ":platinum";
        } else if (totalSpend > 5000) {
            return customerId + ":gold";
        } else if (totalSpend > 1000) {
            return customerId + ":silver";
        } else if (totalSpend > 0) {
            return customerId + ":bronze";
        } else {
            return customerId + ":new";
        }
    }

    // CC=3 (for + if)
    public boolean validateCustomerData(List<String> requiredFields) {
        String customerEmail = customerService.customerEmail;
        String customerPhone = customerService.customerPhone;
        for (String field : requiredFields) {
            if (field.equals("email") && customerEmail == null) {
                return false;
            }
        }
        return customerPhone != null;
    }

    // --- Notification concern ---

    // CC=4 (if + else-if + else-if)
    public void routeNotification(int priority, String message) {
        String notificationId = notificationService.notificationId;
        String notificationChannel = notificationService.notificationChannel;
        if (priority > 8) {
            System.out.println(notificationId + ":urgent:" + notificationChannel);
        } else if (priority > 5) {
            System.out.println(notificationId + ":normal:" + message);
        } else if (priority > 2) {
            System.out.println(notificationId + ":low:" + message);
        } else {
            System.out.println(notificationId + ":suppressed");
        }
    }

    // CC=3 (for + if)
    public int countUnreadNotifications(List<String> recipients) {
        int notificationPriority = notificationService.notificationPriority;
        int unreadCount = 0;
        for (String recipient : recipients) {
            if (recipient != null && notificationPriority > 0) {
                unreadCount++;
            }
        }
        return unreadCount;
    }

    // --- Reporting concern ---

    // CC=5 (if + else-if + else-if + else-if)
    public String formatReport(String format, boolean includeDetails) {
        String reportTitle = reportingService.reportTitle;
        String reportId = reportingService.reportId;
        if (format.equals("pdf")) {
            return reportId + ":pdf:" + reportTitle;
        } else if (format.equals("csv")) {
            return reportId + ":csv:" + reportTitle;
        } else if (format.equals("html")) {
            return reportId + ":html:" + (includeDetails ? reportTitle : "summary");
        } else if (format.equals("json")) {
            return reportId + ":json";
        } else {
            return reportId + ":text";
        }
    }

    // CC=3 (for + if)
    public int countScheduledReports(List<String> schedules) {
        String reportDate = reportingService.reportDate;
        int scheduledCount = 0;
        for (String schedule : schedules) {
            if (schedule != null && !reportDate.isEmpty()) {
                scheduledCount++;
            }
        }
        return scheduledCount;
    }

    // --- Utility methods with no shared fields (drive WMC) ---

    // CC=5 (4 if/else-if)
    public String categorizeAmount(double amount) {
        if (amount > 100000) {
            return "mega";
        } else if (amount > 10000) {
            return "large";
        } else if (amount > 1000) {
            return "medium";
        } else if (amount > 100) {
            return "small";
        } else {
            return "micro";
        }
    }

    // CC=4 (for + if + if)
    public boolean allNonNull(List<String> values) {
        for (String val : values) {
            if (val == null) {
                return false;
            }
            if (val.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // CC=5 (if + else-if + else-if + else-if)
    public int mapCodeToLevel(int code) {
        if (code >= 500) {
            return 5;
        } else if (code >= 400) {
            return 4;
        } else if (code >= 300) {
            return 3;
        } else if (code >= 200) {
            return 2;
        } else {
            return 1;
        }
    }

    static class OrderService {
        public String orderId = "ORD-001";
        public int orderStatus = 1;
        public int orderCount = 0;
    }

    static class PaymentService {
        public String paymentRef = "PAY-001";
        public double paymentBalance = 1000.0;
        public String paymentStatus = "pending";
    }

    static class ShippingService {
        public String trackingNumber = "TRACK-001";
        public double shippingRate = 5.0;
        public int shippingStatus = 1;
    }

    static class InventoryService {
        public int stockLevel = 100;
        public int reservedUnits = 10;
        public int warehouseCapacity = 500;
    }

    static class CustomerService {
        public String customerId = "CUST-001";
        public String customerEmail = "customer@example.com";
        public String customerPhone = "555-0100";
    }

    static class NotificationService {
        public String notificationId = "NOTIF-001";
        public String notificationChannel = "email";
        public int notificationPriority = 5;
    }

    static class ReportingService {
        public String reportId = "RPT-001";
        public String reportTitle = "Monthly Report";
        public String reportDate = "2024-01-01";
    }
}
