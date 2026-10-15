package com.itmda3b33.eats.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.itmda3b33.eats.data.model.Refund
import com.itmda3b33.eats.data.model.UserProfile
import com.itmda3b33.eats.data.model.formatRand
import com.itmda3b33.eats.ui.common.Caption
import com.itmda3b33.eats.ui.common.EatsCard
import com.itmda3b33.eats.ui.common.EatsScaffold
import com.itmda3b33.eats.ui.common.EatsTextField
import com.itmda3b33.eats.ui.common.EmptyView
import com.itmda3b33.eats.ui.common.MessageEffect
import com.itmda3b33.eats.ui.common.SectionTitle
import com.itmda3b33.eats.ui.common.StateContent
import com.itmda3b33.eats.ui.common.StatusPill
import com.itmda3b33.eats.ui.common.UiState
import com.itmda3b33.eats.ui.common.asUiState
import com.itmda3b33.eats.ui.common.eatsViewModel
import com.itmda3b33.eats.ui.common.formatDateTime
import com.itmda3b33.eats.ui.common.shortId
import com.itmda3b33.eats.ui.common.tone
import com.itmda3b33.eats.validation.ServiceResult
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ComplaintsViewModel(container: AppContainer, customerId: String) : ViewModel() {
    val state: StateFlow<UiState<List<Complaint>>> =
        container.complaintRepository.observeCustomerComplaints(customerId).asUiState(viewModelScope)
}

/** The customer's complaints (Module A: read). */
@Composable
fun ComplaintsScreen(profile: UserProfile, onOpen: (String) -> Unit, onChat: () -> Unit) {
    val vm = eatsViewModel { ComplaintsViewModel(it, profile.uid) }
    val state by vm.state.collectAsStateWithLifecycle()

    EatsScaffold(
        title = "Complaints",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onChat,
                icon = { Icon(Icons.Outlined.SupportAgent, contentDescription = null) },
                text = { Text("Get help") },
            )
        },
    ) { padding ->
        StateContent(state) { complaints ->
            if (complaints.isEmpty()) {
                EmptyView("No complaints. If something goes wrong with an order, open it from Orders or tap Get help.")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(complaints, key = { it.id }) { complaint -> ComplaintCard(complaint, onClick = { onOpen(complaint.id) }) }
                }
            }
        }
    }
}

