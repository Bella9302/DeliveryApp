package com.itmda3b33.eats.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.itmda3b33.eats.data.model.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/** Orders are read-only in the app. They are created by the seed script (placing orders is out of scope). */
class OrderRepository(private val db: FirebaseFirestore) {

    private val orders get() = db.collection(Collections.ORDERS)

    fun observeCustomerOrders(customerId: String): Flow<List<Order>> =
        orders.whereEqualTo(Fields.CUSTOMER_ID, customerId)
            .orderBy(Fields.CREATED_AT, Query.Direction.DESCENDING)
            .observe { it.toOrder() }

    fun observeRestaurantOrders(restaurantId: String): Flow<List<Order>> =
        orders.whereEqualTo(Fields.RESTAURANT_ID, restaurantId)
            .orderBy(Fields.CREATED_AT, Query.Direction.DESCENDING)
            .observe { it.toOrder() }

    fun observeOrder(orderId: String): Flow<Order?> = orders.document(orderId).observe { it.toOrder() }

    /** Returns null when the order does not exist or the user may not read it (security rules refuse it). */
    suspend fun getOrder(orderId: String): Order? = try {
        orders.document(orderId).get().await().takeIf { it.exists() }?.toOrder()
    } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
        if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
    }

    suspend fun getRecentOrders(customerId: String, limit: Long = 5): List<Order> =
        orders.whereEqualTo(Fields.CUSTOMER_ID, customerId)
            .orderBy(Fields.CREATED_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .get().await()
            .documents.mapNotNull { it.toOrder() }
}
