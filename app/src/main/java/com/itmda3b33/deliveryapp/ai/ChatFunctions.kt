package com.itmda3b33.deliveryapp.ai

import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema
import com.itmda3b33.deliveryapp.ai.FunctionCallParser.CREATE_COMPLAINT
import com.itmda3b33.deliveryapp.ai.FunctionCallParser.ESCALATE_TO_AGENT
import com.itmda3b33.deliveryapp.ai.FunctionCallParser.ISSUE_REFUND
import com.itmda3b33.deliveryapp.ai.FunctionCallParser.LOOKUP_ORDER
import com.itmda3b33.deliveryapp.data.model.ComplaintCategory

/**
 * The only functions Gemini may request (Systems Brief, section 5). Note that issueRefund has
 * no amount parameter: our code works out the amount.
 */
object ChatFunctions {

    private val lookupOrder = FunctionDeclaration(
        name = LOOKUP_ORDER,
        description = "Read one of the customer's orders: its status, restaurant and items with itemId values.",
        parameters = mapOf("orderId" to Schema.string("The order ID the customer gave.")),
    )

    private val createComplaint = FunctionDeclaration(
        name = CREATE_COMPLAINT,
        description = "Log a complaint against one of the customer's orders.",
        parameters = mapOf(
            "orderId" to Schema.string("The order the complaint is about."),
            "category" to Schema.enumeration(
                ComplaintCategory.entries.map { it.value },
                "What kind of problem it is.",
            ),
            "description" to Schema.string("A short summary of the problem in the customer's words."),
            "itemId" to Schema.string("For missing items: the itemId from lookupOrder."),
            "quantity" to Schema.integer("For missing items: how many are missing."),
        ),
        optionalParameters = listOf("itemId", "quantity"),
    )

    private val issueRefund = FunctionDeclaration(
        name = ISSUE_REFUND,
        description = "Propose a refund for a missing item. The app decides whether it is allowed and how much.",
        parameters = mapOf(
            "complaintId" to Schema.string("The complaintId returned by createComplaint."),
            "orderId" to Schema.string("The order the item was missing from."),
            "itemId" to Schema.string("The itemId of the missing item."),
            "quantity" to Schema.integer("How many are missing."),
        ),
    )

    private val escalateToAgent = FunctionDeclaration(
        name = ESCALATE_TO_AGENT,
        description = "Hand the conversation to a human agent. Always available.",
        parameters = mapOf(
            "reason" to Schema.string("Why a person is needed."),
            "orderId" to Schema.string("The order involved, if known."),
            "complaintId" to Schema.string("The complaint involved, if one was logged."),
        ),
        optionalParameters = listOf("orderId", "complaintId"),
    )

    val declarations = listOf(lookupOrder, createComplaint, issueRefund, escalateToAgent)
}
