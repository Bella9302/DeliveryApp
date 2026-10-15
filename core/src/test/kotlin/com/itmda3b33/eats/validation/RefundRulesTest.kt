package com.itmda3b33.eats.validation

import com.itmda3b33.eats.data.model.ComplaintCategory
import com.itmda3b33.eats.data.model.ComplaintStatus
import com.itmda3b33.eats.data.model.OrderStatus
import com.itmda3b33.eats.data.model.RefundStatus
import com.itmda3b33.eats.validation.Fixtures.CUSTOMER
import com.itmda3b33.eats.validation.Fixtures.OTHER_CUSTOMER
import org.junit.Assert.assertEquals
import org.junit.Test

class RefundRulesTest {

    private fun evaluate(
        menuItemId: String? = "dip",
        quantity: Int? = 1,
        customer: String = CUSTOMER,
        order: com.itmda3b33.eats.data.model.Order = Fixtures.order(),
        complaint: com.itmda3b33.eats.data.model.Complaint = Fixtures.complaint(),
        refunds: List<com.itmda3b33.eats.data.model.Refund> = emptyList(),
        policy: RefundPolicy = RefundPolicy.DEFAULT,
    ) = RefundRules.evaluate(customer, order, complaint, menuItemId, quantity, refunds, policy)

    private fun blockedCode(decision: RefundDecision): String = (decision as RefundDecision.Blocked).failure.code

    @Test
    fun `missing item under R15 is refunded 50 percent automatically`() {
        assertEquals(RefundDecision.AutoApprove(amountCents = 600, claimValueCents = 1_200), evaluate())
    }

    @Test
    fun `amount is rounded half up to the cent`() {
        assertEquals(1, RefundPolicy.DEFAULT.autoRefundAmount(1)) // 0.5c -> 1c
        assertEquals(700, RefundPolicy.DEFAULT.autoRefundAmount(1_399))
    }

    @Test
    fun `claim value counts the quantity`() {
        // 2 x R12 = R24, which is in the R15 to R50 range.
        val decision = evaluate(quantity = 2)
        assertEquals("mid_range", (decision as RefundDecision.Escalate).code)
        assertEquals(2_400, decision.claimValueCents)
    }

    @Test
    fun `mid range escalates by default and auto refunds when the group chooses so`() {
        assertEquals("mid_range", (evaluate(menuItemId = "chips") as RefundDecision.Escalate).code)
        val auto = evaluate(menuItemId = "chips", policy = RefundPolicy(midRangeDecision = MidRangeDecision.AUTO_REFUND))
        assertEquals(RefundDecision.AutoApprove(1_750, 3_500), auto)
    }

    @Test
    fun `exactly R15 and exactly R50 are mid range`() {
        val policy = RefundPolicy()
        val order = Fixtures.order().copy(items = listOf(Fixtures.dip.copy(priceCents = 1_500), Fixtures.chips.copy(priceCents = 5_000)))
        assertEquals("mid_range", (evaluate(order = order, policy = policy) as RefundDecision.Escalate).code)
        assertEquals("mid_range", (evaluate(menuItemId = "chips", order = order, policy = policy) as RefundDecision.Escalate).code)
    }

    @Test
    fun `above R50 escalates`() {
        assertEquals("above_limit", (evaluate(menuItemId = "platter") as RefundDecision.Escalate).code)
    }

    @Test
    fun `safety and health always escalate even when cheap`() {
        val decision = evaluate(complaint = Fixtures.complaint(category = ComplaintCategory.SAFETY_HEALTH))
        assertEquals("safety_health", (decision as RefundDecision.Escalate).code)
    }

    @Test
    fun `other categories need a person`() {
        val decision = evaluate(complaint = Fixtures.complaint(category = ComplaintCategory.WRONG_ITEM))
        assertEquals("needs_review", (decision as RefundDecision.Escalate).code)
    }

    @Test
    fun `order of another customer is blocked by ownership`() {
        assertEquals("not_owner", blockedCode(evaluate(order = Fixtures.order(customerId = OTHER_CUSTOMER))))
        assertEquals("not_owner", blockedCode(evaluate(complaint = Fixtures.complaint(customerId = OTHER_CUSTOMER))))
        assertEquals("not_owner", blockedCode(evaluate(complaint = Fixtures.complaint(orderId = "order-9"))))
    }

    @Test
    fun `missing item on an undelivered order is blocked`() {
        assertEquals("not_delivered", blockedCode(evaluate(order = Fixtures.order(status = OrderStatus.OUT_FOR_DELIVERY))))
    }

    @Test
    fun `item already refunded cannot be refunded again`() {
        assertEquals("already_refunded", blockedCode(evaluate(refunds = listOf(Fixtures.refund("dip")))))
        assertEquals("already_refunded", blockedCode(evaluate(refunds = listOf(Fixtures.refund("dip", RefundStatus.REQUESTED)))))
    }

    @Test
    fun `a rejected refund does not count as a duplicate`() {
        val decision = evaluate(refunds = listOf(Fixtures.refund("dip", RefundStatus.REJECTED)))
        assertEquals(RefundDecision.AutoApprove(600, 1_200), decision)
    }

    @Test
    fun `badly formed requests are blocked`() {
        assertEquals("missing_item_id", blockedCode(evaluate(menuItemId = null)))
        assertEquals("item_not_in_order", blockedCode(evaluate(menuItemId = "pizza")))
        assertEquals("bad_quantity", blockedCode(evaluate(quantity = null)))
        assertEquals("bad_quantity", blockedCode(evaluate(quantity = 0)))
        assertEquals("quantity_too_high", blockedCode(evaluate(quantity = 3)))
        assertEquals("complaint_not_open", blockedCode(evaluate(complaint = Fixtures.complaint(status = ComplaintStatus.ESCALATED))))
    }

    @Test
    fun `checks run in the brief's order`() {
        // Wrong owner AND not delivered AND duplicate AND bad quantity: ownership is reported first.
        val everythingWrong = evaluate(
            order = Fixtures.order(status = OrderStatus.PREPARING, customerId = OTHER_CUSTOMER),
            refunds = listOf(Fixtures.refund("dip")),
            quantity = 0,
        )
        assertEquals(Check.OWNERSHIP, (everythingWrong as RefundDecision.Blocked).failure.check)

        val notDeliveredAndDuplicate = evaluate(order = Fixtures.order(status = OrderStatus.PREPARING), refunds = listOf(Fixtures.refund("dip")))
        assertEquals(Check.DELIVERY, (notDeliveredAndDuplicate as RefundDecision.Blocked).failure.check)

        val duplicateAndBadQuantity = evaluate(refunds = listOf(Fixtures.refund("dip")), quantity = 0)
        assertEquals(Check.DUPLICATE, (duplicateAndBadQuantity as RefundDecision.Blocked).failure.check)
    }
}
