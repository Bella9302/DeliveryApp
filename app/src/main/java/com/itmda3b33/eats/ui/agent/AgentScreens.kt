package com.itmda3b33.eats.ui.agent

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.itmda3b33.eats.AppContainer
import com.itmda3b33.eats.data.model.Complaint
import com.itmda3b33.eats.data.model.ComplaintMessage
import com.itmda3b33.eats.data.model.ComplaintStatus
import com.itmda3b33.eats.data.model.MessageSender
import com.itmda3b33.eats.data.model.Order
import com.itmda3b33.eats.data.model.Refund
import com.itmda3b33.eats.data.model.RefundStatus
import com.itmda3b33.eats.data.model.UserProfile
import com.itmda3b33.eats.data.model.formatRand
import com.itmda3b33.eats.ui.common.Caption
import com.itmda3b33.eats.ui.common.EatsCard
import com.itmda3b33.eats.ui.common.EatsScaffold
import com.itmda3b33.eats.ui.common.EatsTextField
import com.itmda3b33.eats.ui.common.EmptyView
import com.itmda3b33.eats.ui.common.LinkButton
import com.itmda3b33.eats.ui.common.MessageEffect
import com.itmda3b33.eats.ui.common.PrimaryButton
import com.itmda3b33.eats.ui.common.SecondaryButton
import com.itmda3b33.eats.ui.common.StateContent
import com.itmda3b33.eats.ui.common.UiState
import com.itmda3b33.eats.ui.common.asUiState
import com.itmda3b33.eats.ui.common.eatsViewModel
import com.itmda3b33.eats.ui.common.shortId
import com.itmda3b33.eats.ui.customer.ComplaintCard
import com.itmda3b33.eats.ui.customer.ComplaintThread
import com.itmda3b33.eats.validation.ServiceResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AgentQueueViewModel(container: AppContainer) : ViewModel() {
    private val _filter = MutableStateFlow(ComplaintStatus.ESCALATED)
    val filter: StateFlow<ComplaintStatus> = _filter.asStateFlow()

    val state: StateFlow<UiState<List<Complaint>>> =
        _filter.flatMapLatest { container.complaintRepository.observeByStatus(listOf(it)) }.asUiState(viewModelScope)

    fun setFilter(status: ComplaintStatus) {
        _filter.value = status
    }
}

/** Agent and admin home: complaints waiting for a person, oldest first. */
@Composable
fun AgentQueueScreen(onOpen: (String) -> Unit, onSignOut: () -> Unit) {
    val vm = eatsViewModel { AgentQueueViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()

    EatsScaffold(title = "Complaint queue", actions = { LinkButton("Sign out", onClick = onSignOut) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(ComplaintStatus.ESCALATED, ComplaintStatus.IN_PROGRESS, ComplaintStatus.LOGGED, ComplaintStatus.RESOLVED).forEach { status ->
                    FilterChip(selected = filter == status, onClick = { vm.setFilter(status) }, label = { Text(status.label) })
                }
            }
            StateContent(state) { complaints ->
                if (complaints.isEmpty()) {
                    EmptyView("Nothing here. Nice work.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(complaints, key = { it.id }) { complaint -> ComplaintCard(complaint, onClick = { onOpen(complaint.id) }) }
                    }
                }
            }
        }
    }
}

data class AgentComplaintDetail(
    val complaint: Complaint?,
    val order: Order?,
    val messages: List<ComplaintMessage>,
    val refunds: List<Refund>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AgentComplaintViewModel(
    private val container: AppContainer,
    private val staff: UserProfile,
    complaintId: String,
) : ViewModel() {

    private val complaintFlow = container.complaintRepository.observeComplaint(complaintId)

    val state: StateFlow<UiState<AgentComplaintDetail>> = combine(
        complaintFlow,
        complaintFlow.flatMapLatest { c -> c?.orderId?.let { container.orderRepository.observeOrder(it) } ?: flowOf(null) },
        container.complaintRepository.observeMessages(complaintId),
        container.refundRepository.observeForComplaint(complaintId),
    ) { complaint, order, messages, refunds -> AgentComplaintDetail(complaint, order, messages, refunds) }
        .asUiState(viewModelScope)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun take(c: Complaint) = perform("You're handling this complaint.") { container.staffService.take(staff, c) }
    fun resolve(c: Complaint, resolution: String, done: () -> Unit) = perform("Complaint resolved.") {
        container.staffService.resolve(staff, c, resolution).also { if (it is ServiceResult.Success) done() }
    }
    fun approve(r: Refund) = perform("Refund approved.") { container.staffService.approveRefund(staff, r) }
    fun reject(r: Refund) = perform("Refund rejected.") { container.staffService.rejectRefund(staff, r) }
    fun complete(r: Refund) = perform("Refund marked as completed.") { container.staffService.completeRefund(staff, r) }
    fun reply(complaintId: String, text: String, clear: () -> Unit) = perform(null) {
        container.complaintService.reply(complaintId, MessageSender.AGENT, text).also { if (it is ServiceResult.Success) clear() }
    }

    private fun perform(success: String?, block: suspend () -> ServiceResult<Unit>) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            when (val r = block()) {
                is ServiceResult.Success -> success?.let { _messages.send(it) }
                is ServiceResult.Invalid -> _messages.send(r.failure.message)
                is ServiceResult.Error -> _messages.send(r.message)
            }
            _busy.value = false
        }
    }
}

