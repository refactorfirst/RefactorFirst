package com.ideacrest.parser.kotlin.disharmony.parity.external

class InventoryService {
    var stockLevel: Int = 100
    var warehouseId: String = "WH-001"

    fun getStockLevel(): Int = stockLevel

    fun getWarehouseId(): String = warehouseId
}
