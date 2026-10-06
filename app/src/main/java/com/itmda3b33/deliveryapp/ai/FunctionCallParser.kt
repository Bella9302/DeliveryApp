package com.itmda3b33.deliveryapp.ai

import com.itmda3b33.deliveryapp.validation.ProposedAction

/**
 * Turns a function call from Gemini into a [ProposedAction]. Only the four functions in the
 * Systems Brief, section 5, are accepted; any other name returns null.
 * Arguments arrive as text, so numbers such as "2" or "2.0" are converted here.
 */
object FunctionCallParser {

    const val LOOKUP_ORDER = "lookupOrder"
    const val CREATE_COMPLAINT = "createComplaint"
    const val ISSUE_REFUND = "issueRefund"
    const val ESCALATE_TO_AGENT = "escalateToAgent"

    fun parse(name: String, args: Map<String, String?>): ProposedAction? {
        fun text(key: String) = args[key]?.trim()?.takeIf { it.isNotEmpty() }
        fun number(key: String) = text(key)?.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toInt()

        return when (name) {
            LOOKUP_ORDER -> ProposedAction.LookupOrder(text("orderId"))
            CREATE_COMPLAINT -> ProposedAction.CreateComplaint(
                orderId = text("orderId"),
                category = text("category"),
                description = text("description"),
                itemId = text("itemId"),
                quantity = number("quantity"),
            )
            ISSUE_REFUND -> ProposedAction.IssueRefund(
                complaintId = text("complaintId"),
                orderId = text("orderId"),
                itemId = text("itemId"),
                quantity = number("quantity"),
            )
            ESCALATE_TO_AGENT -> ProposedAction.EscalateToAgent(
                reason = text("reason"),
                orderId = text("orderId"),
                complaintId = text("complaintId"),
            )
            else -> null
        }
    }
}
