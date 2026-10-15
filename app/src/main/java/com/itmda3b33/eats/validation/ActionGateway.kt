package com.itmda3b33.eats.validation

import com.itmda3b33.eats.data.ComplaintRepository
import com.itmda3b33.eats.data.OrderRepository
import com.itmda3b33.eats.data.RefundRepository
import com.itmda3b33.eats.data.model.Complaint
import com.itmda3b33.eats.data.model.ComplaintCategory
import com.itmda3b33.eats.data.model.ComplaintSource
import com.itmda3b33.eats.data.model.ComplaintStatus
import com.itmda3b33.eats.data.model.Order
import com.itmda3b33.eats.data.model.Refund
import com.itmda3b33.eats.data.model.RefundStatus
import com.itmda3b33.eats.data.model.TranscriptLine
import com.itmda3b33.eats.data.model.formatRand
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Every action the chatbot proposes passes through here before it runs.
 * The gateway loads the records, applies the rules from [RefundRules] and [ComplaintRules],
 * and only then calls the data layer. Gemini never reaches the database directly.
 *
 * One gateway serves one chat. It remembers the order and complaint being discussed,
 * so "I want a human" works even when the bot never got that far.
 */
class ActionGateway(
    private val customerId: String,
    private val orders: OrderRepository,
    private val complaints: ComplaintRepository,
    private val refunds: RefundRepository,
    private val policy: RefundPolicy,
    /** The chat so far, saved with the complaint for the human agent. */
    private val transcript: () -> List<TranscriptLine>,
    startOrderId: String? = null,
) : ActionHandler {

    var activeOrderId: String? = startOrderId
        private set
    var activeComplaintId: String? = null
        private set

    private var savedLines = 0

    override suspend fun handle(action: ProposedAction): ActionOutcome =
        try {
            when (action) {
                is ProposedAction.LookupOrder -> lookupOrder(action)
                is ProposedAction.CreateComplaint -> createComplaint(action)
                is ProposedAction.IssueRefund -> issueRefund(action)
                is ProposedAction.EscalateToAgent -> escalate(action)
                is ProposedAction.Unknown -> ActionOutcome.Blocked(
                    action.functionName,
                    RuleFailure(Check.WELL_FORMED, "unknown_function", "That action is not available."),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionOutcome.Failed(action.functionName, friendlyError(e))
        }

    /** Saves chat lines that are not stored yet. Called after every turn once a complaint exists. */
    suspend fun syncTranscript() {
        val complaintId = activeComplaintId ?: return
        val lines = transcript()
        if (lines.size > savedLines) {
            complaints.appendMessages(complaintId, lines.subList(savedLines, lines.size))
            savedLines = lines.size
        }
    }

    private suspend fun lookupOrder(action: ProposedAction.LookupOrder): ActionOutcome {
        val name = action.functionName
        val orderId = action.orderId?.takeIf { it.isNotBlank() } ?: activeOrderId

        if (orderId == null) {
            // No order named yet: list recent ones so the customer can pick.
            val recent = orders.getRecentOrders(customerId)
            return ActionOutcome.Done(
                name,
                if (recent.isEmpty()) "No orders found on this account." else "Found ${recent.size} recent orders.",
                mapOf("recentOrders" to recent.map { orderSummary(it, includeItems = false) }),
            )
        }

        val order = loadOwnedOrder(orderId) ?: return notFound(name, "order")
        activeOrderId = order.id
        val refunded = refunds.getForOrder(customerId, order.id)
            .filter { it.status != RefundStatus.REJECTED }
            .map { it.menuItemId }
        return ActionOutcome.Done(
            name,
            "Order from ${order.restaurantName} is ${order.status.label.lowercase()}.",
            orderSummary(order, includeItems = true) + mapOf("alreadyRefundedItemIds" to refunded),
        )
    }

    private suspend fun createComplaint(action: ProposedAction.CreateComplaint): ActionOutcome {
        val name = action.functionName
        val orderId = action.orderId?.takeIf { it.isNotBlank() } ?: activeOrderId
            ?: return blocked(name, Check.WELL_FORMED, "missing_order", "Which order is this about?")
        val order = loadOwnedOrder(orderId) ?: return notFound(name, "order")
        val category = ComplaintCategory.from(action.category)
        val existing = complaints.getCustomerComplaintsForOrder(customerId, order.id)

        val items = ComplaintRules.validateCreate(customerId, order, category, action.description, action.items, existing)
            .getOrElse { return ActionOutcome.Blocked(name, (it as RuleException).failure) }

        val status = ComplaintRules.initialStatus(category!!)
        val complaintId = complaints.create(
            Complaint(
                id = "",
                customerId = customerId,
                orderId = order.id,
                restaurantId = order.restaurantId,
                category = category,
                description = action.description!!.trim(),
                items = items,
                status = status,
                source = ComplaintSource.CHAT,
            ),
        )
        activeOrderId = order.id
        activeComplaintId = complaintId
        syncTranscript()

        return if (status == ComplaintStatus.ESCALATED) {
            ActionOutcome.Escalated(name, "Safety and health issues go straight to a person. An agent has your complaint.", complaintId)
        } else {
            ActionOutcome.Done(
                name,
                "Complaint logged.",
                mapOf(
                    "complaintId" to complaintId,
                    "status" to status.value,
                    "category" to category.value,
                    "items" to items.map { mapOf("menuItemId" to it.menuItemId, "name" to it.name, "quantity" to it.quantity) },
                ),
            )
        }
    }

    private suspend fun issueRefund(action: ProposedAction.IssueRefund): ActionOutcome {
        val name = action.functionName
        val complaintId = action.complaintId?.takeIf { it.isNotBlank() } ?: activeComplaintId
            ?: return blocked(name, Check.WELL_FORMED, "missing_complaint", "A complaint must be logged before a refund.")
        val complaint = complaints.getComplaint(complaintId) ?: return notFound(name, "complaint")
        val orderId = complaint.orderId
            ?: return blocked(name, Check.WELL_FORMED, "missing_order", "This complaint is not linked to an order.")
        val order = loadOwnedOrder(orderId) ?: return notFound(name, "order")
        val existing = refunds.getForOrder(customerId, order.id)

        val decision = RefundRules.evaluate(customerId, order, complaint, action.menuItemId, action.quantity, existing, policy)
        activeComplaintId = complaint.id

        return when (decision) {
            is RefundDecision.Blocked -> ActionOutcome.Blocked(name, decision.failure)

            is RefundDecision.AutoApprove -> {
                val line = order.item(action.menuItemId)!!
                val amount = formatRand(decision.amountCents)
                // Resolve the complaint only once every item in it has a refund, so the next item can still be refunded.
                val refundedIds = existing.filter { it.status != RefundStatus.REJECTED }.map { it.menuItemId }.toSet() + line.menuItemId
                val allItemsRefunded = complaint.items.all { it.menuItemId in refundedIds }
                val refundId = refunds.createAutoRefund(
                    newRefund(complaint, order, line.menuItemId, line.name, action.quantity!!, decision.amountCents, RefundStatus.APPROVED, Refund.APPROVED_BY_SYSTEM),
                    resolution = if (allItemsRefunded) "Automatic refund of $amount for ${action.quantity} x ${line.name}." else null,
                )
                ActionOutcome.Done(
                    name,
                    "Refund of $amount approved.",
                    mapOf(
                        "refundId" to refundId,
                        "amount" to amount,
                        "refundStatus" to RefundStatus.APPROVED.value,
                        "complaintStatus" to (if (allItemsRefunded) ComplaintStatus.RESOLVED else complaint.status).value,
                        "note" to "Refunds are credited within 3 to 5 working days.",
                    ),
                )
            }

            is RefundDecision.Escalate -> {
                val line = order.item(action.menuItemId)!!
                refunds.createRequestAndEscalate(
                    newRefund(complaint, order, line.menuItemId, line.name, action.quantity!!, decision.claimValueCents, RefundStatus.REQUESTED, approvedBy = null),
                )
                syncTranscript()
                ActionOutcome.Escalated(name, "${decision.reason} An agent will review your refund.", complaint.id)
            }
        }
    }

    /** Always works: if there is no complaint yet, one is created so the agent has something to pick up. */
    private suspend fun escalate(action: ProposedAction.EscalateToAgent): ActionOutcome {
        val name = action.functionName
        val reason = action.reason?.trim()?.takeIf { it.length >= ComplaintRules.MIN_DESCRIPTION }
            ?: "Customer asked to speak to a person."
        val existing = (action.complaintId?.takeIf { it.isNotBlank() } ?: activeComplaintId)
            ?.let { complaints.getComplaint(it) }
            ?.takeIf { it.customerId == customerId }

        val complaintId = if (existing != null) {
            if (existing.status != ComplaintStatus.ESCALATED && StatusRules.canMove(existing.status, ComplaintStatus.ESCALATED)) {
                complaints.updateStatus(existing.id, ComplaintStatus.ESCALATED)
            }
            existing.id
        } else {
            // Link the order only if it really belongs to this customer; escalation must not fail because of it.
            val order = (action.orderId?.takeIf { it.isNotBlank() } ?: activeOrderId)?.let { loadOwnedOrder(it) }
            complaints.create(
                Complaint(
                    id = "",
                    customerId = customerId,
                    orderId = order?.id,
                    restaurantId = order?.restaurantId,
                    category = ComplaintCategory.OTHER,
                    description = reason,
                    items = emptyList(),
                    status = ComplaintStatus.ESCALATED,
                    source = ComplaintSource.CHAT,
                ),
            )
        }
        activeComplaintId = complaintId
        syncTranscript()
        return ActionOutcome.Escalated(name, "You're now in the queue for a human agent. They will reply in your complaint.", complaintId)
    }

    /** Ownership check: an order that is missing or belongs to someone else looks the same to the customer. */
    private suspend fun loadOwnedOrder(orderId: String): Order? =
        orders.getOrder(orderId)?.takeIf { it.customerId == customerId }

    private fun notFound(functionName: String, what: String) =
        blocked(functionName, Check.OWNERSHIP, "${what}_not_found", "I couldn't find that $what on your account.")

    private fun blocked(functionName: String, check: Check, code: String, message: String) =
        ActionOutcome.Blocked(functionName, RuleFailure(check, code, message))

    private fun newRefund(
        complaint: Complaint,
        order: Order,
        menuItemId: String,
        itemName: String,
        quantity: Int,
        amountCents: Long,
        status: RefundStatus,
        approvedBy: String?,
    ) = Refund(
        id = "",
        complaintId = complaint.id,
        orderId = order.id,
        customerId = customerId,
        menuItemId = menuItemId,
        itemName = itemName,
        quantity = quantity,
        amountCents = amountCents,
        status = status,
        approvedBy = approvedBy,
        reason = complaint.category.label,
    )

    private fun orderSummary(order: Order, includeItems: Boolean): Map<String, Any?> {
        val summary = mutableMapOf<String, Any?>(
            "orderId" to order.id,
            "restaurant" to order.restaurantName,
            "status" to order.status.value,
            "placed" to dateFormat.format(Date(order.createdAtMillis)),
            "total" to formatRand(order.totalCents),
        )
        if (includeItems) {
            summary["items"] = order.items.map {
                mapOf("menuItemId" to it.menuItemId, "name" to it.name, "quantity" to it.quantity, "unitPrice" to formatRand(it.priceCents))
            }
        } else {
            summary["itemNames"] = order.items.joinToString { "${it.quantity} x ${it.name}" }
        }
        return summary
    }

    private companion object {
        val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.UK)
    }
}
