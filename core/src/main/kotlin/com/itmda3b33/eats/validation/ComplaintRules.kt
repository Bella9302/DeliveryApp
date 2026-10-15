package com.itmda3b33.eats.validation

import com.itmda3b33.eats.data.model.Complaint
import com.itmda3b33.eats.data.model.ComplaintCategory
import com.itmda3b33.eats.data.model.ComplaintItem
import com.itmda3b33.eats.data.model.ComplaintStatus
import com.itmda3b33.eats.data.model.Order
import com.itmda3b33.eats.data.model.OrderStatus
import com.itmda3b33.eats.data.model.Refund

/** An item as the chatbot or the form names it, before it is checked. */
data class ItemClaim(
    val menuItemId: String?,
    val quantity: Int?,
)

/** Rules for creating, editing and deleting complaints (Module A). */
object ComplaintRules {

    const val MIN_DESCRIPTION = 5
    const val MAX_DESCRIPTION = 1_000

    /**
     * Checks a new complaint. On success returns the cleaned-up items with their names filled in.
     * Checks run in the brief's order: ownership, delivery, duplicate, well-formed.
     */
    fun validateCreate(
        customerId: String,
        order: Order,
        category: ComplaintCategory?,
        description: String?,
        items: List<ItemClaim>,
        existingComplaints: List<Complaint>,
    ): Result<List<ComplaintItem>> {
        val failure = firstFailure(
            {
                if (order.customerId != customerId) {
                    RuleFailure(Check.OWNERSHIP, "not_owner", "This order does not belong to your account.")
                } else null
            },
            {
                if (category == ComplaintCategory.MISSING_ITEM && order.status != OrderStatus.DELIVERED) {
                    RuleFailure(
                        Check.DELIVERY,
                        "not_delivered",
                        "This order is ${order.status.label.lowercase()}, not delivered yet, so items cannot be reported missing.",
                    )
                } else null
            },
            {
                val open = existingComplaints.firstOrNull {
                    it.orderId == order.id && it.category == category && it.status != ComplaintStatus.RESOLVED
                }
                if (open != null) {
                    RuleFailure(Check.DUPLICATE, "open_complaint_exists", "You already have an open complaint like this for this order (${open.id}).")
                } else null
            },
            { checkFields(category, description, items, order) },
        )
        if (failure != null) return Result.failure(RuleException(failure))

        val cleaned = items.map { claim ->
            val line = order.item(claim.menuItemId)!!
            ComplaintItem(line.menuItemId, line.name, claim.quantity!!)
        }
        return Result.success(cleaned)
    }

    /** New complaints about safety or health go straight to a person. */
    fun initialStatus(category: ComplaintCategory): ComplaintStatus =
        if (category == ComplaintCategory.SAFETY_HEALTH) ComplaintStatus.ESCALATED else ComplaintStatus.LOGGED

    /** A customer may change the description only while the complaint is still logged. */
    fun validateCustomerEdit(customerId: String, complaint: Complaint, description: String?): RuleFailure? =
        firstFailure(
            { ownerCheck(customerId, complaint) },
            { lockedCheck(complaint, "edited") },
            { checkDescription(description) },
        )

    /** A customer may delete a complaint only while it is logged and has no refunds. */
    fun validateCustomerDelete(customerId: String, complaint: Complaint, refunds: List<Refund>): RuleFailure? =
        firstFailure(
            { ownerCheck(customerId, complaint) },
            { lockedCheck(complaint, "deleted") },
            {
                if (refunds.isNotEmpty()) {
                    RuleFailure(Check.STATUS, "has_refunds", "This complaint has a refund, so it cannot be deleted.")
                } else null
            },
        )

    fun checkDescription(description: String?): RuleFailure? {
        val length = description?.trim()?.length ?: 0
        return when {
            length < MIN_DESCRIPTION ->
                RuleFailure(Check.WELL_FORMED, "description_too_short", "Please describe the problem in at least $MIN_DESCRIPTION characters.")
            length > MAX_DESCRIPTION ->
                RuleFailure(Check.WELL_FORMED, "description_too_long", "Please keep the description under $MAX_DESCRIPTION characters.")
            else -> null
        }
    }

    private fun checkFields(category: ComplaintCategory?, description: String?, items: List<ItemClaim>, order: Order): RuleFailure? {
        if (category == null) {
            return RuleFailure(Check.WELL_FORMED, "missing_category", "Please choose what the problem is about.")
        }
        checkDescription(description)?.let { return it }
        if (category == ComplaintCategory.MISSING_ITEM && items.isEmpty()) {
            return RuleFailure(Check.WELL_FORMED, "missing_items", "Please say which items were missing.")
        }
        if (items.map { it.menuItemId }.toSet().size != items.size) {
            return RuleFailure(Check.WELL_FORMED, "repeated_item", "Each item should be listed once.")
        }
        for (claim in items) {
            val line = order.item(claim.menuItemId)
                ?: return RuleFailure(Check.WELL_FORMED, "item_not_in_order", "One of the items is not part of this order.")
            val quantity = claim.quantity
            if (quantity == null || quantity < 1 || quantity > line.quantity) {
                return RuleFailure(Check.WELL_FORMED, "bad_quantity", "The quantity for ${line.name} must be between 1 and ${line.quantity}.")
            }
        }
        return null
    }

    private fun ownerCheck(customerId: String, complaint: Complaint): RuleFailure? =
        if (complaint.customerId != customerId) {
            RuleFailure(Check.OWNERSHIP, "not_owner", "This complaint does not belong to your account.")
        } else null

    private fun lockedCheck(complaint: Complaint, verb: String): RuleFailure? =
        if (complaint.status != ComplaintStatus.LOGGED) {
            RuleFailure(Check.STATUS, "complaint_locked", "This complaint is ${complaint.status.label.lowercase()} and can no longer be $verb.")
        } else null
}

/** Carries a [RuleFailure] inside a [Result]. */
class RuleException(val failure: RuleFailure) : Exception(failure.message)
