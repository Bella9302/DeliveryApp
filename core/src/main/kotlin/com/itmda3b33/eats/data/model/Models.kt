package com.itmda3b33.eats.data.model

/*
 * Plain data classes for the six Firestore collections and two subcollections.
 * Money is stored in cents (Long) so amounts never suffer from rounding errors.
 * Times are epoch milliseconds; the data layer converts Firestore Timestamps.
 */

data class UserProfile(
    val uid: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val mobile: String,
    val role: Role,
    /** Set only for restaurant users: the restaurant they manage. */
    val restaurantId: String? = null,
) {
    val displayName: String get() = "$firstName $lastName".trim()
}

data class Restaurant(
    val id: String,
    val name: String,
    val cuisine: String,
    val etaMinMinutes: Int,
    val etaMaxMinutes: Int,
    val rating: Double,
    val ownerId: String? = null,
)

/** Stored in restaurants/{restaurantId}/menuItems. */
data class MenuItem(
    val id: String,
    val restaurantId: String,
    val name: String,
    val description: String,
    val priceCents: Long,
    val available: Boolean = true,
)

data class Driver(
    val id: String,
    val name: String,
    val phone: String,
    val vehicle: String,
)

/** One line of an order. The price is copied at order time so later menu changes do not affect it. */
data class OrderItem(
    val menuItemId: String,
    val name: String,
    val priceCents: Long,
    val quantity: Int,
) {
    val lineTotalCents: Long get() = priceCents * quantity
}

data class Order(
    val id: String,
    val customerId: String,
    val restaurantId: String,
    val restaurantName: String,
    val driverId: String?,
    val status: OrderStatus,
    val items: List<OrderItem>,
    val totalCents: Long,
    val createdAtMillis: Long,
    val deliveredAtMillis: Long? = null,
) {
    fun item(menuItemId: String?): OrderItem? = items.firstOrNull { it.menuItemId == menuItemId }
}

/** An item named in a complaint, for example the missing chips. */
data class ComplaintItem(
    val menuItemId: String,
    val name: String,
    val quantity: Int,
)

data class Complaint(
    val id: String,
    val customerId: String,
    /** Null only when a customer asked for a human before naming an order. */
    val orderId: String?,
    val restaurantId: String?,
    val category: ComplaintCategory,
    val description: String,
    val items: List<ComplaintItem>,
    val status: ComplaintStatus,
    val resolution: String? = null,
    val assignedAgentId: String? = null,
    val source: ComplaintSource = ComplaintSource.FORM,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
)

/** Stored in complaints/{complaintId}/messages. */
data class ComplaintMessage(
    val id: String,
    val sender: MessageSender,
    val text: String,
    val createdAtMillis: Long,
)

data class Refund(
    val id: String,
    val complaintId: String,
    val orderId: String,
    val customerId: String,
    val menuItemId: String,
    val itemName: String,
    val quantity: Int,
    val amountCents: Long,
    val status: RefundStatus,
    /** "system" for automatic refunds, otherwise the approving agent's user ID. Null until decided. */
    val approvedBy: String?,
    val reason: String,
    val createdAtMillis: Long = 0,
    val decidedAtMillis: Long? = null,
) {
    companion object {
        const val APPROVED_BY_SYSTEM = "system"
    }
}

/** One line of a chat conversation, before it is saved as a [ComplaintMessage]. */
data class TranscriptLine(
    val sender: MessageSender,
    val text: String,
)
