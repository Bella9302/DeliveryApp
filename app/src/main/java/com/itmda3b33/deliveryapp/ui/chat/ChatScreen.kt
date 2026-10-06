package com.itmda3b33.deliveryapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itmda3b33.deliveryapp.data.model.ChatMessage
import com.itmda3b33.deliveryapp.ui.common.ScreenScaffold

@Composable
fun ChatScreen(vm: ChatViewModel, onOpenComplaints: () -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Keep the newest message in view.
    LaunchedEffect(state.messages.size) { listState.animateScrollToItem(state.messages.lastIndex) }

    ScreenScaffold(title = "Support chat", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.messages) { _, message -> Bubble(message) }
            }

            if (state.waiting) LinearProgressIndicator(Modifier.fillMaxWidth())

            state.error?.let { error ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::dismissError) { Text("OK") }
                }
            }

            if (state.escalatedComplaintId != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("A support agent has your chat. Reference: ${state.escalatedComplaintId}")
                        Button(onClick = onOpenComplaints) { Text("Go to My complaints") }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = vm::requestHuman,
                    enabled = !state.waiting,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                ) { Text("Talk to a person") }

                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Describe the problem") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                    )
                    Button(
                        onClick = { vm.send(draft); draft = "" },
                        enabled = draft.isNotBlank() && !state.waiting,
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("Send") }
                }
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val mine = message.sender == ChatMessage.SENDER_CUSTOMER
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text = message.text,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
        )
    }
}
