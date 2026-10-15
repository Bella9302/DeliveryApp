package com.itmda3b33.eats.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.itmda3b33.eats.data.model.ComplaintStatus
import com.itmda3b33.eats.data.model.Refund
import com.itmda3b33.eats.data.model.RefundStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/** Refund records. No real money moves: a completed refund is only a record (real payments are out of scope). */
class RefundRepository(private val db: FirebaseFirestore) {

    private val refunds get() = db.collection(Collections.REFUNDS)

    suspend fun getForOrder(customerId: String, orderId: String): List<Refund> =
        refunds.whereEqualTo(Fields.CUSTOMER_ID, customerId)
            .whereEqualTo(Fields.ORDER_ID, orderId)
            .get().await()
            .documents.mapNotNull { it.toRefund() }

    suspend fun getForComplaint(complaintId: String, customerId: String? = null): List<Refund> {
        var query: Query = refunds.whereEqualTo(Fields.COMPLAINT_ID, complaintId)
        // Customers must filter on their own ID so the security rules can allow the query.
        if (customerId != null) query = query.whereEqualTo(Fields.CUSTOMER_ID, customerId)
        return query.get().await().documents.mapNotNull { it.toRefund() }
    }

    fun observeForComplaint(complaintId: String, customerId: String? = null): Flow<List<Refund>> {
        var query: Query = refunds.whereEqualTo(Fields.COMPLAINT_ID, complaintId)
        if (customerId != null) query = query.whereEqualTo(Fields.CUSTOMER_ID, customerId)
        return query.observe { it.toRefund() }
    }

    fun observeAll(): Flow<List<Refund>> = refunds.observe { it.toRefund() }

    /**
     * Saves a system-approved refund. When [resolution] is given, the complaint is resolved in the same batch,
     * so the customer never sees one without the other.
     */
    suspend fun createAutoRefund(refund: Refund, resolution: String?): String {
        val ref = refunds.document()
        val batch = db.batch()
        batch.set(ref, refund.toNewMap() + mapOf(Fields.CREATED_AT to FieldValue.serverTimestamp(), Fields.DECIDED_AT to FieldValue.serverTimestamp()))
        if (resolution != null) {
            batch.update(
                db.collection(Collections.COMPLAINTS).document(refund.complaintId),
                mapOf(
                    Fields.STATUS to ComplaintStatus.RESOLVED.value,
                    "resolution" to resolution,
                    Fields.UPDATED_AT to FieldValue.serverTimestamp(),
                ),
            )
        }
        batch.commit().await()
        return ref.id
    }

    /** Saves a refund request for an agent and escalates its complaint in one batch. */
    suspend fun createRequestAndEscalate(refund: Refund): String {
        val ref = refunds.document()
        val batch = db.batch()
        batch.set(ref, refund.toNewMap() + mapOf(Fields.CREATED_AT to FieldValue.serverTimestamp()))
        batch.update(
            db.collection(Collections.COMPLAINTS).document(refund.complaintId),
            mapOf(Fields.STATUS to ComplaintStatus.ESCALATED.value, Fields.UPDATED_AT to FieldValue.serverTimestamp()),
        )
        batch.commit().await()
        return ref.id
    }

    /** An agent's decision. [approvedBy] is the agent's user ID, recorded for approvals. */
    suspend fun updateStatus(refundId: String, status: RefundStatus, approvedBy: String?) {
        val data = mutableMapOf<String, Any>(
            Fields.STATUS to status.value,
            Fields.DECIDED_AT to FieldValue.serverTimestamp(),
        )
        approvedBy?.let { data["approvedBy"] = it }
        refunds.document(refundId).update(data).await()
    }
}
