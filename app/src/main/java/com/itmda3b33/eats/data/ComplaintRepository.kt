package com.itmda3b33.eats.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.itmda3b33.eats.data.model.Complaint
import com.itmda3b33.eats.data.model.ComplaintMessage
import com.itmda3b33.eats.data.model.ComplaintStatus
import com.itmda3b33.eats.data.model.TranscriptLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/** Complaints and their chat history (Module A). Rules are checked in the validation layer before these run. */
class ComplaintRepository(private val db: FirebaseFirestore) {

    private val complaints get() = db.collection(Collections.COMPLAINTS)

    fun observeCustomerComplaints(customerId: String): Flow<List<Complaint>> =
        complaints.whereEqualTo(Fields.CUSTOMER_ID, customerId)
            .orderBy(Fields.CREATED_AT, Query.Direction.DESCENDING)
            .observe { it.toComplaint() }

    /** The agent queue: complaints in the given statuses, oldest first so nobody waits too long. */
    fun observeByStatus(statuses: List<ComplaintStatus>): Flow<List<Complaint>> =
        complaints.whereIn(Fields.STATUS, statuses.map { it.value })
            .orderBy(Fields.CREATED_AT, Query.Direction.ASCENDING)
            .observe { it.toComplaint() }

    fun observeAll(): Flow<List<Complaint>> = complaints.observe { it.toComplaint() }

    fun observeComplaint(complaintId: String): Flow<Complaint?> =
        complaints.document(complaintId).observe { it.toComplaint() }

    fun observeMessages(complaintId: String): Flow<List<ComplaintMessage>> =
        complaints.document(complaintId).collection(Collections.MESSAGES)
            .orderBy(Fields.CREATED_AT, Query.Direction.ASCENDING)
            .observe { it.toMessage() }

    suspend fun getComplaint(complaintId: String): Complaint? = try {
        complaints.document(complaintId).get().await().takeIf { it.exists() }?.toComplaint()
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
    }

    suspend fun getCustomerComplaintsForOrder(customerId: String, orderId: String): List<Complaint> =
        complaints.whereEqualTo(Fields.CUSTOMER_ID, customerId)
            .whereEqualTo(Fields.ORDER_ID, orderId)
            .get().await()
            .documents.mapNotNull { it.toComplaint() }

    /** Saves a new complaint and returns its ID. */
    suspend fun create(complaint: Complaint): String {
        val ref = complaints.document()
        val data = complaint.toNewMap() + mapOf(
            Fields.CREATED_AT to FieldValue.serverTimestamp(),
            Fields.UPDATED_AT to FieldValue.serverTimestamp(),
        )
        ref.set(data).await()
        return ref.id
    }

    suspend fun updateDescription(complaintId: String, description: String) {
        complaints.document(complaintId).update(
            mapOf("description" to description, Fields.UPDATED_AT to FieldValue.serverTimestamp()),
        ).await()
    }

    /** Changes the status. [assignedAgentId] and [resolution] are only written when given. */
    suspend fun updateStatus(
        complaintId: String,
        status: ComplaintStatus,
        assignedAgentId: String? = null,
        resolution: String? = null,
    ) {
        val data = mutableMapOf<String, Any>(
            Fields.STATUS to status.value,
            Fields.UPDATED_AT to FieldValue.serverTimestamp(),
        )
        assignedAgentId?.let { data["assignedAgentId"] = it }
        resolution?.let { data["resolution"] = it }
        complaints.document(complaintId).update(data).await()
    }

    /** Deletes the complaint and its messages. */
    suspend fun delete(complaintId: String) {
        val ref = complaints.document(complaintId)
        val messages = ref.collection(Collections.MESSAGES).get().await()
        val batch = db.batch()
        messages.documents.forEach { batch.delete(it.reference) }
        batch.delete(ref)
        batch.commit().await()
    }

    /** Appends chat lines to the complaint's history in one write. */
    suspend fun appendMessages(complaintId: String, lines: List<TranscriptLine>) {
        if (lines.isEmpty()) return
        val messages = complaints.document(complaintId).collection(Collections.MESSAGES)
        val batch = db.batch()
        // Server timestamps in one batch are equal, so a sequence number keeps the order stable.
        val base = System.currentTimeMillis()
        lines.forEachIndexed { index, line ->
            batch.set(
                messages.document(),
                mapOf(
                    "sender" to line.sender.value,
                    "text" to line.text,
                    Fields.CREATED_AT to com.google.firebase.Timestamp(java.util.Date(base + index)),
                ),
            )
        }
        batch.commit().await()
    }
}
