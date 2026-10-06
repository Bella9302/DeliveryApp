package com.itmda3b33.deliveryapp.validation

import com.itmda3b33.deliveryapp.data.model.ChatMessage

/**
 * An action Gemini asked for. These are only proposals: nothing runs until [ActionGateway] has
 * validated it. Fields are nullable because Gemini can leave arguments out; the well-formed check
 * catches that.
 */
sealed interface ProposedAction {

    /** Read an order and its status. */
    data class LookupOrder(val orderId: String?) : ProposedAction

    /** Log a complaint against an order. [itemId] and [quantity] are needed for missing items. */
    data class CreateComplaint(
        val orderId: String?,
        val category: String?,
        val description: String?,
        val itemId: String? = null,
        val quantity: Int? = null,
    ) : ProposedAction

    /** Propose a refund for a missing item. There is no amount: our code works it out. */
    data class IssueRefund(
        val complaintId: String?,
        val orderId: String?,
        val itemId: String?,
        val quantity: Int?,
    ) : ProposedAction

    /** Hand the conversation to a human agent. Always available. */
    data class EscalateToAgent(
        val reason: String?,
        val orderId: String? = null,
        val complaintId: String? = null,
    ) : ProposedAction
}

/** What happened to a proposed action. This is sent back to Gemini so it can explain it to the customer. */
sealed interface ActionOutcome {
    val fields: Map<String, String>

    data class Success(val message: String, val details: Map<String, String> = emptyMap()) : ActionOutcome {
        override val fields get() = mapOf("outcome" to "success", "message" to message) + details
    }

    data class Blocked(val reason: String) : ActionOutcome {
        override val fields get() = mapOf("outcome" to "blocked", "reason" to reason)
    }

    data class Escalated(val reason: String, val complaintId: String) : ActionOutcome {
        override val fields
            get() = mapOf(
                "outcome" to "escalated",
                "reason" to reason,
                "complaintId" to complaintId,
                "message" to "A human agent will take over. Tell the customer their reference number.",
            )
    }

    /** A technical problem such as no network. Not the customer's fault. */
    data class Failed(val message: String) : ActionOutcome {
        override val fields get() = mapOf("outcome" to "error", "message" to message)
    }
}

/**
 * Runs proposed actions. The ai package calls this but never sees the database.
 * [transcript] is the chat so far, saved for the agent when the chat is escalated.
 */
interface ActionHandler {
    suspend fun handle(action: ProposedAction, transcript: List<ChatMessage>): ActionOutcome
}
