package com.itmda3b33.deliveryapp.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itmda3b33.deliveryapp.data.model.Money
import com.itmda3b33.deliveryapp.data.model.Stats
import com.itmda3b33.deliveryapp.ui.common.LoadingBox
import com.itmda3b33.deliveryapp.ui.common.MessageBox
import com.itmda3b33.deliveryapp.ui.common.ScreenScaffold

@Composable
fun StatsScreen(vm: StatsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }

    ScreenScaffold(title = "Stats", onBack = onBack, actions = { TextButton(onClick = vm::load) { Text("Refresh") } }) { padding ->
        val modifier = Modifier.padding(padding)
        val stats = state.stats
        when {
            state.loading -> LoadingBox(modifier)
            stats == null -> MessageBox(state.error ?: "No data.", modifier, isError = true, actionLabel = "Try again", onAction = vm::load)
            else -> StatsContent(stats, modifier)
        }
    }
}

@Composable
private fun StatsContent(stats: Stats, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Complaints", stats.totalComplaints.toString(), Modifier.weight(1f))
            Tile("Refunded", Money.format(stats.refundedCents), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Auto refunds", stats.autoRefunds.toString(), Modifier.weight(1f))
            Tile("Agent refunds", stats.agentRefunds.toString(), Modifier.weight(1f))
            Tile("Waiting", stats.refundsWaiting.toString(), Modifier.weight(1f))
        }

        Text("Complaints by status", style = MaterialTheme.typography.titleMedium)
        stats.byStatus.forEach { (status, count) -> Bar(status.label, count, stats.totalComplaints) }

        Text("Complaints by type", style = MaterialTheme.typography.titleMedium)
        stats.byCategory.forEach { (category, count) -> Bar(category.label, count, stats.totalComplaints) }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** One labelled bar showing [count] as a share of [total]. */
@Composable
private fun Bar(label: String, count: Int, total: Int) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(count.toString())
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else count.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
