package com.itmda3b33.eats.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.itmda3b33.eats.data.OrderRepository
import com.itmda3b33.eats.data.model.Order
import com.itmda3b33.eats.data.model.OrderStatus
import com.itmda3b33.eats.data.model.UserProfile
import com.itmda3b33.eats.data.model.formatRand
import com.itmda3b33.eats.ui.common.Caption
import com.itmda3b33.eats.ui.common.EatsCard
import com.itmda3b33.eats.ui.common.EatsScaffold
import com.itmda3b33.eats.ui.common.EmptyView
import com.itmda3b33.eats.ui.common.PrimaryButton
import com.itmda3b33.eats.ui.common.SecondaryButton
import com.itmda3b33.eats.ui.common.SectionTitle
import com.itmda3b33.eats.ui.common.StateContent
import com.itmda3b33.eats.ui.common.StatusPill
import com.itmda3b33.eats.ui.common.UiState
import com.itmda3b33.eats.ui.common.asUiState
import com.itmda3b33.eats.ui.common.eatsViewModel
import com.itmda3b33.eats.ui.common.formatDateTime
import com.itmda3b33.eats.ui.common.shortId
import com.itmda3b33.eats.ui.common.tone
import kotlinx.coroutines.flow.StateFlow

class OrdersViewModel(orders: OrderRepository, customerId: String) : ViewModel() {
    val state: StateFlow<UiState<List<Order>>> = orders.observeCustomerOrders(customerId).asUiState(viewModelScope)
}

@Composable
fun OrdersScreen(profile: UserProfile, onOpen: (String) -> Unit) {
    val vm = eatsViewModel { OrdersViewModel(it.orderRepository, profile.uid) }
    val state by vm.state.collectAsStateWithLifecycle()

    EatsScaffold(title = "Your orders") { padding ->
        StateContent(state) { orders ->
            if (orders.isEmpty()) {
                EmptyView("You have no orders yet.")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(orders, key = { it.id }) { order -> OrderCard(order, onClick = { onOpen(order.id) }) }
                }
            }
        }
    }
}

@Composable
internal fun OrderCard(order: Order, onClick: (() -> Unit)? = null) {
    EatsCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(order.restaurantName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(order.status.label, order.status.tone())
        }
        Caption("${shortId(order.id)} • ${formatDateTime(order.createdAtMillis)}")
        Caption(order.items.joinToString { "${it.quantity} x ${it.name}" })
        Text(formatRand(order.totalCents), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

class OrderDetailViewModel(orders: OrderRepository, orderId: String) : ViewModel() {
    val state: StateFlow<UiState<Order?>> = orders.observeOrder(orderId).asUiState(viewModelScope)
}

/** Order tracking: live status timeline, items, and ways to get help. */
@Composable
fun OrderDetailScreen(orderId: String, onBack: () -> Unit, onChat: (String) -> Unit, onNewComplaint: (String) -> Unit) {
    val vm = eatsViewModel(key = "order-$orderId") { OrderDetailViewModel(it.orderRepository, orderId) }
    val state by vm.state.collectAsStateWithLifecycle()

    EatsScaffold(title = "Order ${shortId(orderId)}", onBack = onBack) { padding ->
        StateContent(state) { order ->
            if (order == null) {
                EmptyView("This order could not be found.")
                return@StateContent
            }
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            ) {
                Text(order.restaurantName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Caption("Placed ${formatDateTime(order.createdAtMillis)}")

                SectionTitle("Tracking")
                StatusTimeline(order.status)

                SectionTitle("Items")
                EatsCard {
                    order.items.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("${item.quantity} x ${item.name}", modifier = Modifier.weight(1f))
                            Text(formatRand(item.lineTotalCents))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("Total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(formatRand(order.totalCents), style = MaterialTheme.typography.titleSmall)
                    }
                }

                SectionTitle("Something wrong?")
                PrimaryButton("Chat to us about this order", onClick = { onChat(order.id) })
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Report a problem with a form", onClick = { onNewComplaint(order.id) })
            }
        }
    }
}

/** Steps from placed to delivered. Cancelled orders show a single pill instead. */
@Composable
private fun StatusTimeline(status: OrderStatus) {
    if (status == OrderStatus.CANCELLED) {
        StatusPill("Cancelled", status.tone())
        return
    }
    val reached = OrderStatus.deliveryPath.indexOf(status)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OrderStatus.deliveryPath.forEachIndexed { index, step ->
            val done = index <= reached
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(
                            if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    step.label,
                    color = if (done) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = if (index == reached) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
