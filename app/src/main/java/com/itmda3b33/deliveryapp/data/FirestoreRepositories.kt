package com.itmda3b33.deliveryapp.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.itmda3b33.deliveryapp.data.FirestorePaths.COMPLAINTS
import com.itmda3b33.deliveryapp.data.FirestorePaths.MENU_ITEMS
import com.itmda3b33.deliveryapp.data.FirestorePaths.MESSAGES
import com.itmda3b33.deliveryapp.data.FirestorePaths.ORDERS
import com.itmda3b33.deliveryapp.data.FirestorePaths.REFUNDS
import com.itmda3b33.deliveryapp.data.FirestorePaths.RESTAURANTS
import com.itmda3b33.deliveryapp.data.FirestorePaths.USERS
import com.itmda3b33.deliveryapp.data.model.ChatMessage
import com.itmda3b33.deliveryapp.data.model.Complaint
import com.itmda3b33.deliveryapp.data.model.MenuItem
import com.itmda3b33.deliveryapp.data.model.Order
import com.itmda3b33.deliveryapp.data.model.Refund
import com.itmda3b33.deliveryapp.data.model.Restaurant
import com.itmda3b33.deliveryapp.data.model.User
import kotlinx.coroutines.tasks.await

/*
 * Firestore implementations of the repositories. Lists are sorted in memory, newest first,
 * so the app needs no composite indexes.
 */

/** Reads a document, treating "permission denied" the same as "not found". */
private suspend inline fun <reified T : Any> FirebaseFirestore.readOrNull(
    path: String,
    id: String,
    crossinline withId: T.(String) -> T,
): T? {
    if (id.isBlank()) return null
    return try {
        collection(path).document(id).get().await().toModel<T>()?.withId(id)
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
    }
}

private inline fun <reified T : Any> DocumentSnapshot.toModel(): T? = toObject(T::class.java)

private inline fun <reified T : Any> QuerySnapshot.toModels(withId: T.(String) -> T): List<T> =
    documents.mapNotNull { doc -> doc.toModel<T>()?.withId(doc.id) }

class FirestoreUserRepository(private val db: FirebaseFirestore) : UserRepository {

    override suspend fun get(uid: String): User? = db.readOrNull<User>(USERS, uid) { copy(id = it) }

    override suspend fun create(user: User) {
        db.collection(USERS).document(user.id).set(user.copy(id = "")).await()
    }
}

class FirestoreOrderRepository(private val db: FirebaseFirestore) : OrderRepository {

    override suspend fun get(orderId: String): Order? = db.readOrNull<Order>(ORDERS, orderId) { copy(id = it) }

    override suspend fun forCustomer(customerId: String): List<Order> =
        db.collection(ORDERS).whereEqualTo("customerId", customerId).get().await()
            .toModels<Order> { copy(id = it) }
            .sortedByDescending { it.createdAt }
}

class FirestoreComplaintRepository(private val db: FirebaseFirestore) : ComplaintRepository {

    private val complaints get() = db.collection(COMPLAINTS)

    override suspend fun create(complaint: Complaint): String =
        complaints.add(complaint.copy(id = "")).await().id

    override suspend fun get(complaintId: String): Complaint? =
        db.readOrNull<Complaint>(COMPLAINTS, complaintId) { copy(id = it) }

    override suspend fun forCustomer(customerId: String): List<Complaint> =
        complaints.whereEqualTo("customerId", customerId).get().await().toComplaints()

    override suspend fun withStatus(statuses: List<String>): List<Complaint> =
        complaints.whereIn("status", statuses).get().await().toComplaints()

    override suspend fun all(): List<Complaint> = complaints.get().await().toComplaints()

    override suspend fun updateStatus(complaintId: String, status: String, resolution: String?) {
        val changes = mutableMapOf<String, Any>("status" to status, "updatedAt" to System.currentTimeMillis())
        if (resolution != null) changes["resolution"] = resolution
        complaints.document(complaintId).update(changes).await()
    }

    override suspend fun updateDescription(complaintId: String, description: String) {
        complaints.document(complaintId)
            .update(mapOf("description" to description, "updatedAt" to System.currentTimeMillis()))
            .await()
    }

    override suspend fun delete(complaintId: String) {
        // Firestore does not delete subcollections with their parent, so remove the messages first.
        val messages = complaints.document(complaintId).collection(MESSAGES).get().await()
        val batch = db.batch()
        messages.documents.forEach { batch.delete(it.reference) }
        batch.delete(complaints.document(complaintId))
        batch.commit().await()
    }

    override suspend fun addMessages(complaintId: String, messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        val target = complaints.document(complaintId).collection(MESSAGES)
        val batch = db.batch()
        messages.forEach { batch.set(target.document(), it.copy(id = "")) }
        batch.commit().await()
    }

    override suspend fun messages(complaintId: String): List<ChatMessage> =
        complaints.document(complaintId).collection(MESSAGES).get().await()
            .toModels<ChatMessage> { copy(id = it) }
            .sortedBy { it.sentAt }

    private fun QuerySnapshot.toComplaints() =
        toModels<Complaint> { copy(id = it) }.sortedByDescending { it.createdAt }
}

class FirestoreRefundRepository(private val db: FirebaseFirestore) : RefundRepository {

    private val refunds get() = db.collection(REFUNDS)

    override suspend fun create(refund: Refund): String = refunds.add(refund.copy(id = "")).await().id

    override suspend fun forOrder(orderId: String, customerId: String): List<Refund> =
        refunds.whereEqualTo("customerId", customerId).whereEqualTo("orderId", orderId).get().await()
            .toModels<Refund> { copy(id = it) }

    override suspend fun forComplaint(complaintId: String): List<Refund> =
        refunds.whereEqualTo("complaintId", complaintId).get().await().toModels<Refund> { copy(id = it) }

    override suspend fun all(): List<Refund> = refunds.get().await().toModels<Refund> { copy(id = it) }

    override suspend fun decide(refundId: String, status: String, approvedBy: String) {
        refunds.document(refundId).update(mapOf("status" to status, "approvedBy" to approvedBy)).await()
    }
}

class FirestoreMenuRepository(private val db: FirebaseFirestore) : MenuRepository {

    private fun menu(restaurantId: String) =
        db.collection(RESTAURANTS).document(restaurantId).collection(MENU_ITEMS)

    override suspend fun restaurantOwnedBy(ownerId: String): Restaurant? =
        db.collection(RESTAURANTS).whereEqualTo("ownerId", ownerId).limit(1).get().await()
            .toModels<Restaurant> { copy(id = it) }
            .firstOrNull()

    override suspend fun items(restaurantId: String): List<MenuItem> =
        menu(restaurantId).get().await().toModels<MenuItem> { copy(id = it) }.sortedBy { it.name.lowercase() }

    override suspend fun add(restaurantId: String, item: MenuItem): String =
        menu(restaurantId).add(item.copy(id = "")).await().id

    override suspend fun update(restaurantId: String, item: MenuItem) {
        menu(restaurantId).document(item.id).set(item.copy(id = "")).await()
    }

    override suspend fun delete(restaurantId: String, itemId: String) {
        menu(restaurantId).document(itemId).delete().await()
    }
}

