package com.itmda3b33.eats.ai

import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool

/** The only four functions Gemini may request (Systems Brief, section 5). */
internal object ChatFunctions {

    const val LOOKUP_ORDER = "lookupOrder"
    const val CREATE_COMPLAINT = "createComplaint"
    const val ISSUE_REFUND = "issueRefund"
    const val ESCALATE_TO_AGENT = "escalateToAgent"

    private val lookupOrder = FunctionDeclaration(
        name = LOOKUP_ORDER,
        description = "Read one of the customer's orders with its status and items. Without orderId, lists the customer's recent orders.",
        parameters = mapOf(
            "orderId" to Schema.string(description = "The order ID. Leave out to list recent orders."),
        ),
        optionalParameters = listOf("orderId"),
    )

    private val createComplaint = FunctionDeclaration(
        name = CREATE_COMPLAINT,
        description = "Log a complaint against one of the customer's orders.",
        parameters = mapOf(
            "orderId" to Schema.string(description = "The order ID from lookupOrder."),
            "category" to Schema.enumeration(
                values = listOf("missing_item", "wrong_item", "late_delivery", "food_quality", "safety_health", "other"),
                description = "What the problem is about.",
            ),
            "description" to Schema.string(description = "A short summary of the problem in plain words."),
            "items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "menuItemId" to Schema.string(description = "The menuItemId from lookupOrder."),
                        "quantity" to Schema.integer(description = "How many of this item are affected."),
                    ),
                ),
                description = "The affected items. Required for missing_item.",
            ),
        ),
        optionalParameters = listOf("items"),
    )

    private val issueRefund = FunctionDeclaration(
        name = ISSUE_REFUND,
        description = "Propose a refund for one item in a logged complaint. The app checks the rules and works out the amount; you never set it.",
        parameters = mapOf(
            "complaintId" to Schema.string(description = "The complaint ID from createComplaint."),
            "menuItemId" to Schema.string(description = "The item to refund."),
            "quantity" to Schema.integer(description = "How many of the item to refund."),
        ),
    )

    private val escalateToAgent = FunctionDeclaration(
        name = ESCALATE_TO_AGENT,
        description = "Hand the conversation to a human agent. Always available. Saves the complaint and chat history.",
        parameters = mapOf(
            "complaintId" to Schema.string(description = "The complaint ID, if one was created."),
            "orderId" to Schema.string(description = "The order ID, if known."),
            "reason" to Schema.string(description = "Why a person is needed, in one sentence."),
        ),
        optionalParameters = listOf("complaintId", "orderId"),
    )

    val tool: Tool = Tool.functionDeclarations(listOf(lookupOrder, createComplaint, issueRefund, escalateToAgent))
}