/** An escalated complaint: chat history, order, refund decisions and replies to the customer. */
@Composable
fun AgentComplaintScreen(profile: UserProfile, complaintId: String, onBack: () -> Unit) {
    val vm = eatsViewModel(key = "agent-$complaintId") { AgentComplaintViewModel(it, profile, complaintId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm.messages, snackbar)
    var resolving by rememberSaveable { mutableStateOf(false) }

    EatsScaffold(title = "Complaint ${shortId(complaintId)}", onBack = onBack, snackbar = snackbar) { padding ->
        StateContent(state) { detail ->
            val complaint = detail.complaint
            if (complaint == null) {
                EmptyView("This complaint no longer exists.")
                return@StateContent
            }
            ComplaintThread(
                complaint = complaint,
                messages = detail.messages,
                refunds = detail.refunds,
                busy = busy,
                onReply = { text, clear -> vm.reply(complaint.id, text, clear) },
                modifier = Modifier.padding(padding),
                replyEnabled = complaint.status != ComplaintStatus.RESOLVED,
                header = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detail.order?.let { order ->
                            EatsCard {
                                Text("${order.restaurantName} • ${order.status.label}", style = MaterialTheme.typography.titleSmall)
                                Caption("Order ${shortId(order.id)} • ${formatRand(order.totalCents)}")
                                Caption(order.items.joinToString { "${it.quantity} x ${it.name} (${formatRand(it.priceCents)})" })
                            }
                        }
                        when (complaint.status) {
                            ComplaintStatus.ESCALATED, ComplaintStatus.LOGGED ->
                                PrimaryButton("Take this complaint", onClick = { vm.take(complaint) }, loading = busy)
                            ComplaintStatus.IN_PROGRESS ->
                                PrimaryButton("Resolve", onClick = { resolving = true }, loading = busy)
                            ComplaintStatus.RESOLVED -> Unit
                        }
                        if (complaint.status == ComplaintStatus.LOGGED) {
                            SecondaryButton("Resolve without taking", onClick = { resolving = true }, enabled = !busy)
                        }
                    }
                },
                refundActions = { refund -> RefundButtons(refund, busy, onApprove = vm::approve, onReject = vm::reject, onComplete = vm::complete) },
            )

            if (resolving) {
                ResolveDialog(busy = busy, onDismiss = { resolving = false }, onResolve = { vm.resolve(complaint, it) { resolving = false } })
            }
        }
    }
}

@Composable
private fun RefundButtons(refund: Refund, busy: Boolean, onApprove: (Refund) -> Unit, onReject: (Refund) -> Unit, onComplete: (Refund) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when (refund.status) {
            RefundStatus.REQUESTED, RefundStatus.PROCESSING -> {
                TextButton(onClick = { onApprove(refund) }, enabled = !busy) { Text("Approve ${formatRand(refund.amountCents)}") }
                TextButton(onClick = { onReject(refund) }, enabled = !busy) { Text("Reject", color = MaterialTheme.colorScheme.error) }
            }
            RefundStatus.APPROVED -> TextButton(onClick = { onComplete(refund) }, enabled = !busy) { Text("Mark completed") }
            RefundStatus.COMPLETED, RefundStatus.REJECTED -> Unit
        }
    }
}

@Composable
private fun ResolveDialog(busy: Boolean, onDismiss: () -> Unit, onResolve: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resolve complaint") },
        text = { EatsTextField(text, { text = it }, "How was it resolved?", singleLine = false, minLines = 3) },
        confirmButton = { TextButton(onClick = { onResolve(text) }, enabled = !busy) { Text("Resolve") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
