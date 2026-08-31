package com.ideacrest.parser.kotlin.disharmony.parity.external

class ShippingService {
    var trackingNumber: String = "TRK-001"
    var carrier: String = "UPS"

    fun getTrackingNumber(): String = trackingNumber

    fun getCarrier(): String = carrier
}
