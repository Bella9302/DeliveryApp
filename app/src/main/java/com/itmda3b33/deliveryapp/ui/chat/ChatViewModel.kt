package com.itmda3b33.deliveryapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itmda3b33.deliveryapp.ai.BotReply
import com.itmda3b33.deliveryapp.ai.ChatbotService
import com.itmda3b33.deliveryapp.data.model.ChatMessage
import com.itmda3b33.deliveryapp.ui.common.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(sender = ChatMessage.SENDER_BOT, text = "Hi! What went wrong with your order?"),
    ),
    val waiting: Boolean = false,
    val error: String? = null,
    /** Set once a person has taken over. The chat input is then closed. */
    val escalatedComplaintId: String? = null,
)

/** Holds the chat on screen and passes messages to the [ChatbotService]. */
class ChatViewModel(private val bot: ChatbotService) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.waiting) return
        _state.update {
            it.copy(messages = it.messages + ChatMessage(sender = ChatMessage.SENDER_CUSTOMER, text = message), error = null)
        }
        ask { bot.send(message) }
    }

    /** "Talk to a person" works at any time and does not wait for Gemini. */
    fun requestHuman() {
        if (_state.value.escalatedComplaintId != null) return
        _state.update { it.copy(error = null) }
        ask { bot.requestHuman() }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun ask(call: suspend () -> BotReply) {
        _state.update { it.copy(waiting = true) }
        viewModelScope.launch {
            try {
                val reply = call()
                _state.update {
                    it.copy(
                        messages = it.messages + ChatMessage(sender = ChatMessage.SENDER_BOT, text = reply.text),
                        waiting = false,
                        escalatedComplaintId = reply.escalatedComplaintId,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(waiting = false, error = "${e.toUserMessage()} You can also tap \"Talk to a person\".")
                }
            }
        }
    }
}
