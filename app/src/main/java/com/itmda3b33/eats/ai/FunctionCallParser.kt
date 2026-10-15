package com.itmda3b33.eats.ai

import com.itmda3b33.eats.validation.ActionOutcome
import com.itmda3b33.eats.validation.ItemClaim
import com.itmda3b33.eats.validation.ProposedAction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** Turns Gemini's function calls into [ProposedAction]s and outcomes back into JSON. No checks happen here. */
internal object FunctionCallParser {

    fun parse(name: String, args: Map<String, JsonElement>): ProposedAction = when (name) {
        ChatFunctions.LOOKUP_ORDER -> ProposedAction.LookupOrder(args.string("orderId"))
        ChatFunctions.CREATE_COMPLAINT -> ProposedAction.CreateComplaint(
            orderId = args.string("orderId"),
            category = args.string("category"),
            description = args.string("description"),
            items = (args["items"] as? JsonArray).orEmpty().mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                ItemClaim(item.string("menuItemId"), item.int("quantity"))
            },
        )
        ChatFunctions.ISSUE_REFUND -> ProposedAction.IssueRefund(
            complaintId = args.string("complaintId"),
            menuItemId = args.string("menuItemId"),
            quantity = args.int("quantity"),
        )
        ChatFunctions.ESCALATE_TO_AGENT -> ProposedAction.EscalateToAgent(
            complaintId = args.string("complaintId"),
            orderId = args.string("orderId"),
            reason = args.string("reason"),
        )
        else -> ProposedAction.Unknown(name)
    }

    /** The function response Gemini reads to write its reply. */
    fun toJson(outcome: ActionOutcome): JsonObject {
        val fields = mutableMapOf<String, Any?>("message" to outcome.summary)
        when (outcome) {
            is ActionOutcome.Done -> {
                fields["result"] = "done"
                fields.putAll(outcome.data)
            }
            is ActionOutcome.Blocked -> {
                fields["result"] = "blocked"
                fields["check"] = outcome.failure.check.name.lowercase()
                fields["code"] = outcome.failure.code
            }
            is ActionOutcome.Escalated -> {
                fields["result"] = "escalated"
                fields["complaintId"] = outcome.complaintId
            }
            is ActionOutcome.Failed -> fields["result"] = "error"
        }
        return toElement(fields) as JsonObject
    }

    private fun toElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toElement(v) })
        is Iterable<*> -> JsonArray(value.map { toElement(it) })
        else -> JsonPrimitive(value.toString())
    }

    private fun Map<String, JsonElement>.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** Gemini sometimes sends 1 as 1.0, so read numbers as doubles and convert. */
    private fun Map<String, JsonElement>.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it % 1.0 == 0.0 }?.toInt()
}
