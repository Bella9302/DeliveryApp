package com.itmda3b33.deliveryapp.validation

import com.itmda3b33.deliveryapp.data.model.Complaint
import com.itmda3b33.deliveryapp.data.model.ComplaintCategory
import com.itmda3b33.deliveryapp.data.model.Order
import com.itmda3b33.deliveryapp.data.model.OrderItem
import com.itmda3b33.deliveryapp.data.model.OrderStatus
import com.itmda3b33.deliveryapp.data.model.Refund
import com.itmda3b33.deliveryapp.data.model.RefundStatus

/** Shared records for the tests. Chips cost R12 each, a burger R65. */
object TestData {
    const val CUSTOMER = "cust-1"
    const val OTHER_CUSTOMER = "cust-2"

    val chips = OrderItem(itemId = "chips", name = "Chips", unitPriceCents = 12_00, quantity = 2)
    val burger = OrderItem(itemId = "burger", name = "Burger", unitPriceCents = 65_00, quantity = 1)
    val coke = OrderItem(itemId = "coke", name = "Coke", unitPriceCents = 15_00, quantity = 1)

    fun order(status: OrderStatus = OrderStatus.DELIVERED, customerId: String = CUSTOMER) = Order(
        id = "order-1",
        customerId = customerId,
        restaurantName = "Chicken Shack",
        status = status.value,
        items = listOf(chips, burger, coke),
        totalCents = 104_00,
    )

    fun complaint(itemId: String = "chips", category: ComplaintCategory = ComplaintCategory.MISSING_ITEM) =
        Complaint(id = "complaint-1", customerId = CUSTOMER, orderId = "order-1", itemId = itemId, category = category.value)

    fun refund(itemId: String, status: RefundStatus) =
        Refund(id = "refund-$itemId", orderId = "order-1", customerId = CUSTOMER, itemId = itemId, status = status.value)
}
