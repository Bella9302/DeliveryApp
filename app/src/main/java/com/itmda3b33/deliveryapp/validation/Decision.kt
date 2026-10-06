package com.itmda3b33.deliveryapp.validation

import com.itmda3b33.deliveryapp.data.model.Complaint
import com.itmda3b33.deliveryapp.data.model.Order
import com.itmda3b33.deliveryapp.data.model.Refund

/** The validation checks, in the order the Systems Brief lists them. */
enum class Check { OWNERSHIP, DELIVERY, DUPLICATE, WELL_FORMED, AMOUNT_AND_ESCALATION }

/** The result of validating one proposed action. */
sealed interface Decision {

    /** The action may run as proposed. */
    data object Allow : Decision

    /** A refund may run, and the system approves it. The amount is ours, not Gemini's. */
    data class AllowRefund(val amountCents: Long) : Decision

    /** The action must not run. [reason] is safe to show the customer. */
    data class Block(val check: Check, val reason: String) : Decision

    /** A human agent must decide. [suggestedRefundCents] is set when a refund is waiting for approval. */
    data class Escalate(
        val check: Check,
        val reason: String,
        val suggestedRefundCents: Long? = null,
    ) : Decision
}

/**
 * Everything the validator needs, loaded by [ActionGateway] before validating.
 * [order] and [complaint] are null when they do not exist or the customer may not read them.
 */
data class ValidationContext(
    val customerId: String,
    val order: Order?,
    val existingRefunds: List<Refund> = emptyList(),
    val complaint: Complaint? = null,
)
