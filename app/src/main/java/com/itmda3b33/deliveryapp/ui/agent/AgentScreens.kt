package com.itmda3b33.deliveryapp.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itmda3b33.deliveryapp.data.model.ComplaintStatus
import com.itmda3b33.deliveryapp.data.model.Money
import com.itmda3b33.deliveryapp.data.model.Refund
import com.itmda3b33.deliveryapp.ui.common.LoadingBox
import com.itmda3b33.deliveryapp.ui.common.MessageBox
import com.itmda3b33.deliveryapp.ui.common.ScreenScaffold
import com.itmda3b33.deliveryapp.ui.common.categoryLabel
import com.itmda3b33.deliveryapp.ui.common.complaintStatusLabel
import com.itmda3b33.deliveryapp.ui.common.formatTime
import com.itmda3b33.deliveryapp.ui.common.orderStatusLabel
import com.itmda3b33.deliveryapp.ui.common.refundStatusLabel

@Composable
fun AgentQueueScreen(vm: AgentViewModel, onOpen: (complaintId: String) -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadQueue() }

    ScreenScaffold(title = "Escalated complaints", onBack = onBack, actions = { TextButton(onClick = vm::loadQueue) { Text("Refresh") } }) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.loading -> LoadingBox(modifier)
            state.error != null -> MessageBox(state.error!!, modifier, isError = true, actionLabel = "Try again", onAction = vm::loadQueue)
            state.queue.isEmpty() -> MessageBox("Nothing is waiting. Well done!", modifier)
            else -> LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.queue, key = { it.id }) { complaint ->
                    Card(onClick = { onOpen(complaint.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(categoryLabel(complaint.category), style = MaterialTheme.typography.titleMedium)
                            Text(complaintStatusLabel(complaint.status))
                            Text(complaint.description, maxLines = 2)
                            Text(formatTime(complaint.createdAt), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgentComplaintScreen(vm: AgentViewModel, complaintId: String, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var resolving by remember { mutableStateOf(false) }

    LaunchedEffect(complaintId) { vm.loadCase(complaintId) }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); vm.noticeShown() }
    }

    ScreenScaffold(title = "Complaint", onBack = onBack, snackbar = snackbar) { padding ->
        val modifier = Modifier.padding(padding)
        val case = state.case
        when {
            state.loading && case == null -> LoadingBox(modifier)
            case == null -> MessageBox(state.error ?: "Not found.", modifier, isError = true, actionLabel = "Try again") {
                vm.loadCase(complaintId)
            }
            else -> Column(
                modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val complaint = case.complaint
                Text(categoryLabel(complaint.category), style = MaterialTheme.typography.headlineSmall)
                Text("Status: ${complaintStatusLabel(complaint.status)} · Ref ${complaint.id}")
                Text(complaint.description)

                case.order?.let { order ->
                    HorizontalDivider()
                    Text("Order", style = MaterialTheme.typography.titleMedium)
                    Text("${order.restaurantName} · ${orderStatusLabel(order.status)} · ${Money.format(order.totalCents)}")
                    order.items.forEach { Text("${it.quantity} x ${it.name} (${Money.format(it.unitPriceCents)} each)") }
                }

                if (case.refunds.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Refunds", style = MaterialTheme.typography.titleMedium)
                    case.refunds.forEach { RefundRow(it, vm.canDecide(it), vm::approve, vm::reject) }
                }

                HorizontalDivider()
                Text("Chat history", style = MaterialTheme.typography.titleMedium)
                if (case.messages.isEmpty()) Text("No chat was saved.")
                case.messages.forEach {
                    Text("${it.sender.replaceFirstChar(Char::uppercase)} (${formatTime(it.sentAt)}): ${it.text}")
                }

                if (ComplaintStatus.from(complaint.status) != ComplaintStatus.RESOLVED) {
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (ComplaintStatus.from(complaint.status) == ComplaintStatus.ESCALATED) {
                            OutlinedButton(onClick = { vm.take(complaint) }) { Text("Take it") }
                        }
                        Button(onClick = { resolving = true }) { Text("Resolve") }
                    }
                }
            }
        }
    }

    val complaint = state.case?.complaint
    if (resolving && complaint != null) {
        var note by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { resolving = false },
            title = { Text("Resolve complaint") },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it; error = null },
                    label = { Text("Note for the customer") },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    error = vm.resolve(complaint, note)
                    if (error == null) resolving = false
                }) { Text("Resolve") }
            },
            dismissButton = { TextButton(onClick = { resolving = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RefundRow(refund: Refund, canDecide: Boolean, onApprove: (Refund) -> Unit, onReject: (Refund) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${refund.quantity} x item ${refund.itemId}: ${Money.format(refund.amountCents)}")
            Text("Status: ${refundStatusLabel(refund.status)}" + (refund.approvedBy?.let { " · by $it" } ?: ""))
            if (canDecide) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onApprove(refund) }) { Text("Approve") }
                    OutlinedButton(onClick = { onReject(refund) }) { Text("Reject") }
                }
            }
        }
    }
}
