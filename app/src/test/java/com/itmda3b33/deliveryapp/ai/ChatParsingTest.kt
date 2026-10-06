package com.itmda3b33.deliveryapp.ai

import com.itmda3b33.deliveryapp.validation.ProposedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatParsingTest {

    @Test
    fun `issueRefund is parsed without any amount`() {
        val action = FunctionCallParser.parse(
            "issueRefund",
            mapOf("complaintId" to "c1", "orderId" to "o1", "itemId" to "chips", "quantity" to "2.0", "amount" to "999"),
        )
        assertEquals(ProposedAction.IssueRefund("c1", "o1", "chips", 2), action)
    }

    @Test
    fun `unknown functions are rejected`() {
        assertNull(FunctionCallParser.parse("deleteDatabase", emptyMap()))
    }

    @Test
    fun `blank and fractional arguments become null`() {
        val action = FunctionCallParser.parse("createComplaint", mapOf("orderId" to "  ", "quantity" to "1.5"))
        assertEquals(ProposedAction.CreateComplaint(null, null, null, null, null), action)
    }

    @Test
    fun `requests for a human are recognised`() {
        listOf(
            "I want a human",
            "can i speak to someone",
            "let me talk to an agent pls",
            "get me your manager",
            "REAL PERSON",
        ).forEach { assertTrue(it, HumanRequest.matches(it)) }
    }

    @Test
    fun `ordinary complaints are not mistaken for human requests`() {
        listOf(
            "my chips r missing lol",
            "the burger was cold",
            "order 123 never arrived",
        ).forEach { assertFalse(it, HumanRequest.matches(it)) }
    }
}
