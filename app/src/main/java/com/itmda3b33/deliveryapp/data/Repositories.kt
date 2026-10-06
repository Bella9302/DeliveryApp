package com.itmda3b33.deliveryapp.data

import com.itmda3b33.deliveryapp.data.model.ChatMessage
import com.itmda3b33.deliveryapp.data.model.Complaint
import com.itmda3b33.deliveryapp.data.model.MenuItem
import com.itmda3b33.deliveryapp.data.model.Order
import com.itmda3b33.deliveryapp.data.model.Refund
import com.itmda3b33.deliveryapp.data.model.Restaurant
import com.itmda3b33.deliveryapp.data.model.User

/*
 * The data layer's contracts. The Firestore classes in this package are the only code that
 * reads or writes the database. The interfaces let the validation layer be tested with fakes.
 * Read methods that take a customerId filter on it, because Firestore security rules reject
 * queries that could return other customers' documents.
 */

interface AuthRepository {
    val currentUserId: String?
    suspend fun signIn(email: String, password: String): String
    suspend fun signUp(email: String, password: String): String
    suspend fun sendPasswordReset(email: String)
    fun signOut()
}

interface UserRepository {
    suspend fun get(uid: String): User?
    suspend fun create(user: User)
}

interface OrderRepository {
    /** Returns null if the order does not exist or the user may not read it. */
    suspend fun get(orderId: String): Order?
    suspend fun forCustomer(customerId: String): List<Order>
}

interface ComplaintRepository {
    /** Saves a new complaint and returns its ID. */
    suspend fun create(complaint: Complaint): String

    /** Returns null if the complaint does not exist or the user may not read it. */
    suspend fun get(complaintId: String): Complaint?
    suspend fun forCustomer(customerId: String): List<Complaint>
    suspend fun withStatus(statuses: List<String>): List<Complaint>
    suspend fun all(): List<Complaint>
    suspend fun updateStatus(complaintId: String, status: String, resolution: String? = null)
    suspend fun updateDescription(complaintId: String, description: String)
    suspend fun delete(complaintId: String)
    suspend fun addMessages(complaintId: String, messages: List<ChatMessage>)
    suspend fun messages(complaintId: String): List<ChatMessage>
}

interface RefundRepository {
    /** Saves a new refund and returns its ID. */
    suspend fun create(refund: Refund): String
    suspend fun forOrder(orderId: String, customerId: String): List<Refund>
    suspend fun forComplaint(complaintId: String): List<Refund>
    suspend fun all(): List<Refund>
    suspend fun decide(refundId: String, status: String, approvedBy: String)
}

interface MenuRepository {
    suspend fun restaurantOwnedBy(ownerId: String): Restaurant?
    suspend fun items(restaurantId: String): List<MenuItem>
    suspend fun add(restaurantId: String, item: MenuItem): String
    suspend fun update(restaurantId: String, item: MenuItem)
    suspend fun delete(restaurantId: String, itemId: String)
}
