package com.itmda3b33.eats.data

import com.google.firebase.firestore.DocumentSnapshot
import com.itmda3b33.eats.data.model.Complaint
import com.itmda3b33.eats.data.model.ComplaintCategory
import com.itmda3b33.eats.data.model.ComplaintItem
import com.itmda3b33.eats.data.model.ComplaintMessage
import com.itmda3b33.eats.data.model.ComplaintSource
import com.itmda3b33.eats.data.model.ComplaintStatus
import com.itmda3b33.eats.data.model.MenuItem
import com.itmda3b33.eats.data.model.MessageSender
import com.itmda3b33.eats.data.model.Order
import com.itmda3b33.eats.data.model.OrderItem
import com.itmda3b33.eats.data.model.OrderStatus
import com.itmda3b33.eats.data.model.Refund
import com.itmda3b33.eats.data.model.RefundStatus
import com.itmda3b33.eats.data.model.Restaurant
import com.itmda3b33.eats.data.model.Role
import com.itmda3b33.eats.data.model.UserProfile

/*
 * Converts Firestore documents to models and back. Documents with unknown status values are skipped
 * (the mapper returns null) instead of crashing the app.
 */

private fun DocumentSnapshot.millis(field: String): Long? = getTimestamp(field)?.toDate()?.time

private fun DocumentSnapshot.int(field: String): Int = getLong(field)?.toInt() ?: 0

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.maps(field: String): List<Map<String, Any?>> =
    (get(field) as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

private fun Map<String, Any?>.long(key: String): Long = (this[key] as? Number)?.toLong() ?: 0

internal fun DocumentSnapshot.toUserProfile(): UserProfile? {
    val role = Role.from(getString("role")) ?: return null
    return UserProfile(
        uid = id,
        firstName = getString("firstName").orEmpty(),
        lastName = getString("lastName").orEmpty(),
        email = getString("email").orEmpty(),
        mobile = getString("mobile").orEmpty(),
        role = role,
        restaurantId = getString("restaurantId"),
    )
}

internal fun DocumentSnapshot.toRestaurant() = Restaurant(
    id = id,
    name = getString("name").orEmpty(),
    cuisine = getString("cuisine").orEmpty(),
    etaMinMinutes = int("etaMinMinutes"),
    etaMaxMinutes = int("etaMaxMinutes"),
    rating = getDouble("rating") ?: 0.0,
    ownerId = getString("ownerId"),
)

internal fun DocumentSnapshot.toMenuItem(restaurantId: String) = MenuItem(
    id = id,
    restaurantId = restaurantId,
    name = getString("name").orEmpty(),
    description = getString("description").orEmpty(),
    priceCents = getLong("priceCents") ?: 0,
    available = getBoolean("available") ?: true,
)

internal fun MenuItem.toMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "description" to description,
    "priceCents" to priceCents,
    "available" to available,
)

internal fun DocumentSnapshot.toOrder(): Order? {
    val status = OrderStatus.from(getString(Fields.STATUS)) ?: return null
    return Order(
        id = id,
        customerId = getString(Fields.CUSTOMER_ID).orEmpty(),
        restaurantId = getString(Fields.RESTAURANT_ID).orEmpty(),
        restaurantName = getString("restaurantName").orEmpty(),
        driverId = getString("driverId"),
        status = status,
        items = maps("items").map {
            OrderItem(
                menuItemId = it["menuItemId"] as? String ?: "",
                name = it["name"] as? String ?: "",
                priceCents = it.long("priceCents"),
                quantity = it.long("quantity").toInt(),
            )
        },
        totalCents = getLong("totalCents") ?: 0,
        createdAtMillis = millis(Fields.CREATED_AT) ?: 0,
        deliveredAtMillis = millis("deliveredAt"),
    )
}

internal fun DocumentSnapshot.toComplaint(): Complaint? {
    val status = ComplaintStatus.from(getString(Fields.STATUS)) ?: return null
    return Complaint(
        id = id,
        customerId = getString(Fields.CUSTOMER_ID).orEmpty(),
        orderId = getString(Fields.ORDER_ID),
        restaurantId = getString(Fields.RESTAURANT_ID),
        category = ComplaintCategory.from(getString("category")) ?: ComplaintCategory.OTHER,
        description = getString("description").orEmpty(),
        items = maps("items").map {
            ComplaintItem(
                menuItemId = it["menuItemId"] as? String ?: "",
                name = it["name"] as? String ?: "",
                quantity = it.long("quantity").toInt(),
            )
        },
        status = status,
        resolution = getString("resolution"),
        assignedAgentId = getString("assignedAgentId"),
        source = ComplaintSource.from(getString("source")),
        createdAtMillis = millis(Fields.CREATED_AT) ?: 0,
        updatedAtMillis = millis(Fields.UPDATED_AT) ?: 0,
    )
}

/** Fields for a new complaint. Timestamps are added by the repository. */
internal fun Complaint.toNewMap(): Map<String, Any?> = mapOf(
    Fields.CUSTOMER_ID to customerId,
    Fields.ORDER_ID to orderId,
    Fields.RESTAURANT_ID to restaurantId,
    "category" to category.value,
    "description" to description,
    "items" to items.map { mapOf("menuItemId" to it.menuItemId, "name" to it.name, "quantity" to it.quantity) },
    Fields.STATUS to status.value,
    "resolution" to resolution,
    "assignedAgentId" to assignedAgentId,
    "source" to source.value,
)

internal fun DocumentSnapshot.toMessage() = ComplaintMessage(
    id = id,
    sender = MessageSender.from(getString("sender")),
    text = getString("text").orEmpty(),
    createdAtMillis = millis(Fields.CREATED_AT) ?: 0,
)

internal fun DocumentSnapshot.toRefund(): Refund? {
    val status = RefundStatus.from(getString(Fields.STATUS)) ?: return null
    return Refund(
        id = id,
        complaintId = getString(Fields.COMPLAINT_ID).orEmpty(),
        orderId = getString(Fields.ORDER_ID).orEmpty(),
        customerId = getString(Fields.CUSTOMER_ID).orEmpty(),
        menuItemId = getString("menuItemId").orEmpty(),
        itemName = getString("itemName").orEmpty(),
        quantity = int("quantity"),
        amountCents = getLong("amountCents") ?: 0,
        status = status,
        approvedBy = getString("approvedBy"),
        reason = getString("reason").orEmpty(),
        createdAtMillis = millis(Fields.CREATED_AT) ?: 0,
        decidedAtMillis = millis(Fields.DECIDED_AT),
    )
}

internal fun Refund.toNewMap(): Map<String, Any?> = mapOf(
    Fields.COMPLAINT_ID to complaintId,
    Fields.ORDER_ID to orderId,
    Fields.CUSTOMER_ID to customerId,
    "menuItemId" to menuItemId,
    "itemName" to itemName,
    "quantity" to quantity,
    "amountCents" to amountCents,
    Fields.STATUS to status.value,
    "approvedBy" to approvedBy,
    "reason" to reason,
)

