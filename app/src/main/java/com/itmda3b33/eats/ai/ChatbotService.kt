package com.itmda3b33.eats.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.itmda3b33.eats.validation.ActionHandler
import com.itmda3b33.eats.validation.ActionOutcome

/**
 * Talks to Gemini through Firebase AI Logic. This layer has no database access:
 * every function Gemini requests is handed to an [ActionHandler] in the validation layer.
 */
class ChatbotService(private val modelName: String = MODEL) {

    fun startSession(customerFirstName: String, startOrderId: String?, handler: ActionHandler): ChatbotSession {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            tools = listOf(ChatFunctions.tool),
            systemInstruction = content { text(SystemPrompt.build(customerFirstName, startOrderId)) },
        )
        return ChatbotSession(model.startChat(), handler)
    }

    companion object {
        /** Change this when Google retires the model. Gemini 3 models do not accept a temperature setting. */
        const val MODEL = "gemini-3.8-flash"
    }
}

/** What the bot said in one turn and every action it proposed along the way. */
data class BotTurn(
    val reply: String,
    val outcomes: List<ActionOutcome>,
    /** True when Gemini kept asking for functions past the limit. The app escalates. */
    val hitActionLimit: Boolean = false,
)

/** One conversation. Keeps Gemini's chat history between turns. */
class ChatbotSession internal constructor(
    private val chat: Chat,
    private val handler: ActionHandler,
) {

    /**
     * Sends the customer's message, runs any function calls through the handler and returns the final reply.
     * Throws when Gemini cannot be reached; the caller shows an error and offers a person.
     */
    suspend fun send(message: String): BotTurn {
        val outcomes = mutableListOf<ActionOutcome>()
        var response = chat.sendMessage(message)

        repeat(MAX_ACTION_ROUNDS) {
            val calls = response.functionCalls
            if (calls.isEmpty()) {
                return BotTurn(response.text?.trim().takeUnless { it.isNullOrEmpty() } ?: FALLBACK_REPLY, outcomes)
            }
            val replies = calls.map { call ->
                val outcome = handler.handle(FunctionCallParser.parse(call.name, call.args))
                outcomes += outcome
                // Passing the call ID back lets Gemini match each answer to its request.
                FunctionResponsePart(call.name, FunctionCallParser.toJson(outcome), call.id)
            }
            response = chat.sendMessage(content("function") { replies.forEach { part(it) } })
        }

        // Too many rounds in one turn: stop here so the customer is never stuck waiting.
        return BotTurn(response.text?.trim().takeUnless { it.isNullOrEmpty() } ?: FALLBACK_REPLY, outcomes, hitActionLimit = true)
    }

    private companion object {
        /** Enough for lookup, complaint and a few refunds in one turn. */
        const val MAX_ACTION_ROUNDS = 6
        const val FALLBACK_REPLY = "Sorry, I didn't quite get that. Could you say it another way, or tap Talk to a person?"
    }
}
