package com.itmda3b33.eats.validation

import com.itmda3b33.eats.data.model.ComplaintCategory
import com.itmda3b33.eats.data.model.ComplaintStatus
import com.itmda3b33.eats.data.model.RefundStatus
import com.itmda3b33.eats.data.model.Role
import com.itmda3b33.eats.data.model.UserProfile
import com.itmda3b33.eats.data.model.computeStats
import com.itmda3b33.eats.data.model.formatRand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtherRulesTest {

    @Test
    fun `rand formatting matches the design`() {
        assertEquals("R79,00", formatRand(7_900))
        assertEquals("R0,05", formatRand(5))
        assertEquals("R1 250,50", formatRand(125_050))
        assertEquals("-R6,00", formatRand(-600))
    }

    @Test
    fun `menu prices are parsed in both styles`() {
        assertEquals(7_900L, MenuRules.parsePriceCents("79"))
        assertEquals(7_950L, MenuRules.parsePriceCents("79,5"))
        assertEquals(7_950L, MenuRules.parsePriceCents("R79.50"))
        assertNull(MenuRules.parsePriceCents("79,999"))
        assertNull(MenuRules.parsePriceCents("abc"))
    }

    @Test
    fun `menu input is checked`() {
        assertTrue(MenuRules.validateInput("Chips", "Large", "35,00").isValid)
        val bad = MenuRules.validateInput(" ", "", "0")
        assertEquals(setOf(MenuItemInput.FIELD_NAME, MenuItemInput.FIELD_PRICE), bad.errors.keys)
    }

    @Test
    fun `only the restaurant's own user or an admin edits a menu`() {
        val owner = UserProfile("u", "A", "B", "a@b.co", "", Role.RESTAURANT, restaurantId = "rest-1")
        assertNull(MenuRules.validateCanEdit(owner, "rest-1"))
        assertEquals("not_your_menu", MenuRules.validateCanEdit(owner, "rest-2")?.code)
        assertNull(MenuRules.validateCanEdit(owner.copy(role = Role.ADMIN, restaurantId = null), "rest-2"))
        assertEquals("not_your_menu", MenuRules.validateCanEdit(owner.copy(role = Role.CUSTOMER), "rest-1")?.code)
    }

    @Test
    fun `auth fields are checked`() {
        assertNull(AuthRules.checkEmail("thandi@example.com"))
        assertTrue(AuthRules.checkEmail("thandi@") != null)
        assertTrue(AuthRules.checkPassword("short") != null)
        assertNull(AuthRules.checkMobile("082 123 4567"))
        assertNull(AuthRules.checkMobile("+27821234567"))
        assertTrue(AuthRules.checkMobile("12345") != null)
        assertEquals("+27821234567", AuthRules.normaliseMobile("082 123 4567"))
    }

    @Test
    fun `status moves follow the allowed paths`() {
        assertTrue(StatusRules.canMove(ComplaintStatus.LOGGED, ComplaintStatus.ESCALATED))
        assertTrue(StatusRules.canMove(ComplaintStatus.RESOLVED, ComplaintStatus.ESCALATED))
        assertFalse(StatusRules.canMove(ComplaintStatus.RESOLVED, ComplaintStatus.LOGGED))
        assertFalse(StatusRules.canMove(RefundStatus.REJECTED, RefundStatus.APPROVED))
        assertEquals("staff_only", StatusRules.validateStaffRefundMove(Role.CUSTOMER, RefundStatus.REQUESTED, RefundStatus.APPROVED)?.code)
        assertNull(StatusRules.validateStaffRefundMove(Role.AGENT, RefundStatus.REQUESTED, RefundStatus.APPROVED))
        assertEquals("bad_transition", StatusRules.validateStaffComplaintMove(Role.ADMIN, ComplaintStatus.RESOLVED, ComplaintStatus.IN_PROGRESS)?.code)
    }

    @Test
    fun `asking for a human is spotted`() {
        assertTrue(ConversationGuard.isHumanRequest("I want a HUMAN"))
        assertTrue(ConversationGuard.isHumanRequest("can i talk to someone please"))
        assertFalse(ConversationGuard.isHumanRequest("my chips r missing lol"))
    }

    @Test
    fun `guard escalates after two stuck turns in a row`() {
        val guard = ConversationGuard()
        val blocked = ActionOutcome.Blocked("issueRefund", RuleFailure(Check.DUPLICATE, "x", "x"))
        val done = ActionOutcome.Done("lookupOrder", "ok")
        guard.record(listOf(blocked), botFailed = false)
        assertFalse(guard.shouldEscalate)
        guard.record(listOf(done), botFailed = false)
        guard.record(emptyList(), botFailed = true)
        assertFalse(guard.shouldEscalate)
        guard.record(listOf(blocked), botFailed = false)
        assertTrue(guard.shouldEscalate)
    }

    @Test
    fun `stats count auto resolved complaints and refunds`() {
        val resolved = Fixtures.complaint(status = ComplaintStatus.RESOLVED)
        val escalated = Fixtures.complaint(status = ComplaintStatus.ESCALATED, category = ComplaintCategory.SAFETY_HEALTH).copy(id = "complaint-2")
        val agentRefund = Fixtures.refund("platter", RefundStatus.REQUESTED).copy(complaintId = "complaint-2", approvedBy = null, amountCents = 18_900)
        val stats = computeStats(listOf(resolved, escalated), listOf(Fixtures.refund("dip"), agentRefund))

        assertEquals(2, stats.totalComplaints)
        assertEquals(1, stats.autoResolved)
        assertEquals(1, stats.escalated)
        assertEquals(600L, stats.refundedCents)
        assertEquals(600L, stats.systemRefundedCents)
        assertEquals(0L, stats.agentRefundedCents)
        assertEquals(1, stats.refundsByStatus[RefundStatus.REQUESTED])
        assertEquals(0.5, stats.autoResolveRate, 0.0001)
    }
}
