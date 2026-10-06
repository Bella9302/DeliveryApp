package com.itmda3b33.deliveryapp.validation

import com.itmda3b33.deliveryapp.data.ComplaintRepository
import com.itmda3b33.deliveryapp.data.OrderRepository
import com.itmda3b33.deliveryapp.data.RefundRepository
import com.itmda3b33.deliveryapp.data.model.ChatMessage
import com.itmda3b33.deliveryapp.data.model.Complaint
import com.itmda3b33.deliveryapp.data.model.ComplaintCategory
import com.itmda3b33.deliveryapp.data.model.ComplaintStatus
import com.itmda3b33.deliveryapp.data.model.Order
import com.itmda3b33.deliveryapp.data.model.OrderStatus
import com.itmda3b33.deliveryapp.data.model.Refund
import com.itmda3b33.deliveryapp.data.model.RefundStatus
import com.itmda3b33.deliveryapp.validation.TestData.CUSTOMER
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionGatewayTest {

    private val orders = FakeOrders()
    private val complaints = FakeComplaints()
    private val refunds = FakeRefunds()
    private val gateway = ActionGateway(CUSTOMER, orders, complaints, refunds, clock = { 1_000L })
    private val transcript = listOf(ChatMessage(sender = ChatMessage.SENDER_CUSTOMER, text = "my chips r missing lol"))

    private suspend fun logMissing(itemId: String = "chips", quantity: Int = 1): String {
        val outcome = gateway.handle(
            ProposedAction.CreateComplaint("order-1", ComplaintCategory.MISSING_ITEM.value, "chips missing", itemId, quantity),
            transcript,
        )
        return (outcome as ActionOutcome.Success).details.getValue("complaintId")
    }

    @Test
    fun `small missing item is refunded by the system and the complaint is resolved`() = runTest {
        val complaintId = logMissing()
        val outcome = gateway.handle(ProposedAction.IssueRefund(complaintId, "order-1", "chips", 1), transcript)

        assertEquals("R6.00", (outcome as ActionOutcome.Success).details["refundAmount"])
        val saved = refunds.saved.single()
        assertEquals(RefundStatus.APPROVED.value, saved.status)
        assertEquals(Refund.APPROVED_BY_SYSTEM, saved.approvedBy)
        assertEquals(6_00L, saved.amountCents)
        assertEquals(ComplaintStatus.RESOLVED.value, complaints.byId.getValue(complaintId).status)
    }

    @Test
    fun `large missing item waits for an agent and the chat is saved`() = runTest {
        val complaintId = logMissing(itemId = "burger")
        val outcome = gateway.handle(ProposedAction.IssueRefund(complaintId, "order-1", "burger", 1), transcript)

        assertTrue(outcome is ActionOutcome.Escalated)
        val saved = refunds.saved.single()
        assertEquals(RefundStatus.REQUESTED.value, saved.status)
        assertEquals(null, saved.approvedBy)
        assertEquals(ComplaintStatus.ESCALATED.value, complaints.byId.getValue(complaintId).status)
        assertEquals(transcript, complaints.messages.getValue(complaintId))
    }

    @Test
    fun `second refund for the same item is blocked`() = runTest {
        val complaintId = logMissing()
        gateway.handle(ProposedAction.IssueRefund(complaintId, "order-1", "chips", 1), transcript)
        val again = gateway.handle(ProposedAction.IssueRefund(complaintId, "order-1", "chips", 1), transcript)

        assertTrue(again is ActionOutcome.Blocked)
        assertEquals(1, refunds.saved.size)
    }

    @Test
    fun `two blocked actions in a row hand the chat to a person`() = runTest {
        val first = gateway.handle(ProposedAction.LookupOrder("someone-elses-order"), transcript)
        val second = gateway.handle(ProposedAction.LookupOrder("still-wrong"), transcript)

        assertTrue(first is ActionOutcome.Blocked)
        assertTrue(second is ActionOutcome.Escalated)
        val complaint = complaints.byId.values.single()
        assertEquals(ComplaintStatus.ESCALATED.value, complaint.status)
        assertEquals(transcript, complaints.messages.getValue(complaint.id))
    }

    @Test
    fun `asking for a human works without an order`() = runTest {
        val outcome = gateway.handle(ProposedAction.EscalateToAgent("I want a human"), transcript)

        assertTrue(outcome is ActionOutcome.Escalated)
        assertEquals("", complaints.byId.values.single().orderId)
    }

    @Test
    fun `escalation does not link another customer's order`() = runTest {
        orders.byId["theirs"] = TestData.order().copy(id = "theirs", customerId = TestData.OTHER_CUSTOMER)
        gateway.handle(ProposedAction.EscalateToAgent("help", orderId = "theirs"), transcript)

        assertEquals("", complaints.byId.values.single().orderId)
    }

    @Test
    fun `database errors become a friendly failure, not a crash`() = runTest {
        orders.failing = true
        val outcome = gateway.handle(ProposedAction.LookupOrder("order-1"), transcript)
        assertTrue(outcome is ActionOutcome.Failed)
    }

    // In-memory fakes of the data layer.

    private class FakeOrders : OrderRepository {
        val byId = mutableMapOf("order-1" to TestData.order(OrderStatus.DELIVERED))
        var failing = false
        override suspend fun get(orderId: String): Order? {
            if (failing) error("offline")
            return byId[orderId]
        }
        override suspend fun forCustomer(customerId: String) = byId.values.filter { it.customerId == customerId }
    }

    private class FakeComplaints : ComplaintRepository {
        val byId = linkedMapOf<String, Complaint>()
        val messages = mutableMapOf<String, List<ChatMessage>>()
        override suspend fun create(complaint: Complaint): String {
            val id = "complaint-${byId.size + 1}"
            byId[id] = complaint.copy(id = id)
            return id
        }
        override suspend fun get(complaintId: String) = byId[complaintId]
        override suspend fun forCustomer(customerId: String) = byId.values.filter { it.customerId == customerId }
        override suspend fun withStatus(statuses: List<String>) = byId.values.filter { it.status in statuses }
        override suspend fun all() = byId.values.toList()
        override suspend fun updateStatus(complaintId: String, status: String, resolution: String?) {
            byId[complaintId] = byId.getValue(complaintId).copy(status = status, resolution = resolution ?: "")
        }
        override suspend fun updateDescription(complaintId: String, description: String) {
            byId[complaintId] = byId.getValue(complaintId).copy(description = description)
        }
        override suspend fun delete(complaintId: String) {
            byId.remove(complaintId)
        }
        override suspend fun addMessages(complaintId: String, messages: List<ChatMessage>) {
            this.messages[complaintId] = this.messages[complaintId].orEmpty() + messages
        }
        override suspend fun messages(complaintId: String) = messages[complaintId].orEmpty()
    }

    private class FakeRefunds : RefundRepository {
        val saved = mutableListOf<Refund>()
        override suspend fun create(refund: Refund): String {
            saved += refund.copy(id = "refund-${saved.size + 1}")
            return saved.last().id
        }
        override suspend fun forOrder(orderId: String, customerId: String) =
            saved.filter { it.orderId == orderId && it.customerId == customerId }
        override suspend fun forComplaint(complaintId: String) = saved.filter { it.complaintId == complaintId }
        override suspend fun all() = saved.toList()
        override suspend fun decide(refundId: String, status: String, approvedBy: String) = Unit
    }
}
