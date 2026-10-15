package com.itmda3b33.eats.validation

import com.itmda3b33.eats.data.model.Complaint
import com.itmda3b33.eats.data.model.ComplaintCategory
import com.itmda3b33.eats.data.model.ComplaintItem
import com.itmda3b33.eats.data.model.ComplaintStatus
import com.itmda3b33.eats.data.model.Order
import com.itmda3b33.eats.data.model.OrderItem
import com.itmda3b33.eats.data.model.OrderStatus
import com.itmda3b33.eats.data.model.Refund
import com.itmda3b33.eats.data.model.RefundStatus

/** Shared test data: one delivered order with a cheap, a mid-priced and an expensive item. */
object Fixtures {
    const val CUSTOMER = "cust-1"
    const val OTHER_CUSTOMER = "cust-2"

    val dip = OrderItem("dip", "Garlic dip", 1_200, 2)          // R12,00 each
    val chips = OrderItem("chips", "Chips", 3_500, 1)           // R35,00
    val platter = OrderItem("platter", "Family platter", 18_900, 1) // R189,00

    fun order(status: OrderStatus = OrderStatus.DELIVERED, customerId: String = CUSTOMER) = Order(
        id = "order-1",
        customerId = customerId,
        restaurantId = "rest-1",
        restaurantName = "Burger Spot",
        driverId = "driver-1",
        status = status,
        items = listOf(dip, chips, platter),
        totalCents = dip.lineTotalCents + chips.lineTotalCents + platter.lineTotalCents,
        createdAtMillis = 0,
    )

    fun complaint(
        category: ComplaintCategory = ComplaintCategory.MISSING_ITEM,
        status: ComplaintStatus = ComplaintStatus.LOGGED,
        customerId: String = CUSTOMER,
        orderId: String = "order-1",
    ) = Complaint(
        id = "complaint-1",
        customerId = customerId,
        orderId = orderId,
        restaurantId = "rest-1",
        category = category,
        description = "Missing items",
        items = listOf(ComplaintItem("dip", "Garlic dip", 1)),
        status = status,
    )

    fun refund(menuItemId: String, status: RefundStatus = RefundStatus.APPROVED) = Refund(
        id = "refund-$menuItemId",
        complaintId = "complaint-1",
        orderId = "order-1",
        customerId = CUSTOMER,
        menuItemId = menuItemId,
        itemName = menuItemId,
        quantity = 1,
        amountCents = 600,
        status = status,
        approvedBy = Refund.APPROVED_BY_SYSTEM,
        reason = "missing",
    )
}
