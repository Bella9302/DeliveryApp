package com.itmda3b33.deliveryapp.validation

import com.itmda3b33.deliveryapp.data.model.ComplaintCategory
import com.itmda3b33.deliveryapp.data.model.Money
import com.itmda3b33.deliveryapp.data.model.OrderStatus
import com.itmda3b33.deliveryapp.data.model.RefundStatus
import com.itmda3b33.deliveryapp.validation.ProposedAction.CreateComplaint
import com.itmda3b33.deliveryapp.validation.ProposedAction.EscalateToAgent
import com.itmda3b33.deliveryapp.validation.ProposedAction.IssueRefund
import com.itmda3b33.deliveryapp.validation.ProposedAction.LookupOrder

/**
 * Checks every action Gemini proposes against the rules in the Systems Brief, section 6.
 * Checks run in this order: ownership, delivery, duplicate, well-formed, amount and escalation.
 * The first failing check decides the result.
 *
 * Plain Kotlin with no database or Android code, so every rule is covered by unit tests.
 */
class ActionValidator(private val policy: RefundPolicy = RefundPolicy()) {

    fun validate(action: ProposedAction, ctx: ValidationContext): Decision = when (action) {
        is LookupOrder -> checkOwnership(ctx) ?: Decision.Allow
        is CreateComplaint -> validateComplaint(action, ctx)
        is IssueRefund -> validateRefund(action, ctx)
        // "I want a human" must always work, so escalation is never blocked.
        is EscalateToAgent -> Decision.Allow
    }

    private fun validateComplaint(action: CreateComplaint, ctx: ValidationContext): Decision {
        val category = ComplaintCategory.from(action.category)
        val isMissingItem = category == ComplaintCategory.MISSING_ITEM
        return checkOwnership(ctx)
            ?: (if (isMissingItem) checkDelivered(ctx) else null)
            ?: (if (isMissingItem) checkNotRefunded(action.itemId, ctx) else null)
            ?: checkComplaintWellFormed(action, category, ctx)
            ?: checkComplaintEscalation(category)
            ?: Decision.Allow
    }

    private fun validateRefund(action: IssueRefund, ctx: ValidationContext): Decision {
        return checkOwnership(ctx)
            ?: checkDelivered(ctx)
            ?: checkNotRefunded(action.itemId, ctx)
            ?: checkRefundWellFormed(action, ctx)
            ?: decideRefundAmount(action, ctx)
    }

    // Check 1: ownership. The order, and the complaint if there is one, must belong to the customer.
    private fun checkOwnership(ctx: ValidationContext): Decision? {
        val order = ctx.order
        if (order == null || order.customerId != ctx.customerId) {
            return Decision.Block(Check.OWNERSHIP, "That order could not be found on your account.")
        }
        val complaint = ctx.complaint
        if (complaint != null && complaint.customerId != ctx.customerId) {
            return Decision.Block(Check.OWNERSHIP, "That complaint could not be found on your account.")
        }
        return null
    }

    // Check 2: delivery. Missing items can only be claimed on a delivered order.
    private fun checkDelivered(ctx: ValidationContext): Decision? {
        val status = OrderStatus.from(ctx.order?.status)
        if (status == OrderStatus.DELIVERED) return null
        val current = status?.label?.lowercase() ?: "not delivered yet"
        return Decision.Block(
            Check.DELIVERY,
            "Missing items can only be reported after the order is delivered. This order is $current.",
        )
    }

    // Check 3: duplicate. An item with a live refund (anything except rejected) cannot be refunded again.
    private fun checkNotRefunded(itemId: String?, ctx: ValidationContext): Decision? {
        if (itemId.isNullOrBlank()) return null // Caught by the well-formed check.
        val alreadyRefunded = ctx.existingRefunds.any {
            it.itemId == itemId && RefundStatus.from(it.status) != RefundStatus.REJECTED
        }
        return if (alreadyRefunded) {
            Decision.Block(Check.DUPLICATE, "A refund for this item has already been made or requested.")
        } else {
            null
        }
    }

    // Check 4: well-formed. All inputs are present and match the order.
    private fun checkComplaintWellFormed(
        action: CreateComplaint,
        category: ComplaintCategory?,
        ctx: ValidationContext,
    ): Decision? {
        if (category == null) {
            return wellFormedBlock("The type of problem was not clear. Please describe what went wrong.")
        }
        if (action.description.isNullOrBlank()) {
            return wellFormedBlock("Please describe the problem.")
        }
        if (category == ComplaintCategory.MISSING_ITEM) {
            return checkItemAndQuantity(action.itemId, action.quantity, ctx)
        }
        return null
    }

    private fun checkRefundWellFormed(action: IssueRefund, ctx: ValidationContext): Decision? {
        val complaint = ctx.complaint
            ?: return wellFormedBlock("A complaint must be logged before a refund.")
        if (complaint.orderId != ctx.order?.id) {
            return wellFormedBlock("The complaint is for a different order.")
        }
        if (ComplaintCategory.from(complaint.category) != ComplaintCategory.MISSING_ITEM) {
            return wellFormedBlock("Only missing items can be refunded automatically.")
        }
        if (complaint.itemId.isNotBlank() && complaint.itemId != action.itemId) {
            return wellFormedBlock("The refund is for a different item than the complaint.")
        }
        return checkItemAndQuantity(action.itemId, action.quantity, ctx)
    }

    private fun checkItemAndQuantity(itemId: String?, quantity: Int?, ctx: ValidationContext): Decision? {
        if (itemId.isNullOrBlank()) return wellFormedBlock("Please say which item is missing.")
        val line = ctx.order?.items?.firstOrNull { it.itemId == itemId }
            ?: return wellFormedBlock("That item is not on this order.")
        if (quantity == null || quantity < 1) return wellFormedBlock("Please say how many are missing.")
        if (quantity > line.quantity) {
            return wellFormedBlock("Only ${line.quantity} x ${line.name} were ordered.")
        }
        return null
    }

    private fun wellFormedBlock(reason: String) = Decision.Block(Check.WELL_FORMED, reason)

    // Check 5: amount and escalation.
    private fun checkComplaintEscalation(category: ComplaintCategory?): Decision? = when (category) {
        ComplaintCategory.MISSING_ITEM -> null // Decided by the refund amount.
        ComplaintCategory.SAFETY_HEALTH ->
            Decision.Escalate(Check.AMOUNT_AND_ESCALATION, "Safety and health issues are always handled by a person.")
        else ->
            Decision.Escalate(Check.AMOUNT_AND_ESCALATION, "This kind of problem needs a person to review it.")
    }

    private fun decideRefundAmount(action: IssueRefund, ctx: ValidationContext): Decision {
        // Both are non-null here: the well-formed check has passed.
        val line = ctx.order!!.items.first { it.itemId == action.itemId }
        val valueCents = line.unitPriceCents * action.quantity!!
        val refundCents = policy.refundFor(valueCents)
        return if (policy.canAutoRefund(valueCents)) {
            Decision.AllowRefund(refundCents)
        } else {
            Decision.Escalate(
                Check.AMOUNT_AND_ESCALATION,
                "Missing items worth ${Money.format(valueCents)} need an agent to approve the refund.",
                suggestedRefundCents = refundCents,
            )
        }
    }
}
