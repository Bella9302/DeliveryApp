package com.itmda3b33.eats.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.itmda3b33.eats.data.model.MessageSender
import com.itmda3b33.eats.data.model.UserProfile
import com.itmda3b33.eats.ui.common.EatsScaffold
import com.itmda3b33.eats.ui.common.EatsTextField
import com.itmda3b33.eats.ui.common.StatusPill
import com.itmda3b33.eats.ui.common.Tone
import com.itmda3b33.eats.ui.common.eatsViewModel
import com.itmda3b33.eats.validation.ActionOutcome

@Composable
fun ChatScreen(profile: UserProfile, orderId: String?, onBack: (() -> Unit)?, onOpenComplaint: (String) -> Unit) {
    val vm = eatsViewModel(key = "chat-${orderId ?: "general"}") { ChatViewModel(it, profile, orderId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()

    LaunchedEffect(state.bubbles.size) {
        if (state.bubbles.isNotEmpty()) list.animateScrollToItem(state.bubbles.lastIndex)
    }

    EatsScaffold(
        title = "Help",
        onBack = onBack,
        actions = {
            // "I want a human" always works immediately.
            if (state.escalatedComplaintId == null) {
                TextButton(onClick = { vm.talkToPerson() }, enabled = !state.sending) {
                    Text("Talk to a person", color = MaterialTheme.colorScheme.secondary)
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.bubbles, key = { it.id }) { bubble -> Bubble(bubble) }
                if (state.sending) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("  Working on it…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            (state.escalatedComplaintId ?: state.complaintId)?.let { id ->
                TextButton(onClick = { onOpenComplaint(id) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        if (state.escalatedComplaintId != null) "With an agent. View your complaint" else "View your complaint",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                EatsTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = if (state.escalatedComplaintId != null) "Message the agent" else "Describe the problem",
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                )
                IconButton(
                    onClick = {
                        vm.send(input)
                        input = ""
                    },
                    enabled = input.isNotBlank() && !state.sending,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun Bubble(bubble: ChatBubble) {
    val mine = bubble.sender == MessageSender.CUSTOMER
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (bubble.sender == MessageSender.SYSTEM) {
            Text(bubble.text, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        } else {
            Surface(
                color = if (mine) scheme.primary else scheme.surfaceVariant,
                contentColor = if (mine) scheme.onPrimary else scheme.onSurface,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (mine) 16.dp else 4.dp,
                    bottomEnd = if (mine) 4.dp else 16.dp,
                ),
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(bubble.text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
        // Visible feedback for every action the rules decided on.
        bubble.outcomes.forEach { outcome ->
            Box(Modifier.padding(top = 4.dp)) { OutcomeTag(outcome) }
        }
    }
}

@Composable
private fun OutcomeTag(outcome: ActionOutcome) {
    val (label, tone) = when (outcome) {
        is ActionOutcome.Done -> when (outcome.functionName) {
            "issueRefund" -> outcome.summary to Tone.SUCCESS
            "createComplaint" -> "Complaint logged" to Tone.ACTIVE
            else -> "Checked your order" to Tone.NEUTRAL
        }
        is ActionOutcome.Blocked -> "Not allowed: ${outcome.summary}" to Tone.DANGER
        is ActionOutcome.Escalated -> "Sent to an agent" to Tone.WARNING
        is ActionOutcome.Failed -> "Error: ${outcome.summary}" to Tone.DANGER
    }
    StatusPill(label, tone)
}