@Composable
internal fun ComplaintCard(complaint: Complaint, onClick: () -> Unit) {
    EatsCard(onClick = onClick) {
        Row {
            Text(complaint.category.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(complaint.status.label, complaint.status.tone())
        }
        Caption("${shortId(complaint.id)} • ${formatDateTime(complaint.createdAtMillis)}" + (complaint.orderId?.let { " • Order ${shortId(it)}" } ?: ""))
        Text(complaint.description, maxLines = 2, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

data class ComplaintDetail(
    val complaint: Complaint?,
    val messages: List<ComplaintMessage>,
    val refunds: List<Refund>,
)

class ComplaintDetailViewModel(
    private val container: AppContainer,
    private val customerId: String,
    complaintId: String,
) : ViewModel() {

    val state: StateFlow<UiState<ComplaintDetail>> = combine(
        container.complaintRepository.observeComplaint(complaintId),
        container.complaintRepository.observeMessages(complaintId),
        container.refundRepository.observeForComplaint(complaintId, customerId),
    ) { complaint, messages, refunds -> ComplaintDetail(complaint, messages, refunds) }
        .asUiState(viewModelScope)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Set after a successful delete so the screen can close. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun saveDescription(complaint: Complaint, text: String, onSaved: () -> Unit) = perform("Complaint updated.") {
        container.complaintService.updateDescription(customerId, complaint, text).also { if (it is ServiceResult.Success) onSaved() }
    }

    fun delete(complaint: Complaint) = perform("Complaint deleted.") {
        container.complaintService.delete(customerId, complaint).also { if (it is ServiceResult.Success) _deleted.value = true }
    }

    fun reply(complaintId: String, text: String, onSent: () -> Unit) = perform(null) {
        container.complaintService.reply(complaintId, MessageSender.CUSTOMER, text).also { if (it is ServiceResult.Success) onSent() }
    }

    private fun perform(successMessage: String?, block: suspend () -> ServiceResult<Unit>) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            when (val result = block()) {
                is ServiceResult.Success -> successMessage?.let { _messages.send(it) }
                is ServiceResult.Invalid -> _messages.send(result.failure.message)
                is ServiceResult.Error -> _messages.send(result.message)
            }
            _busy.value = false
        }
    }
}

/** One complaint: status, refunds, chat history and replies. Customers can edit or delete while it is logged. */
@Composable
fun ComplaintDetailScreen(profile: UserProfile, complaintId: String, onBack: () -> Unit) {
    val vm = eatsViewModel(key = "complaint-$complaintId") { ComplaintDetailViewModel(it, profile.uid, complaintId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val deleted by vm.deleted.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm.messages, snackbar)

    LaunchedEffect(deleted) { if (deleted) onBack() }

    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val complaint = (state as? UiState.Ready)?.data?.complaint
    val editable = complaint?.status == ComplaintStatus.LOGGED

    EatsScaffold(
        title = "Complaint ${shortId(complaintId)}",
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            if (editable) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, contentDescription = "Edit") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
            }
        },
    ) { padding ->
        StateContent(state) { detail ->
            val c = detail.complaint
            if (c == null) {
                EmptyView("This complaint no longer exists.")
                return@StateContent
            }
            ComplaintThread(
                complaint = c,
                messages = detail.messages,
                refunds = detail.refunds,
                busy = busy,
                onReply = { text, clear -> vm.reply(c.id, text, clear) },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (editing && complaint != null) {
        EditDescriptionDialog(
            initial = complaint.description,
            busy = busy,
            onDismiss = { editing = false },
            onSave = { vm.saveDescription(complaint, it) { editing = false } },
        )
    }
    if (confirmDelete && complaint != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this complaint?") },
            text = { Text("This removes the complaint and its messages. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(complaint) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EditDescriptionDialog(initial: String, busy: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit description") },
        text = { EatsTextField(text, { text = it }, "What went wrong?", singleLine = false, minLines = 3) },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = !busy) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Complaint summary, refunds, message history and a reply box. Shared by the customer and agent screens.
 * [header] lets the agent screen add its own controls above the messages.
 */
@Composable
fun ComplaintThread(
    complaint: Complaint,
    messages: List<ComplaintMessage>,
    refunds: List<Refund>,
    busy: Boolean,
    onReply: (text: String, clear: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    replyEnabled: Boolean = true,
    header: @Composable () -> Unit = {},
    refundActions: @Composable (Refund) -> Unit = {},
) {
    var reply by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                EatsCard {
                    Row {
                        Text(complaint.category.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        StatusPill(complaint.status.label, complaint.status.tone())
                    }
                    Caption("Opened ${formatDateTime(complaint.createdAtMillis)}" + (complaint.orderId?.let { " • Order ${shortId(it)}" } ?: ""))
                    Text(complaint.description, modifier = Modifier.padding(top = 8.dp))
                    if (complaint.items.isNotEmpty()) {
                        Caption("Items: " + complaint.items.joinToString { "${it.quantity} x ${it.name}" }, Modifier.padding(top = 4.dp))
                    }
                    complaint.resolution?.let { Text("Resolution: $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
            item { header() }
            if (refunds.isNotEmpty()) {
                item { SectionTitle("Refunds") }
                items(refunds, key = { it.id }) { refund ->
                    EatsCard {
                        Row {
                            Text("${refund.quantity} x ${refund.itemName}", modifier = Modifier.weight(1f))
                            Text(formatRand(refund.amountCents), style = MaterialTheme.typography.titleSmall)
                        }
                        Row(Modifier.padding(top = 4.dp)) {
                            StatusPill(refund.status.label, refund.status.tone())
                            Spacer(Modifier.weight(1f))
                            refund.approvedBy?.let { Caption(if (it == Refund.APPROVED_BY_SYSTEM) "Approved automatically" else "Decided by an agent") }
                        }
                        refundActions(refund)
                    }
                }
            }
            item { SectionTitle("Messages") }
            if (messages.isEmpty()) item { Caption("No messages yet.") }
            items(messages, key = { it.id }) { message -> MessageRow(message) }
        }
        if (replyEnabled) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                EatsTextField(reply, { reply = it }, "Write a message", Modifier.weight(1f), singleLine = false)
                TextButton(onClick = { onReply(reply) { reply = "" } }, enabled = !busy && reply.isNotBlank()) { Text("Send") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MessageRow(message: ComplaintMessage) {
    val who = when (message.sender) {
        MessageSender.CUSTOMER -> "Customer"
        MessageSender.BOT -> "eats assistant"
        MessageSender.AGENT -> "Agent"
        MessageSender.SYSTEM -> "Update"
    }
    Column(Modifier.padding(vertical = 4.dp)) {
        Caption("$who • ${formatDateTime(message.createdAtMillis)}")
        Text(message.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}
