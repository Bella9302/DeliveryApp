package com.itmda3b33.deliveryapp.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itmda3b33.deliveryapp.data.ComplaintRepository
import com.itmda3b33.deliveryapp.data.OrderRepository
import com.itmda3b33.deliveryapp.data.RefundRepository
import com.itmda3b33.deliveryapp.data.model.ChatMessage
import com.itmda3b33.deliveryapp.data.model.Complaint
import com.itmda3b33.deliveryapp.data.model.ComplaintStatus
import com.itmda3b33.deliveryapp.data.model.Money
import com.itmda3b33.deliveryapp.data.model.Order
import com.itmda3b33.deliveryapp.data.model.Refund
import com.itmda3b33.deliveryapp.data.model.RefundStatus
import com.itmda3b33.deliveryapp.ui.common.runAction
import com.itmda3b33.deliveryapp.ui.common.toUserMessage
import com.itmda3b33.deliveryapp.validation.EditRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One escalated complaint with everything the agent needs to decide it. */
data class CaseFile(
    val complaint: Complaint,
    val order: Order?,
    val messages: List<ChatMessage>,
    val refunds: List<Refund>,
)

data class AgentState(
    val loading: Boolean = true,
    val queue: List<Complaint> = emptyList(),
    val case: CaseFile? = null,
    val error: String? = null,
    val notice: String? = null,
)

/**
 * The agent screen: complaints the bot handed over, with the saved chat. Agents approve or reject
 * refunds above the auto-approve limit, and their user ID is recorded as approvedBy.
 */
class AgentViewModel(
    private val agentId: String,
    private val complaints: ComplaintRepository,
    private val refunds: RefundRepository,
    private val orders: OrderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    fun canDecide(refund: Refund) = EditRules.agentCanDecide(refund)

    fun loadQueue() = load {
        val open = complaints.withStatus(listOf(ComplaintStatus.ESCALATED.value, ComplaintStatus.IN_PROGRESS.value))
        _state.update { it.copy(loading = false, queue = open) }
    }

    fun loadCase(complaintId: String) = load {
        val complaint = complaints.get(complaintId)
        if (complaint == null) {
            _state.update { it.copy(loading = false, error = "This complaint could not be found.") }
            return@load
        }
        val case = CaseFile(
            complaint = complaint,
            order = orders.get(complaint.orderId),
            messages = complaints.messages(complaintId),
            refunds = refunds.forComplaint(complaintId),
        )
        _state.update { it.copy(loading = false, case = case) }
    }

    /** Marks the complaint as being worked on so other agents can see it is taken. */
    fun take(complaint: Complaint) = runAction("Marked as in progress.", { finish(it, complaint.id) }) {
        complaints.updateStatus(complaint.id, ComplaintStatus.IN_PROGRESS.value)
    }

    fun approve(refund: Refund) = decide(refund, approve = true)

    fun reject(refund: Refund) = decide(refund, approve = false)

    /** Closes the complaint with a note the customer sees under My complaints. */
    fun resolve(complaint: Complaint, note: String): String? {
        if (note.isBlank()) return "Write a short note for the customer."
        runAction("Complaint resolved.", { finish(it, complaint.id) }) {
            complaints.updateStatus(complaint.id, ComplaintStatus.RESOLVED.value, note.trim())
        }
        return null
    }

    fun noticeShown() = _state.update { it.copy(notice = null) }

    private fun decide(refund: Refund, approve: Boolean) {
        if (!canDecide(refund)) return
        val status = if (approve) RefundStatus.APPROVED else RefundStatus.REJECTED
        val message = if (approve) "Refund of ${Money.format(refund.amountCents)} approved." else "Refund rejected."
        runAction(message, { finish(it, refund.complaintId) }) {
            refunds.decide(refund.id, status.value, agentId)
        }
    }

    private fun finish(message: String, complaintId: String) {
        _state.update { it.copy(notice = message) }
        loadCase(complaintId)
    }

    private fun load(block: suspend () -> Unit) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }
}
