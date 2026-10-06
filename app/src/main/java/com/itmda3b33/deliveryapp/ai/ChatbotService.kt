package com.itmda3b33.deliveryapp.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.itmda3b33.deliveryapp.data.model.ChatMessage
import com.itmda3b33.deliveryapp.validation.ActionHandler
import com.itmda3b33.deliveryapp.validation.ActionOutcome
import com.itmda3b33.deliveryapp.validation.ProposedAction
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** The bot's answer to one customer message. [escalatedComplaintId] is set once a person takes over. */
data class BotReply(val text: String, val escalatedComplaintId: String? = null)

/**
 * Talks to Gemini through Firebase AI Logic. Gemini understands the message and proposes function
 * calls; this class passes each proposal to [handler] (the validation layer) and sends the result
 * back so Gemini can write the reply. This class has no database access.
 *
 * @param contextOrderId the order the chat was opened from, if any.
 */
class ChatbotService(
    private val handler: ActionHandler,
    private val contextOrderId: String? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val chat: Chat = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content { text(SystemPrompt.TEXT) },
            tools = listOf(Tool.functionDeclarations(ChatFunctions.declarations)),
        )
        .startChat()

    private val transcript = mutableListOf<ChatMessage>()
    private var latestComplaintId: String? = null
    private var escalatedComplaintId: String? = null

    /** Sends one customer message and returns the bot's reply once all function calls are done. */
    suspend fun send(message: String): BotReply {
        record(ChatMessage.SENDER_CUSTOMER, message)
        if (escalatedComplaintId == null && HumanRequest.matches(message)) {
            return requestHuman("Customer asked for a human: \"$message\"")
        }

        var response = chat.sendMessage(withContext(message))
        var rounds = 0
        while (response.functionCalls.isNotEmpty()) {
            if (++rounds > MAX_FUNCTION_ROUNDS) {
                return requestHuman("The assistant could not finish the request.")
            }
            response = chat.sendMessage(runFunctionCalls(response))
        }

        val text = response.text?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "Sorry, I didn't catch that. Could you say it another way?"
        return reply(text)
    }

    /**
     * Hands the chat to a person straight away, without asking Gemini. Used by the
     * "Talk to a person" button and by [send] when the customer asks for a human.
     */
    suspend fun requestHuman(reason: String = "Customer asked for a human agent."): BotReply {
        escalatedComplaintId?.let { return reply(alreadyEscalatedText(it)) }
        val action = ProposedAction.EscalateToAgent(reason, contextOrderId, latestComplaintId)
        return when (val outcome = handler.handle(action, transcript.toList())) {
            is ActionOutcome.Escalated -> {
                remember(outcome)
                reply("I've passed this to a support agent. Your reference is ${outcome.complaintId}. You can follow it under My complaints.")
            }
            else -> reply("I couldn't reach a support agent just now. Please check your connection and tap \"Talk to a person\" again.")
        }
    }

    /** Runs every function call in [response] through the validation layer and packs the results for Gemini. */
    private suspend fun runFunctionCalls(response: GenerateContentResponse): Content {
        val parts = response.functionCalls.map { call ->
            val args = call.args.mapValues { (_, value) -> (value as? JsonPrimitive)?.contentOrNull }
            val action = FunctionCallParser.parse(call.name, args)
            val outcome = if (action == null) {
                ActionOutcome.Blocked("Unknown function ${call.name}.")
            } else {
                handler.handle(action, transcript.toList())
            }
            remember(outcome)
            FunctionResponsePart(call.name, buildJsonObject { outcome.fields.forEach { (k, v) -> put(k, v) } })
        }
        return content("function") { parts.forEach { part(it) } }
    }

    private fun remember(outcome: ActionOutcome) {
        outcome.fields["complaintId"]?.let { latestComplaintId = it }
        if (outcome is ActionOutcome.Escalated) escalatedComplaintId = outcome.complaintId
    }

    /** Tells Gemini which order the chat was opened from, on the first message only. */
    private fun withContext(message: String): String =
        if (contextOrderId != null && transcript.size == 1) {
            "[The customer opened this chat from order $contextOrderId.]\n$message"
        } else {
            message
        }

    private fun reply(text: String): BotReply {
        record(ChatMessage.SENDER_BOT, text)
        return BotReply(text, escalatedComplaintId)
    }

    private fun record(sender: String, text: String) {
        transcript += ChatMessage(sender = sender, text = text, sentAt = clock())
    }

    private fun alreadyEscalatedText(complaintId: String) =
        "A support agent already has your chat (reference $complaintId). You can follow it under My complaints."

    private companion object {
        const val MODEL_NAME = "gemini-2.5-flash"

        /** Stops a confused model from calling functions forever. */
        const val MAX_FUNCTION_ROUNDS = 5
    }
}
