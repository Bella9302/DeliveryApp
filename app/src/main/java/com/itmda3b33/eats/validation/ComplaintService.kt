package com.itmda3b33.eats.validation

import com.itmda3b33.eats.data.ComplaintRepository
import com.itmda3b33.eats.data.OrderRepository
import com.itmda3b33.eats.data.RefundRepository
import com.itmda3b33.eats.data.model.Complaint
import com.itmda3b33.eats.data.model.ComplaintCategory
import com.itmda3b33.eats.data.model.ComplaintSource
import com.itmda3b33.eats.data.model.MessageSender
import com.itmda3b33.eats.data.model.TranscriptLine

/** Customer complaint actions from the complaint screens (create, update, delete). Each one is checked first. */
class ComplaintService(
    private val orders: OrderRepository,
    private val complaints: ComplaintRepository,
    private val refunds: RefundRepository,
) {

    suspend fun create(
        customerId: String,
        orderId: String,
        category: ComplaintCategory?,
        description: String,
        items: List<ItemClaim>,
    ): ServiceResult<String> = guarded {
        val order = orders.getOrder(orderId)?.takeIf { it.customerId == customerId }
            ?: return@guarded ServiceResult.Invalid(RuleFailure(Check.OWNERSHIP, "order_not_found", "That order is not on your account."))
        val existing = complaints.getCustomerComplaintsForOrder(customerId, orderId)
        val checked = ComplaintRules.validateCreate(customerId, order, category, description, items, existing)
            .getOrElse { return@guarded ServiceResult.Invalid((it as RuleException).failure) }

        val id = complaints.create(
            Complaint(
                id = "",
                customerId = customerId,
                orderId = order.id,
                restaurantId = order.restaurantId,
                category = category!!,
                description = description.trim(),
                items = checked,
                status = ComplaintRules.initialStatus(category),
                source = ComplaintSource.FORM,
            ),
        )
        ServiceResult.Success(id)
    }

    suspend fun updateDescription(customerId: String, complaint: Complaint, description: String): ServiceResult<Unit> = guarded {
        ComplaintRules.validateCustomerEdit(customerId, complaint, description)?.let { return@guarded ServiceResult.Invalid(it) }
        complaints.updateDescription(complaint.id, description.trim())
        ServiceResult.Success(Unit)
    }

    suspend fun delete(customerId: String, complaint: Complaint): ServiceResult<Unit> = guarded {
        val existingRefunds = refunds.getForComplaint(complaint.id, customerId)
        ComplaintRules.validateCustomerDelete(customerId, complaint, existingRefunds)?.let { return@guarded ServiceResult.Invalid(it) }
        complaints.delete(complaint.id)
        ServiceResult.Success(Unit)
    }

    /** A message from the customer or an agent in the complaint thread. */
    suspend fun reply(complaintId: String, sender: MessageSender, text: String): ServiceResult<Unit> = guarded {
        val clean = text.trim()
        if (clean.isEmpty()) {
            return@guarded ServiceResult.Invalid(RuleFailure(Check.WELL_FORMED, "empty_message", "Type a message first."))
        }
        if (clean.length > ComplaintRules.MAX_DESCRIPTION) {
            return@guarded ServiceResult.Invalid(RuleFailure(Check.WELL_FORMED, "message_too_long", "Please keep messages under ${ComplaintRules.MAX_DESCRIPTION} characters."))
        }
        complaints.appendMessages(complaintId, listOf(TranscriptLine(sender, clean)))
        ServiceResult.Success(Unit)
    }
}
