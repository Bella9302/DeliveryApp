package com.itmda3b33.deliveryapp.validation

import com.itmda3b33.deliveryapp.data.ComplaintRepository
import com.itmda3b33.deliveryapp.data.OrderRepository
import com.itmda3b33.deliveryapp.data.RefundRepository
import com.itmda3b33.deliveryapp.data.model.ChatMessage
import com.itmda3b33.deliveryapp.data.model.Complaint
import com.itmda3b33.deliveryapp.data.model.ComplaintCategory
import com.itmda3b33.deliveryapp.data.model.ComplaintStatus
import com.itmda3b33.deliveryapp.data.model.Money
import com.itmda3b33.deliveryapp.data.model.Order
import com.itmda3b33.deliveryapp.data.model.Refund
import com.itmda3b33.deliveryapp.data.model.RefundStatus
import com.itmda3b33.deliveryapp.validation.ProposedAction.CreateComplaint
import com.itmda3b33.deliveryapp.validation.ProposedAction.EscalateToAgent
import com.itmda3b33.deliveryapp.validation.ProposedAction.IssueRefund
import com.itmda3b33.deliveryapp.validation.ProposedAction.LookupOrder
import kotlinx.coroutines.CancellationException

/**
 * Step 5 of the message flow: every action Gemini proposes passes through here. The gateway loads
 * the records the rules need, asks [ActionValidator] for a decision, and then runs, blocks or
 * escalates the action. Refund amounts come only from the validator, never from Gemini.
 *
 * One gateway serves one customer's chat. It is created with the logged-in customer's ID, so
 * Gemini cannot act for anyone else.
 */
