package com.ideacrest.parser.metrics.testclasses

/**
 * Example Kotlin class exhibiting God Class disharmony.
 * Mirrors the Java `GodClassExample`:
 *   ATFD > 5 (directly accesses fields of more than 5 foreign classes),
 *   WMC >= 47 (sum of cyclomatic complexity), TCC < 1/3 (low cohesion).
 *
 * Each method handles a different unrelated concern (orders, payments,
 * shipping, inventory, customers, notifications, reports), so they share
 * few accessed variables, keeping TCC low.
 *
 * NOTE: This is a plain-text fixture for OpenRewrite's Kotlin parser, NOT
 * compiled by the Maven build.
 */
class GodClassKt {

    private val orderService = OrderService()
    private val paymentService = PaymentService()
    private val shippingService = ShippingService()
    private val inventoryService = InventoryService()
    private val customerService = CustomerService()
    private val notificationService = NotificationService()
    private val reportingService = ReportingService()

    // --- Order concern ---

    // CC=3 (for + if)
    fun processOrder(orderId: Int, items: List<String>): String {
        val orderRef = orderService.orderId
        var currentStatus = orderService.orderStatus
        for (orderItem in items) {
            if (orderItem != null) {
                currentStatus++
            }
        }
        return "$orderRef-$currentStatus"
    }

    // CC=4 (if + else-if + else-if + else)
    fun classifyOrder(amount: Int): String {
        val orderCount = orderService.orderCount
        if (amount > 1000) {
            return "enterprise-$orderCount"
        } else if (amount > 500) {
            return "bulk-$orderCount"
        } else if (amount > 100) {
            return "standard-$orderCount"
        } else {
            return "small-$orderCount"
        }
    }

    // --- Payment concern ---

    // CC=4 (if + else-if + if)
    fun processPayment(amount: Double, currency: String?): Boolean {
        val paymentRef = paymentService.paymentRef
        val paymentAmount = paymentService.paymentBalance
        if (paymentRef == null) {
            return false
        } else if (amount > paymentAmount) {
            return false
        } else {
            if (currency != null) {
                return true
            }
            return false
        }
    }

    // CC=3 (for + if)
    fun countPendingPayments(paymentIds: List<String>): Int {
        var pendingCount = 0
        val paymentStatus = paymentService.paymentStatus
        for (pid in paymentIds) {
            if (pid != null && paymentStatus.isNotEmpty()) {
                pendingCount++
            }
        }
        return pendingCount
    }

    // --- Shipping concern ---

    // CC=5 (if + else-if + else-if + else-if + else)
    fun calculateShippingCost(weight: Int, destination: String?): Double {
        val baseCost = shippingService.shippingRate
        if (weight > 50) {
            return baseCost * 5
        } else if (weight > 20) {
            return baseCost * 3
        } else if (weight > 10) {
            return baseCost * 2
        } else if (weight > 5) {
            return baseCost * 1.5
        } else {
            return baseCost
        }
    }

    // CC=3 (while + if)
    fun trackShipment(shipmentId: String): String {
        val trackingNum = shippingService.trackingNumber
        var shippingStatus = shippingService.shippingStatus
        while (shippingStatus < 5) {
            if (trackingNum == shipmentId) {
                return "in-transit-$shippingStatus"
            }
            shippingStatus++
        }
        return "delivered"
    }

    // --- Inventory concern ---

    // CC=5 (if + else-if + else-if + else-if)
    fun getStockStatus(productId: String?): String {
        val stockLevel = inventoryService.stockLevel
        val reservedUnits = inventoryService.reservedUnits
        val available = stockLevel - reservedUnits
        if (available <= 0) {
            return "out-of-stock"
        } else if (available < 5) {
            return "critical"
        } else if (available < 20) {
            return "low"
        } else if (available < 100) {
            return "adequate"
        } else {
            return "well-stocked"
        }
    }

    // CC=4 (for + if + if)
    fun reserveStock(productIds: List<String>, quantity: Int): Int {
        val warehouseCapacity = inventoryService.warehouseCapacity
        var reservedCount = 0
        for (pid in productIds) {
            if (pid != null) {
                if (reservedCount < warehouseCapacity) {
                    reservedCount += quantity
                }
            }
        }
        return reservedCount
    }

    // --- Customer concern ---

