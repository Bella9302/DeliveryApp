package com.itmda3b33.eats.validation

import com.itmda3b33.eats.data.model.Complaint
import com.itmda3b33.eats.data.model.ComplaintCategory
import com.itmda3b33.eats.data.model.ComplaintStatus
import com.itmda3b33.eats.data.model.Order
import com.itmda3b33.eats.data.model.OrderStatus
import com.itmda3b33.eats.data.model.Refund
import com.itmda3b33.eats.data.model.RefundStatus

/** The result of checking a proposed refund. */
sealed interface RefundDecision {
    /** All checks passed and the amount is inside the auto-approve limit. The system approves it. */
    data class AutoApprove(val amountCents: Long, val claimValueCents: Long) : RefundDecision

    /** The refund is valid but needs a person: it is saved as requested and the complaint is escalated. */
    data class Escalate(val code: String, val reason: String, val claimValueCents: Long) : RefundDecision

    /** A check failed. Nothing is saved. */
    data class Blocked(val failure: RuleFailure) : RefundDecision
}

/**
 * Checks a refund request against the rules, in the order the Systems Brief gives:
 * ownership, delivery, duplicate, well-formed, then amount and escalation.
 * The amount is always worked out here, never taken from the chatbot.
 */
object RefundRules {

    fun evaluate(
        customerId: String,
        order: Order,
        complaint: Complaint,
        menuItemId: String?,
        quantity: Int?,
        existingRefunds: List<Refund>,
        policy: RefundPolicy = RefundPolicy.DEFAULT,
    ): RefundDecision {
        val orderItem = order.item(menuItemId)

        val failure = firstFailure(
            { checkOwnership(customerId, order, complaint) },
            { checkDelivery(order, complaint) },
            { checkDuplicate(menuItemId, existingRefunds) },
            { checkWellFormed(complaint, menuItemId, quantity, orderItem?.quantity) },
        )
        if (failure != null) return RefundDecision.Blocked(failure)

        // Well-formed passed, so the item and quantity are present and valid.
        val claimValue = orderItem!!.priceCents * quantity!!
        return decideAmount(complaint.category, claimValue, policy)
    }

    private fun checkOwnership(customerId: String, order: Order, complaint: Complaint): RuleFailure? =
        if (order.customerId != customerId || complaint.customerId != customerId || complaint.orderId != order.id) {
            RuleFailure(Check.OWNERSHIP, "not_owner", "This order does not belong to your account.")
        } else {
            null
        }

    private fun checkDelivery(order: Order, complaint: Complaint): RuleFailure? =
        if (complaint.category == ComplaintCategory.MISSING_ITEM && order.status != OrderStatus.DELIVERED) {
            RuleFailure(
                Check.DELIVERY,
                "not_delivered",
                "This order is ${order.status.label.lowercase()}, not delivered yet, so items cannot be reported missing.",
            )
        } else {
            null
        }

    private fun checkDuplicate(menuItemId: String?, existingRefunds: List<Refund>): RuleFailure? =
        if (menuItemId != null && existingRefunds.any { it.menuItemId == menuItemId && it.status != RefundStatus.REJECTED }) {
            RuleFailure(Check.DUPLICATE, "already_refunded", "A refund for this item already exists, so it cannot be refunded again.")
        } else {
            null
        }

    private fun checkWellFormed(complaint: Complaint, menuItemId: String?, quantity: Int?, orderedQuantity: Int?): RuleFailure? =
        when {
            menuItemId.isNullOrBlank() ->
                RuleFailure(Check.WELL_FORMED, "missing_item_id", "Which item should be refunded?")
            orderedQuantity == null ->
                RuleFailure(Check.WELL_FORMED, "item_not_in_order", "That item is not part of this order.")
            quantity == null || quantity < 1 ->
                RuleFailure(Check.WELL_FORMED, "bad_quantity", "The quantity must be at least 1.")
            quantity > orderedQuantity ->
                RuleFailure(Check.WELL_FORMED, "quantity_too_high", "Only $orderedQuantity of that item was ordered.")
            complaint.status != ComplaintStatus.LOGGED ->
                RuleFailure(
                    Check.WELL_FORMED,
                    "complaint_not_open",
                    "This complaint is ${complaint.status.label.lowercase()}, so the assistant cannot refund it.",
                )
            else -> null
        }

    /** The last check: decide between an automatic refund and a person. */
    private fun decideAmount(category: ComplaintCategory, claimValue: Long, policy: RefundPolicy): RefundDecision =
        when {
            category == ComplaintCategory.SAFETY_HEALTH ->
                RefundDecision.Escalate("safety_health", "Safety and health issues are always handled by a person.", claimValue)
            category != ComplaintCategory.MISSING_ITEM ->
                RefundDecision.Escalate("needs_review", "Only missing items can be refunded automatically. An agent will review this.", claimValue)
            claimValue < policy.autoRefundBelowCents ->
                RefundDecision.AutoApprove(policy.autoRefundAmount(claimValue), claimValue)
            claimValue > policy.escalateAboveCents ->
                RefundDecision.Escalate("above_limit", "Refunds of this size are approved by an agent.", claimValue)
            policy.midRangeDecision == MidRangeDecision.AUTO_REFUND ->
                RefundDecision.AutoApprove(policy.autoRefundAmount(claimValue), claimValue)
            else ->
                RefundDecision.Escalate("mid_range", "Refunds of this size are approved by an agent.", claimValue)
        }
}