class ActionGateway(
    private val customerId: String,
    private val orders: OrderRepository,
    private val complaints: ComplaintRepository,
    private val refunds: RefundRepository,
    private val validator: ActionValidator = ActionValidator(),
    private val loopGuard: LoopGuard = LoopGuard(),
    private val clock: () -> Long = System::currentTimeMillis,
) : ActionHandler {

    override suspend fun handle(action: ProposedAction, transcript: List<ChatMessage>): ActionOutcome {
        val outcome = runSafely { execute(action, transcript) }
        if (outcome !is ActionOutcome.Blocked && outcome !is ActionOutcome.Failed) {
            loopGuard.reset()
            return outcome
        }
        if (!loopGuard.recordFailure()) return outcome

        // Too many failures in a row: hand over to a person so the customer is never stuck.
        val reason = when (outcome) {
            is ActionOutcome.Blocked -> "Customer was blocked repeatedly. Last reason: ${outcome.reason}"
            else -> "The assistant hit repeated errors."
        }
        loopGuard.reset()
        return runSafely { escalate(EscalateToAgent(reason, action.orderIdOrNull(), action.complaintIdOrNull()), transcript) }
    }

    private suspend fun execute(action: ProposedAction, transcript: List<ChatMessage>): ActionOutcome =
        when (action) {
            is LookupOrder -> lookupOrder(action)
            is CreateComplaint -> createComplaint(action, transcript)
            is IssueRefund -> issueRefund(action, transcript)
            is EscalateToAgent -> escalate(action, transcript)
        }

    private suspend fun lookupOrder(action: LookupOrder): ActionOutcome {
        val order = loadOrder(action.orderId)
        return when (val decision = validator.validate(action, ValidationContext(customerId, order))) {
            is Decision.Block -> ActionOutcome.Blocked(decision.reason)
            else -> ActionOutcome.Success("Order found.", describe(order!!))
        }
    }

    private suspend fun createComplaint(action: CreateComplaint, transcript: List<ChatMessage>): ActionOutcome {
        val order = loadOrder(action.orderId)
        val existing = if (order != null) refunds.forOrder(order.id, customerId) else emptyList()
        val decision = validator.validate(action, ValidationContext(customerId, order, existing))
        if (decision is Decision.Block) return ActionOutcome.Blocked(decision.reason)

        val now = clock()
        val escalate = decision is Decision.Escalate
        val complaint = Complaint(
            customerId = customerId,
            orderId = order!!.id,
            itemId = action.itemId.orEmpty(),
            quantity = action.quantity ?: 0,
            category = action.category!!,
            description = action.description!!.trim(),
            status = if (escalate) ComplaintStatus.ESCALATED.value else ComplaintStatus.LOGGED.value,
            createdAt = now,
            updatedAt = now,
        )
        val complaintId = complaints.create(complaint)

        if (decision is Decision.Escalate) {
            complaints.addMessages(complaintId, transcript)
            return ActionOutcome.Escalated(decision.reason, complaintId)
        }
        val next = if (action.category == ComplaintCategory.MISSING_ITEM.value) {
            "Now call issueRefund with this complaintId."
        } else {
            ""
        }
        return ActionOutcome.Success("Complaint logged. $next".trim(), mapOf("complaintId" to complaintId))
    }

    private suspend fun issueRefund(action: IssueRefund, transcript: List<ChatMessage>): ActionOutcome {
        val order = loadOrder(action.orderId)
        val complaint = action.complaintId?.takeIf { it.isNotBlank() }?.let { complaints.get(it) }
        val existing = if (order != null) refunds.forOrder(order.id, customerId) else emptyList()
        val decision = validator.validate(action, ValidationContext(customerId, order, existing, complaint))

        return when (decision) {
            is Decision.Block -> ActionOutcome.Blocked(decision.reason)

            is Decision.AllowRefund -> {
                refunds.create(newRefund(action, complaint!!, decision.amountCents, RefundStatus.APPROVED, Refund.APPROVED_BY_SYSTEM))
                val amount = Money.format(decision.amountCents)
                complaints.updateStatus(complaint.id, ComplaintStatus.RESOLVED.value, "Automatic refund of $amount approved.")
                ActionOutcome.Success(
                    "Refund approved.",
                    mapOf("refundAmount" to amount, "complaintId" to complaint.id),
                )
            }

            is Decision.Escalate -> {
                // The refund waits for an agent, who approves or rejects it on the agent screen.
                refunds.create(newRefund(action, complaint!!, decision.suggestedRefundCents ?: 0, RefundStatus.REQUESTED, null))
                complaints.updateStatus(complaint.id, ComplaintStatus.ESCALATED.value)
                complaints.addMessages(complaint.id, transcript)
                ActionOutcome.Escalated(decision.reason, complaint.id)
            }

            Decision.Allow -> error("A refund decision always carries an amount")
        }
    }

    /** Saves the chat for a human agent. Never blocked: "I want a human" must always work. */
    private suspend fun escalate(action: EscalateToAgent, transcript: List<ChatMessage>): ActionOutcome {
        val reason = action.reason?.takeIf { it.isNotBlank() } ?: "Customer asked for a human agent."
        val ownComplaint = action.complaintId?.takeIf { it.isNotBlank() }
            ?.let { complaints.get(it) }
            ?.takeIf { it.customerId == customerId }

        val complaintId = if (ownComplaint != null) {
            complaints.updateStatus(ownComplaint.id, ComplaintStatus.ESCALATED.value)
            ownComplaint.id
        } else {
            // Link the order only if it is the customer's own.
            val order = loadOrder(action.orderId)?.takeIf { it.customerId == customerId }
            val now = clock()
            complaints.create(
                Complaint(
                    customerId = customerId,
                    orderId = order?.id.orEmpty(),
                    category = ComplaintCategory.OTHER.value,
                    description = reason,
                    status = ComplaintStatus.ESCALATED.value,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        complaints.addMessages(complaintId, transcript)
        return ActionOutcome.Escalated(reason, complaintId)
    }

    private suspend fun loadOrder(orderId: String?): Order? =
        orderId?.trim()?.takeIf { it.isNotEmpty() }?.let { orders.get(it) }

    private fun newRefund(
        action: IssueRefund,
        complaint: Complaint,
        amountCents: Long,
        status: RefundStatus,
        approvedBy: String?,
    ) = Refund(
        complaintId = complaint.id,
        orderId = complaint.orderId,
        customerId = customerId,
        itemId = action.itemId!!,
        quantity = action.quantity!!,
        amountCents = amountCents,
        status = status.value,
        approvedBy = approvedBy,
        createdAt = clock(),
    )

    /** The order details Gemini needs to talk about the order and pick item IDs. */
    private fun describe(order: Order): Map<String, String> = mapOf(
        "orderId" to order.id,
        "restaurant" to order.restaurantName,
        "status" to order.status,
        "total" to Money.format(order.totalCents),
        "items" to order.items.joinToString("; ") {
            "itemId=${it.itemId}, ${it.quantity} x ${it.name} at ${Money.format(it.unitPriceCents)} each"
        },
    )

    private inline fun runSafely(block: () -> ActionOutcome): ActionOutcome = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ActionOutcome.Failed("Something went wrong on our side. Please try again.")
    }
}

private fun ProposedAction.orderIdOrNull(): String? = when (this) {
    is LookupOrder -> orderId
    is CreateComplaint -> orderId
    is IssueRefund -> orderId
    is EscalateToAgent -> orderId
}

private fun ProposedAction.complaintIdOrNull(): String? = when (this) {
    is IssueRefund -> complaintId
    is EscalateToAgent -> complaintId
    else -> null
}
