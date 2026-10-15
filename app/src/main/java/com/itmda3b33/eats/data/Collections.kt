package com.itmda3b33.eats.data

/** Firestore collection and field names, kept in one place so they match firestore.rules and the seed script. */
internal object Collections {
    const val USERS = "users"
    const val RESTAURANTS = "restaurants"
    const val MENU_ITEMS = "menuItems"
    const val DRIVERS = "drivers"
    const val ORDERS = "orders"
    const val COMPLAINTS = "complaints"
    const val MESSAGES = "messages"
    const val REFUNDS = "refunds"
}

internal object Fields {
    const val CUSTOMER_ID = "customerId"
    const val RESTAURANT_ID = "restaurantId"
    const val ORDER_ID = "orderId"
    const val COMPLAINT_ID = "complaintId"
    const val STATUS = "status"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val DECIDED_AT = "decidedAt"
    const val NAME = "name"
}
