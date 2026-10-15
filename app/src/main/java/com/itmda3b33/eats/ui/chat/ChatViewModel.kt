package com.itmda3b33.eats.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itmda3b33.eats.AppContainer
import com.itmda3b33.eats.ai.ChatbotSession
import com.itmda3b33.eats.data.model.MessageSender
import com.itmda3b33.eats.data.model.TranscriptLine
import com.itmda3b33.eats.data.model.UserProfile
import com.itmda3b33.eats.validation.ActionOutcome
import com.itmda3b33.eats.validation.ConversationGuard
import com.itmda3b33.eats.validation.ProposedAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One bubble in the chat. [outcomes] are shown as small cards under a bot message. */
data class ChatBubble(
    val id: Int,
    val sender: MessageSender,
    val text: String,
    val outcomes: List<ActionOutcome> = emptyList(),
)

data class ChatState(
    val bubbles: List<ChatBubble> = emptyList(),
    val sending: Boolean = false,
    /** Set once the chat is with a human agent. */
    val escalatedComplaintId: String? = null,
    /** The complaint created during this chat, if any. */
    val complaintId: String? = null,
)

/**
 * Runs a support chat. The UI layer only shows state: Gemini lives in [ChatbotSession],
 * and every action goes through the validation layer's ActionGateway.
 *
 * Never stuck: "I want a human" escalates without asking Gemini, Gemini errors offer a person,
 * and two stuck turns in a row escalate automatically.
 */
class ChatViewModel(container: AppContainer, profile: UserProfile, startOrderId: String?) : ViewModel() {

    private val transcript = mutableListOf<TranscriptLine>()
    private val gateway = container.newActionGateway(profile.uid, startOrderId) { transcript.toList() }
    private val bot: ChatbotSession = container.chatbotService.startSession(profile.firstName, startOrderId, gateway)
    private val guard = ConversationGuard()
    private var nextId = 0

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        val about = if (startOrderId != null) " about this order" else ""
        addBubble(
            MessageSender.BOT,
            "Hi ${profile.firstName}, I'm the eats assistant. Tell me what went wrong$about. You can ask for a person at any time.",
        )
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.sending) return
        addBubble(MessageSender.CUSTOMER, message)

        // Already with a person: just save the message for the agent.
        if (_state.value.escalatedComplaintId != null) {
            launchSending { gateway.syncTranscript() }
            return
        }
        if (ConversationGuard.isHumanRequest(message)) {
            talkToPerson("Customer asked to speak to a person.")
            return
        }

        launchSending {
            val turn = try {
                bot.send(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            if (turn == null) {
                addBubble(MessageSender.BOT, "Sorry, I can't reach the assistant right now. Try again, or tap Talk to a person.")
                guard.record(emptyList(), botFailed = true)
            } else {
                addBubble(MessageSender.BOT, turn.reply, turn.outcomes)
                guard.record(turn.outcomes, botFailed = turn.hitActionLimit)
                turn.outcomes.filterIsInstance<ActionOutcome.Escalated>().lastOrNull()?.let { escalated ->
                    _state.update { it.copy(escalatedComplaintId = escalated.complaintId) }
                }
            }
            _state.update { it.copy(complaintId = gateway.activeComplaintId) }

            if (_state.value.escalatedComplaintId == null && guard.shouldEscalate) {
                escalate("This isn't getting sorted, so I'm passing you to a person.", "Bot could not resolve the problem after several tries.")
            }
            gateway.syncTranscript()
        }
    }

    /** The "Talk to a person" button and "I want a human". Always available, does not depend on Gemini. */
    fun talkToPerson(reason: String = "Customer asked to speak to a person.") {
        if (_state.value.escalatedComplaintId != null) return
        launchSending { escalate(null, reason) }
    }

    private suspend fun escalate(introText: String?, reason: String) {
        introText?.let { addBubble(MessageSender.BOT, it) }
        val outcome = gateway.handle(ProposedAction.EscalateToAgent(complaintId = null, orderId = null, reason = reason))
        if (outcome is ActionOutcome.Escalated) {
            addBubble(MessageSender.SYSTEM, outcome.summary, listOf(outcome))
            _state.update { it.copy(escalatedComplaintId = outcome.complaintId, complaintId = outcome.complaintId) }
            guard.reset()
        } else {
            addBubble(MessageSender.SYSTEM, "Couldn't reach a person: ${outcome.summary} Please tap Talk to a person again.", listOf(outcome))
        }
    }

    private fun launchSending(block: suspend () -> Unit) {
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addBubble(MessageSender.SYSTEM, "Something went wrong saving the chat. Check your connection.")
            } finally {
                _state.update { it.copy(sending = false) }
            }
        }
    }

    private fun addBubble(sender: MessageSender, text: String, outcomes: List<ActionOutcome> = emptyList()) {
        transcript += TranscriptLine(sender, text)
        // Outcomes are saved too, so the agent sees exactly what the rules decided.
        outcomes.forEach { transcript += TranscriptLine(MessageSender.SYSTEM, "${it.functionName}: ${it.summary}") }
        _state.update { it.copy(bubbles = it.bubbles + ChatBubble(nextId++, sender, text, outcomes)) }
    }
}
