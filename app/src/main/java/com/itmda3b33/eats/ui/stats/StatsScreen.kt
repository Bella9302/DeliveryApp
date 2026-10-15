package com.itmda3b33.eats.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.itmda3b33.eats.AppContainer
import com.itmda3b33.eats.data.model.ComplaintStats
import com.itmda3b33.eats.data.model.computeStats
import com.itmda3b33.eats.data.model.formatRand
import com.itmda3b33.eats.ui.common.Caption
import com.itmda3b33.eats.ui.common.EatsCard
import com.itmda3b33.eats.ui.common.EatsScaffold
import com.itmda3b33.eats.ui.common.LinkButton
import com.itmda3b33.eats.ui.common.SectionTitle
import com.itmda3b33.eats.ui.common.StateContent
import com.itmda3b33.eats.ui.common.UiState
import com.itmda3b33.eats.ui.common.asUiState
import com.itmda3b33.eats.ui.common.eatsViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlin.math.roundToInt

class StatsViewModel(container: AppContainer) : ViewModel() {
    val state: StateFlow<UiState<ComplaintStats>> =
        combine(container.complaintRepository.observeAll(), container.refundRepository.observeAll()) { complaints, refunds -> computeStats(complaints, refunds) }
            .asUiState(viewModelScope)
}

/** Live figures for agents and admins: how many complaints the bot resolves and where the rest go. */
@Composable
fun StatsScreen(onSignOut: () -> Unit) {
    val vm = eatsViewModel { StatsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    EatsScaffold(title = "Stats", actions = { LinkButton("Sign out", onClick = onSignOut) }) { padding ->
        StateContent(state) { stats ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile("Complaints", stats.totalComplaints.toString(), Modifier.weight(1f))
                    Tile("Resolved by bot", "${(stats.autoResolveRate * 100).roundToInt()}%", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Tile("Reached an agent", stats.escalated.toString(), Modifier.weight(1f))
                    Tile("Refunded", formatRand(stats.refundedCents), Modifier.weight(1f))
                }
                Caption(
                    "Automatic ${formatRand(stats.systemRefundedCents)} • By agents ${formatRand(stats.agentRefundedCents)}",
                    Modifier.padding(top = 8.dp),
                )

                SectionTitle("Complaints by status")
                Bars(stats.byStatus.mapKeys { it.key.label })
                SectionTitle("Complaints by category")
                Bars(stats.byCategory.mapKeys { it.key.label })
                SectionTitle("Refunds by status")
                Bars(stats.refundsByStatus.mapKeys { it.key.label })
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    EatsCard(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Caption(label)
    }
}

/** Simple horizontal bars, labelled with the count so the numbers stay readable without colour. */
@Composable
private fun Bars(values: Map<String, Int>) {
    val max = (values.values.maxOrNull() ?: 0).coerceAtLeast(1)
    EatsCard {
        values.forEach { (label, count) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(140.dp))
                Box(Modifier.weight(1f).height(12.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(count.toFloat() / max)
                            .height(12.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
                    )
                }
                Text(count.toString(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp).width(32.dp))
            }
        }
    }
}
