package com.ideacrest.parser.kotlin.disharmony.parity.external

class NotificationService {
    var notificationId: String = "NOTIF-001"
    var message: String = "hello"

    fun getNotificationId(): String = notificationId

    fun getMessage(): String = message
}
