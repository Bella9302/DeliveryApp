package com.itmda3b33.eats.validation

/**
 * The only actions the chatbot may propose (Systems Brief, section 5).
 * Every field is nullable because Gemini may leave things out; the well-formed check catches that.
 */
sealed interface ProposedAction {
    val functionName: String

    data class LookupOrder(val orderId: String?) : ProposedAction {
        override val functionName = "lookupOrder"
    }

    data class CreateComplaint(
        val orderId: String?,
        val category: String?,
        val description: String?,
        val items: List<ItemClaim>,
    ) : ProposedAction {
        override val functionName = "createComplaint"
    }

    /** No amount here on purpose: our code works it out. */
    data class IssueRefund(
        val complaintId: String?,
        val menuItemId: String?,
        val quantity: Int?,
    ) : ProposedAction {
        override val functionName = "issueRefund"
    }

    data class EscalateToAgent(
        val complaintId: String?,
        val orderId: String?,
        val reason: String?,
    ) : ProposedAction {
        override val functionName = "escalateToAgent"
    }

    /** Gemini asked for a function that does not exist. It is always refused. */
    data class Unknown(override val functionName: String) : ProposedAction
}

/** What happened to a proposed action. [summary] is shown in the chat and sent back to Gemini. */
sealed interface ActionOutcome {
    val functionName: String
    val summary: String

    /** The action ran. [data] is returned to Gemini so it can write a reply. */
    data class Done(
        override val functionName: String,
        override val summary: String,
        val data: Map<String, Any?> = emptyMap(),
    ) : ActionOutcome

    /** A rule refused the action. Nothing was saved. */
    data class Blocked(
        override val functionName: String,
        val failure: RuleFailure,
    ) : ActionOutcome {
        override val summary: String get() = failure.message
    }

    /** The complaint was handed to a human agent. */
    data class Escalated(
        override val functionName: String,
        override val summary: String,
        val complaintId: String,
    ) : ActionOutcome

    /** A technical error, for example no network. */
    data class Failed(
        override val functionName: String,
        override val summary: String,
    ) : ActionOutcome
}

/** Receives actions proposed by the chatbot, checks them and runs them. Implemented next to the data layer. */
interface ActionHandler {
    suspend fun handle(action: ProposedAction): ActionOutcome
}