    // CC=5 (if + else-if + else-if + else-if)
    fun determineCustomerTier(totalSpend: Int): String {
        val customerId = customerService.customerId
        if (totalSpend > 10000) {
            return "$customerId:platinum"
        } else if (totalSpend > 5000) {
            return "$customerId:gold"
        } else if (totalSpend > 1000) {
            return "$customerId:silver"
        } else if (totalSpend > 0) {
            return "$customerId:bronze"
        } else {
            return "$customerId:new"
        }
    }

    // CC=3 (for + if)
    fun validateCustomerData(requiredFields: List<String>): Boolean {
        val customerEmail = customerService.customerEmail
        val customerPhone = customerService.customerPhone
        for (field in requiredFields) {
            if (field == "email" && customerEmail == null) {
                return false
            }
        }
        return customerPhone != null
    }

    // --- Notification concern ---

    // CC=4 (if + else-if + else-if)
    fun routeNotification(priority: Int, message: String) {
        val notificationId = notificationService.notificationId
        val notificationChannel = notificationService.notificationChannel
        if (priority > 8) {
            println("$notificationId:urgent:$notificationChannel")
        } else if (priority > 5) {
            println("$notificationId:normal:$message")
        } else if (priority > 2) {
            println("$notificationId:low:$message")
        } else {
            println("$notificationId:suppressed")
        }
    }

    // CC=3 (for + if)
    fun countUnreadNotifications(recipients: List<String>): Int {
        val notificationPriority = notificationService.notificationPriority
        var unreadCount = 0
        for (recipient in recipients) {
            if (recipient != null && notificationPriority > 0) {
                unreadCount++
            }
        }
        return unreadCount
    }

    // --- Reporting concern ---

    // CC=5 (if + else-if + else-if + else-if)
    fun formatReport(format: String, includeDetails: Boolean): String {
        val reportTitle = reportingService.reportTitle
        val reportId = reportingService.reportId
        if (format == "pdf") {
            return "$reportId:pdf:$reportTitle"
        } else if (format == "csv") {
            return "$reportId:csv:$reportTitle"
        } else if (format == "html") {
            return "$reportId:html:" + if (includeDetails) reportTitle else "summary"
        } else if (format == "json") {
            return "$reportId:json"
        } else {
            return "$reportId:text"
        }
    }

    // CC=3 (for + if)
    fun countScheduledReports(schedules: List<String>): Int {
        val reportDate = reportingService.reportDate
        var scheduledCount = 0
        for (schedule in schedules) {
            if (schedule != null && reportDate.isNotEmpty()) {
                scheduledCount++
            }
        }
        return scheduledCount
    }

    // --- Utility methods with no shared fields (drive WMC) ---

    // CC=5 (4 if/else-if)
    fun categorizeAmount(amount: Double): String {
        if (amount > 100000) {
            return "mega"
        } else if (amount > 10000) {
            return "large"
        } else if (amount > 1000) {
            return "medium"
        } else if (amount > 100) {
            return "small"
        } else {
            return "micro"
        }
    }

    // CC=4 (for + if + if)
    fun allNonNull(values: List<String>): Boolean {
        for (v in values) {
            if (v == null) {
                return false
            }
            if (v.isEmpty()) {
                return false
            }
        }
        return true
    }

    // CC=5 (if + else-if + else-if + else-if)
    fun mapCodeToLevel(code: Int): Int {
        if (code >= 500) {
            return 5
        } else if (code >= 400) {
            return 4
        } else if (code >= 300) {
            return 3
        } else if (code >= 200) {
            return 2
        } else {
            return 1
        }
    }

    class OrderService {
        var orderId: String = "ORD-001"
        var orderStatus: Int = 1
        var orderCount: Int = 0
    }

    class PaymentService {
        var paymentRef: String = "PAY-001"
        var paymentBalance: Double = 1000.0
        var paymentStatus: String = "pending"
    }

    class ShippingService {
        var trackingNumber: String = "TRACK-001"
        var shippingRate: Double = 5.0
        var shippingStatus: Int = 1
    }

    class InventoryService {
        var stockLevel: Int = 100
        var reservedUnits: Int = 10
        var warehouseCapacity: Int = 500
    }

    class CustomerService {
        var customerId: String = "CUST-001"
        var customerEmail: String = "customer@example.com"
        var customerPhone: String = "555-0100"
    }

    class NotificationService {
        var notificationId: String = "NOTIF-001"
        var notificationChannel: String = "email"
        var notificationPriority: Int = 5
    }

    class ReportingService {
        var reportId: String = "RPT-001"
        var reportTitle: String = "Monthly Report"
        var reportDate: String = "2024-01-01"
    }
}
