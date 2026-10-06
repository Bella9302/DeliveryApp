package com.itmda3b33.deliveryapp.ui.orders

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itmda3b33.deliveryapp.data.model.Money
import com.itmda3b33.deliveryapp.data.model.Order
import com.itmda3b33.deliveryapp.data.model.OrderStatus
import com.itmda3b33.deliveryapp.ui.common.LoadingBox
import com.itmda3b33.deliveryapp.ui.common.MessageBox
import com.itmda3b33.deliveryapp.ui.common.ScreenScaffold
import com.itmda3b33.deliveryapp.ui.common.formatTime
import com.itmda3b33.deliveryapp.ui.common.orderStatusLabel

@Composable
fun OrdersScreen(vm: OrdersViewModel, onOpen: (orderId: String) -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadOrders() }

    ScreenScaffold(title = "My orders", onBack = onBack) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.loading -> LoadingBox(modifier)
            state.error != null -> MessageBox(state.error!!, modifier, isError = true, actionLabel = "Try again") { vm.loadOrders() }
            state.orders.isEmpty() -> MessageBox("You have no orders yet.", modifier)
            else -> LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.orders, key = { it.id }) { order ->
                    Card(onClick = { onOpen(order.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(order.restaurantName, style = MaterialTheme.typography.titleMedium)
                            Text("${orderStatusLabel(order.status)} · ${Money.format(order.totalCents)}")
                            Text(formatTime(order.createdAt), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderDetailScreen(vm: OrdersViewModel, orderId: String, onReportProblem: () -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(orderId) { vm.loadOrder(orderId) }

    ScreenScaffold(title = "Order details", onBack = onBack) { padding ->
        val modifier = Modifier.padding(padding)
        val order = state.selected
        when {
            state.loading -> LoadingBox(modifier)
            order == null -> MessageBox(state.error ?: "Order not found.", modifier, isError = true, actionLabel = "Try again") {
                vm.loadOrder(orderId)
            }
            else -> OrderDetail(order, onReportProblem, modifier)
        }
    }
}

@Composable
private fun OrderDetail(order: Order, onReportProblem: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(order.restaurantName, style = MaterialTheme.typography.headlineSmall)
        Text("Order ${order.id}", style = MaterialTheme.typography.bodySmall)
        StatusTracker(order.status)
        HorizontalDivider()
        order.items.forEach {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${it.quantity} x ${it.name}")
                Text(Money.format(it.unitPriceCents * it.quantity))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", fontWeight = FontWeight.Bold)
            Text(Money.format(order.totalCents), fontWeight = FontWeight.Bold)
        }
        Button(onClick = onReportProblem, modifier = Modifier.fillMaxWidth()) { Text("Report a problem with this order") }
    }
}

/** Shows each delivery step, with the steps already reached in bold. */
@Composable
private fun StatusTracker(status: String) {
    val current = OrderStatus.from(status)
    if (current == OrderStatus.CANCELLED || current == null) {
        Text("Status: ${orderStatusLabel(status)}", style = MaterialTheme.typography.titleMedium)
        return
    }
    val steps = OrderStatus.entries.filter { it != OrderStatus.CANCELLED }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEach { step ->
            val reached = step.ordinal <= current.ordinal
            Text(
                text = (if (reached) "● " else "○ ") + step.label,
                fontWeight = if (step == current) FontWeight.Bold else FontWeight.Normal,
                color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
