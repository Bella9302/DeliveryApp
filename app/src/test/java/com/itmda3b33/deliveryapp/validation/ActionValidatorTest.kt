package com.itmda3b33.deliveryapp.validation

import com.itmda3b33.deliveryapp.data.model.ComplaintCategory
import com.itmda3b33.deliveryapp.data.model.OrderStatus
import com.itmda3b33.deliveryapp.data.model.RefundStatus
import com.itmda3b33.deliveryapp.validation.ProposedAction.CreateComplaint
import com.itmda3b33.deliveryapp.validation.ProposedAction.EscalateToAgent
import com.itmda3b33.deliveryapp.validation.ProposedAction.IssueRefund
import com.itmda3b33.deliveryapp.validation.ProposedAction.LookupOrder
import com.itmda3b33.deliveryapp.validation.TestData.CUSTOMER
import com.itmda3b33.deliveryapp.validation.TestData.OTHER_CUSTOMER
import com.itmda3b33.deliveryapp.validation.TestData.complaint
import com.itmda3b33.deliveryapp.validation.TestData.order
import com.itmda3b33.deliveryapp.validation.TestData.refund
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionValidatorTest {

    private val validator = ActionValidator()

    private fun refundCtx(
        status: OrderStatus = OrderStatus.DELIVERED,
        refunds: List<com.itmda3b33.deliveryapp.data.model.Refund> = emptyList(),
        itemId: String = "chips",
        category: ComplaintCategory = ComplaintCategory.MISSING_ITEM,
    ) = ValidationContext(CUSTOMER, order(status), refunds, complaint(itemId, category))

    private fun refund(itemId: String = "chips", quantity: Int? = 1) =
        IssueRefund(complaintId = "complaint-1", orderId = "order-1", itemId = itemId, quantity = quantity)

    private fun missingItem(itemId: String? = "chips", quantity: Int? = 1, description: String? = "my chips r missing lol") =
        CreateComplaint("order-1", ComplaintCategory.MISSING_ITEM.value, description, itemId, quantity)

    private fun assertBlockedBy(check: Check, decision: Decision) {
        assertTrue("Expected a block, got $decision", decision is Decision.Block)
        assertEquals(check, (decision as Decision.Block).check)
    }

    // Ownership

    @Test
    fun `lookup of own order is allowed`() {
        assertEquals(Decision.Allow, validator.validate(LookupOrder("order-1"), ValidationContext(CUSTOMER, order())))
    }

    @Test
    fun `lookup of another customer's order is blocked`() {
        val decision = validator.validate(LookupOrder("order-1"), ValidationContext(CUSTOMER, order(customerId = OTHER_CUSTOMER)))
        assertBlockedBy(Check.OWNERSHIP, decision)
    }

    @Test
    fun `lookup of an order that does not exist is blocked`() {
        assertBlockedBy(Check.OWNERSHIP, validator.validate(LookupOrder("nope"), ValidationContext(CUSTOMER, null)))
    }

    @Test
    fun `refund against another customer's complaint is blocked`() {
        val ctx = refundCtx().copy(complaint = complaint().copy(customerId = OTHER_CUSTOMER))
        assertBlockedBy(Check.OWNERSHIP, validator.validate(refund(), ctx))
    }

    // Delivery

    @Test
    fun `missing item on an order still out for delivery is blocked`() {
        val ctx = ValidationContext(CUSTOMER, order(OrderStatus.OUT_FOR_DELIVERY))
        assertBlockedBy(Check.DELIVERY, validator.validate(missingItem(), ctx))
        assertBlockedBy(Check.DELIVERY, validator.validate(refund(), refundCtx(OrderStatus.OUT_FOR_DELIVERY)))
    }

    @Test
    fun `late delivery complaint does not need a delivered order`() {
        val action = CreateComplaint("order-1", ComplaintCategory.LATE_DELIVERY.value, "Still waiting after 2 hours")
        val decision = validator.validate(action, ValidationContext(CUSTOMER, order(OrderStatus.OUT_FOR_DELIVERY)))
        assertTrue(decision is Decision.Escalate)
    }

    // Duplicate

    @Test
    fun `item already refunded cannot be refunded again`() {
        val approved = listOf(refund("chips", RefundStatus.APPROVED))
        assertBlockedBy(Check.DUPLICATE, validator.validate(refund(), refundCtx(refunds = approved)))
    }

    @Test
    fun `item with a refund waiting for an agent cannot be claimed again`() {
        val waiting = listOf(refund("chips", RefundStatus.REQUESTED))
        assertBlockedBy(Check.DUPLICATE, validator.validate(missingItem(), ValidationContext(CUSTOMER, order(), waiting)))
    }

    @Test
    fun `rejected refund does not count as a duplicate`() {
        val rejected = listOf(refund("chips", RefundStatus.REJECTED))
        assertTrue(validator.validate(refund(), refundCtx(refunds = rejected)) is Decision.AllowRefund)
    }

    @Test
    fun `refund on a different item is not a duplicate`() {
        val other = listOf(refund("burger", RefundStatus.APPROVED))
        assertTrue(validator.validate(refund(), refundCtx(refunds = other)) is Decision.AllowRefund)
    }

    // Well-formed

    @Test
    fun `missing item complaint without an item is blocked`() {
        assertBlockedBy(Check.WELL_FORMED, validator.validate(missingItem(itemId = null), ValidationContext(CUSTOMER, order())))
    }

    @Test
    fun `item that is not on the order is blocked`() {
        assertBlockedBy(Check.WELL_FORMED, validator.validate(missingItem(itemId = "pizza"), ValidationContext(CUSTOMER, order())))
    }

    @Test
    fun `more missing than were ordered is blocked`() {
        assertBlockedBy(Check.WELL_FORMED, validator.validate(missingItem(quantity = 3), ValidationContext(CUSTOMER, order())))
        assertBlockedBy(Check.WELL_FORMED, validator.validate(refund(quantity = 0), refundCtx()))
    }

    @Test
    fun `complaint without a description or with an unknown category is blocked`() {
        val ctx = ValidationContext(CUSTOMER, order())
        assertBlockedBy(Check.WELL_FORMED, validator.validate(missingItem(description = null), ctx))
        assertBlockedBy(Check.WELL_FORMED, validator.validate(CreateComplaint("order-1", "cold_vibes", "meh"), ctx))
    }

    @Test
    fun `refund without a complaint is blocked`() {
        val ctx = refundCtx().copy(complaint = null)
        assertBlockedBy(Check.WELL_FORMED, validator.validate(refund(), ctx))
    }

    @Test
    fun `refund for a different item than the complaint is blocked`() {
        assertBlockedBy(Check.WELL_FORMED, validator.validate(refund(itemId = "burger"), refundCtx(itemId = "chips")))
    }

    @Test
    fun `refund on a non missing item complaint is blocked`() {
        val ctx = refundCtx(category = ComplaintCategory.WRONG_ITEM)
        assertBlockedBy(Check.WELL_FORMED, validator.validate(refund(), ctx))
    }

    // Amount and escalation

    @Test
    fun `missing item under R15 is refunded 50 percent automatically`() {
        // One chips at R12: refund R6.
        assertEquals(Decision.AllowRefund(6_00), validator.validate(refund(), refundCtx()))
    }

    @Test
    fun `missing item worth exactly R15 is escalated`() {
        val decision = validator.validate(refund(itemId = "coke"), refundCtx(itemId = "coke"))
        assertTrue(decision is Decision.Escalate)
        assertEquals(7_50L, (decision as Decision.Escalate).suggestedRefundCents)
    }

    @Test
    fun `missing items between R15 and R50 are escalated`() {
        // Two chips at R12 = R24.
        val decision = validator.validate(refund(quantity = 2), refundCtx())
        assertTrue(decision is Decision.Escalate)
        assertEquals(Check.AMOUNT_AND_ESCALATION, (decision as Decision.Escalate).check)
    }

    @Test
    fun `missing item above R50 is escalated`() {
        val decision = validator.validate(refund(itemId = "burger"), refundCtx(itemId = "burger"))
        assertTrue(decision is Decision.Escalate)
    }

    @Test
    fun `middle band can be switched to automatic refunds`() {
        val lenient = ActionValidator(RefundPolicy(autoRefundMiddleBand = true))
        assertEquals(Decision.AllowRefund(12_00), lenient.validate(refund(quantity = 2), refundCtx()))
        assertTrue(lenient.validate(refund(itemId = "burger"), refundCtx(itemId = "burger")) is Decision.Escalate)
    }

    @Test
    fun `safety issue is escalated`() {
        val action = CreateComplaint("order-1", ComplaintCategory.SAFETY_HEALTH.value, "Found glass in my burger")
        assertTrue(validator.validate(action, ValidationContext(CUSTOMER, order())) is Decision.Escalate)
    }

    @Test
    fun `valid missing item complaint is allowed`() {
        assertEquals(Decision.Allow, validator.validate(missingItem(), ValidationContext(CUSTOMER, order())))
    }

    @Test
    fun `escalation is always allowed, even with no order`() {
        assertEquals(Decision.Allow, validator.validate(EscalateToAgent("I want a human"), ValidationContext(CUSTOMER, null)))
    }

    // Check order

    @Test
    fun `ownership is checked before delivery`() {
        val ctx = ValidationContext(CUSTOMER, order(OrderStatus.PREPARING, customerId = OTHER_CUSTOMER))
        assertBlockedBy(Check.OWNERSHIP, validator.validate(missingItem(), ctx))
    }

    @Test
    fun `duplicate is checked before amount`() {
        val approved = listOf(refund("burger", RefundStatus.APPROVED))
        assertBlockedBy(Check.DUPLICATE, validator.validate(refund(itemId = "burger"), refundCtx(refunds = approved, itemId = "burger")))
    }
}
