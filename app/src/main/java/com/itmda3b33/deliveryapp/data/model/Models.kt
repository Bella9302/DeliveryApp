package com.itmda3b33.deliveryapp.data.model

/*
 * Firestore documents. Every field has a default so Firestore can build the class with
 * toObject(). The document ID is not stored in the document; the data layer copies it into [id].
 * Money is stored in cents (Long) so refund maths never has rounding errors.
 * Times are epoch milliseconds.
 */

/** users/{uid} */
data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = Role.CUSTOMER.value,
)

/** restaurants/{restaurantId} */
data class Restaurant(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val address: String = "",
)

/** restaurants/{restaurantId}/menuItems/{itemId} */
data class MenuItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val priceCents: Long = 0,
    val available: Boolean = true,
)

/** drivers/{driverId} */
data class Driver(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val vehicle: String = "",
)

/** One line on an order. The price is copied at order time so later menu changes do not affect refunds. */
data class OrderItem(
    val itemId: String = "",
    val name: String = "",
    val unitPriceCents: Long = 0,
    val quantity: Int = 0,
)

/** orders/{orderId} */
data class Order(
    val id: String = "",
    val customerId: String = "",
    val restaurantId: String = "",
    val restaurantName: String = "",
    val driverId: String = "",
    val status: String = OrderStatus.PLACED.value,
    val items: List<OrderItem> = emptyList(),
    val totalCents: Long = 0,
    val createdAt: Long = 0,
)

/** complaints/{complaintId} */
data class Complaint(
    val id: String = "",
    val customerId: String = "",
    val orderId: String = "",
    val itemId: String = "",
    val quantity: Int = 0,
    val category: String = ComplaintCategory.OTHER.value,
    val description: String = "",
    val status: String = ComplaintStatus.LOGGED.value,
    val resolution: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** complaints/{complaintId}/messages/{messageId}. [sender] is "customer", "bot" or "agent". */
data class ChatMessage(
    val id: String = "",
    val sender: String = SENDER_CUSTOMER,
    val text: String = "",
    val sentAt: Long = 0,
) {
    companion object {
        const val SENDER_CUSTOMER = "customer"
        const val SENDER_BOT = "bot"
        const val SENDER_AGENT = "agent"
    }
}

/** refunds/{refundId}. [approvedBy] is "system", an agent's user ID, or null while undecided. */
data class Refund(
    val id: String = "",
    val complaintId: String = "",
    val orderId: String = "",
    val customerId: String = "",
    val itemId: String = "",
    val quantity: Int = 0,
    val amountCents: Long = 0,
    val status: String = RefundStatus.REQUESTED.value,
    val approvedBy: String? = null,
    val createdAt: Long = 0,
) {
    companion object {
        const val APPROVED_BY_SYSTEM = "system"
    }
}
