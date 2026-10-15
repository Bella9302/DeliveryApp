package com.itmda3b33.eats.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.itmda3b33.eats.AppContainer
import com.itmda3b33.eats.data.model.ComplaintCategory
import com.itmda3b33.eats.data.model.Order
import com.itmda3b33.eats.data.model.UserProfile
import com.itmda3b33.eats.ui.common.Caption
import com.itmda3b33.eats.ui.common.EatsScaffold
import com.itmda3b33.eats.ui.common.EatsTextField
import com.itmda3b33.eats.ui.common.EmptyView
import com.itmda3b33.eats.ui.common.MessageEffect
import com.itmda3b33.eats.ui.common.PrimaryButton
import com.itmda3b33.eats.ui.common.SectionTitle
import com.itmda3b33.eats.ui.common.StateContent
import com.itmda3b33.eats.ui.common.UiState
import com.itmda3b33.eats.ui.common.asUiState
import com.itmda3b33.eats.ui.common.eatsViewModel
import com.itmda3b33.eats.ui.common.shortId
import com.itmda3b33.eats.validation.ItemClaim
import com.itmda3b33.eats.validation.ServiceResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewComplaintForm(
    val category: ComplaintCategory? = null,
    val description: String = "",
    /** menuItemId to quantity for the ticked items. */
    val items: Map<String, Int> = emptyMap(),
    val submitting: Boolean = false,
    val createdId: String? = null,
)

class NewComplaintViewModel(private val container: AppContainer, private val customerId: String, private val orderId: String) : ViewModel() {
    val order: StateFlow<UiState<Order?>> = container.orderRepository.observeOrder(orderId).asUiState(viewModelScope)

    private val _form = MutableStateFlow(NewComplaintForm())
    val form: StateFlow<NewComplaintForm> = _form.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun onCategory(category: ComplaintCategory) = _form.update { it.copy(category = category) }
    fun onDescription(text: String) = _form.update { it.copy(description = text) }

    fun toggleItem(menuItemId: String, checked: Boolean) = _form.update {
        it.copy(items = if (checked) it.items + (menuItemId to 1) else it.items - menuItemId)
    }

    fun setQuantity(menuItemId: String, quantity: Int) = _form.update { it.copy(items = it.items + (menuItemId to quantity)) }

    /** The rules decide; this only shows their answer. */
    fun submit() {
        val f = _form.value
        if (f.submitting) return
        _form.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val claims = f.items.map { (id, qty) -> ItemClaim(id, qty) }
            when (val result = container.complaintService.create(customerId, orderId, f.category, f.description, claims)) {
                is ServiceResult.Success -> {
                    _messages.send("Complaint logged.")
                    _form.update { it.copy(submitting = false, createdId = result.value) }
                }
                is ServiceResult.Invalid -> {
                    _messages.send(result.failure.message)
                    _form.update { it.copy(submitting = false) }
                }
                is ServiceResult.Error -> {
                    _messages.send(result.message)
                    _form.update { it.copy(submitting = false) }
                }
            }
        }
    }
}

/** Module A: create. A form alternative to the chat for reporting a problem. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewComplaintScreen(profile: UserProfile, orderId: String, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val vm = eatsViewModel(key = "new-complaint-$orderId") { NewComplaintViewModel(it, profile.uid, orderId) }
    val orderState by vm.order.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm.messages, snackbar)
    LaunchedEffect(form.createdId) { form.createdId?.let(onCreated) }

    EatsScaffold(title = "Report a problem", onBack = onBack, snackbar = snackbar) { padding ->
        StateContent(orderState) { order ->
            if (order == null) {
                EmptyView("This order could not be found.")
                return@StateContent
            }
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Caption("${order.restaurantName} • Order ${shortId(order.id)} • ${order.status.label}")

                SectionTitle("What went wrong?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComplaintCategory.entries.forEach { category ->
                        FilterChip(
                            selected = form.category == category,
                            onClick = { vm.onCategory(category) },
                            label = { Text(category.label) },
                        )
                    }
                }

                SectionTitle("Affected items" + if (form.category == ComplaintCategory.MISSING_ITEM) "" else " (optional)")
                order.items.forEach { line ->
                    val selected = form.items[line.menuItemId]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selected != null, onCheckedChange = { vm.toggleItem(line.menuItemId, it) })
                        Text("${line.quantity} x ${line.name}", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
                        if (selected != null && line.quantity > 1) {
                            IconButton(onClick = { vm.setQuantity(line.menuItemId, (selected - 1).coerceAtLeast(1)) }) {
                                Icon(Icons.Filled.Remove, contentDescription = "Fewer")
                            }
                            Text("$selected", color = MaterialTheme.colorScheme.onBackground)
                            IconButton(onClick = { vm.setQuantity(line.menuItemId, (selected + 1).coerceAtMost(line.quantity)) }) {
                                Icon(Icons.Filled.Add, contentDescription = "More")
                            }
                        }
                    }
                }

                SectionTitle("Describe the problem")
                EatsTextField(form.description, vm::onDescription, "Description", singleLine = false, minLines = 3)

                Spacer(Modifier.height(16.dp))
                PrimaryButton("Submit complaint", onClick = vm::submit, loading = form.submitting)
                Caption(
                    "Missing-item refunds are handled in the chat. Complaints logged here are reviewed by our team.",
                    Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
