package com.itmda3b33.deliveryapp.validation

import com.itmda3b33.deliveryapp.data.model.ComplaintStatus
import com.itmda3b33.deliveryapp.data.model.Money
import com.itmda3b33.deliveryapp.data.model.Refund
import com.itmda3b33.deliveryapp.data.model.RefundStatus
import com.itmda3b33.deliveryapp.data.model.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    @Test
    fun `refund policy rounds down to the cent`() {
        assertEquals(6_49L, RefundPolicy().refundFor(12_99))
    }

    @Test
    fun `loop guard hands over after two failures in a row`() {
        val guard = LoopGuard()
        assertFalse(guard.recordFailure())
        assertTrue(guard.recordFailure())
    }

    @Test
    fun `loop guard starts counting again after a success`() {
        val guard = LoopGuard()
        guard.recordFailure()
        guard.reset()
        assertFalse(guard.recordFailure())
    }

    @Test
    fun `customers can edit only logged complaints`() {
        assertTrue(EditRules.customerCanEdit(TestData.complaint()))
        assertFalse(EditRules.customerCanEdit(TestData.complaint().copy(status = ComplaintStatus.ESCALATED.value)))
    }

    @Test
    fun `agents decide only requested refunds`() {
        assertTrue(EditRules.agentCanDecide(TestData.refund("chips", RefundStatus.REQUESTED)))
        assertFalse(EditRules.agentCanDecide(TestData.refund("chips", RefundStatus.APPROVED)))
    }

    @Test
    fun `menu item form is checked`() {
        assertEquals(EditRules.MenuCheck.Valid(45_50), EditRules.checkMenuItem("Burger", "45.50"))
        assertTrue(EditRules.checkMenuItem("", "45") is EditRules.MenuCheck.Invalid)
        assertTrue(EditRules.checkMenuItem("Burger", "abc") is EditRules.MenuCheck.Invalid)
        assertTrue(EditRules.checkMenuItem("Burger", "0") is EditRules.MenuCheck.Invalid)
    }

    @Test
    fun `money is parsed and formatted in rand`() {
        assertEquals(15_50L, Money.parse("R15.5"))
        assertEquals(15_00L, Money.parse("15"))
        assertNull(Money.parse("15.555"))
        assertNull(Money.parse("-3"))
        assertEquals("R6.05", Money.format(6_05))
    }

    @Test
    fun `stats count only paid refunds and split system from agent approvals`() {
        val refunds = listOf(
            Refund(amountCents = 6_00, status = RefundStatus.APPROVED.value, approvedBy = Refund.APPROVED_BY_SYSTEM),
            Refund(amountCents = 32_50, status = RefundStatus.COMPLETED.value, approvedBy = "agent-1"),
            Refund(amountCents = 99_00, status = RefundStatus.REQUESTED.value),
            Refund(amountCents = 10_00, status = RefundStatus.REJECTED.value, approvedBy = "agent-1"),
        )
        val stats = Stats.from(listOf(TestData.complaint()), refunds)
        assertEquals(38_50L, stats.refundedCents)
        assertEquals(1, stats.autoRefunds)
        assertEquals(1, stats.agentRefunds)
        assertEquals(1, stats.refundsWaiting)
        assertEquals(1, stats.byStatus[ComplaintStatus.LOGGED])
    }
}
