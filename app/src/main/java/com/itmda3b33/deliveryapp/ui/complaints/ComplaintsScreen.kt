package com.itmda3b33.deliveryapp.ui.complaints

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import com.itmda3b33.deliveryapp.data.model.Complaint
import com.itmda3b33.deliveryapp.ui.common.LoadingBox
import com.itmda3b33.deliveryapp.ui.common.MessageBox
import com.itmda3b33.deliveryapp.ui.common.ScreenScaffold
import com.itmda3b33.deliveryapp.ui.common.categoryLabel
import com.itmda3b33.deliveryapp.ui.common.complaintStatusLabel
import com.itmda3b33.deliveryapp.ui.common.formatTime

@Composable
fun ComplaintsScreen(vm: ComplaintsViewModel, onNewComplaint: () -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Complaint?>(null) }
    var deleting by remember { mutableStateOf<Complaint?>(null) }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); vm.noticeShown() }
    }

    ScreenScaffold(
        title = "My complaints",
        onBack = onBack,
        snackbar = snackbar,
        floatingAction = { ExtendedFloatingActionButton(onClick = onNewComplaint) { Text("New complaint") } },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.loading -> LoadingBox(modifier)
            state.error != null -> MessageBox(state.error!!, modifier, isError = true, actionLabel = "Try again", onAction = vm::load)
            state.complaints.isEmpty() -> MessageBox("You have no complaints. Tap New complaint to report a problem.", modifier)
            else -> LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.complaints, key = { it.id }) { complaint ->
                    ComplaintCard(
                        complaint = complaint,
                        editable = vm.canEdit(complaint),
                        onEdit = { editing = complaint },
                        onDelete = { deleting = complaint },
                    )
                }
            }
        }
    }

    editing?.let { complaint ->
        EditDescriptionDialog(
            initial = complaint.description,
            onSave = { text -> vm.updateDescription(complaint, text).also { if (it == null) editing = null } },
            onDismiss = { editing = null },
        )
    }

    deleting?.let { complaint ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Cancel this complaint?") },
            text = { Text("It will be removed and no one will follow it up.") },
            confirmButton = { TextButton(onClick = { vm.delete(complaint); deleting = null }) { Text("Cancel complaint") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun ComplaintCard(complaint: Complaint, editable: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(categoryLabel(complaint.category), style = MaterialTheme.typography.titleMedium)
            Text("Status: ${complaintStatusLabel(complaint.status)}")
            Text(complaint.description)
            if (complaint.resolution.isNotBlank()) {
                Text("Outcome: ${complaint.resolution}", color = MaterialTheme.colorScheme.primary)
            }
            Text("Ref ${complaint.id} · ${formatTime(complaint.createdAt)}", style = MaterialTheme.typography.bodySmall)
            if (editable) {
                Row {
                    TextButton(onClick = onEdit) { Text("Edit") }
                    TextButton(onClick = onDelete) { Text("Cancel complaint") }
                }
            }
        }
    }
}

/** [onSave] returns an error message to show, or null when the dialog can close. */
@Composable
private fun EditDescriptionDialog(initial: String, onSave: (String) -> String?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit complaint") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                label = { Text("Description") },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                minLines = 3,
            )
        },
        confirmButton = { TextButton(onClick = { error = onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
