package com.itmda3b33.eats.ui.restaurant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.itmda3b33.eats.AppContainer
import com.itmda3b33.eats.data.model.MenuItem
import com.itmda3b33.eats.data.model.Order
import com.itmda3b33.eats.data.model.UserProfile
import com.itmda3b33.eats.data.model.formatRand
import com.itmda3b33.eats.ui.common.Caption
import com.itmda3b33.eats.ui.common.EatsCard
import com.itmda3b33.eats.ui.common.EatsScaffold
import com.itmda3b33.eats.ui.common.EatsTextField
import com.itmda3b33.eats.ui.common.EmptyView
import com.itmda3b33.eats.ui.common.ErrorView
import com.itmda3b33.eats.ui.common.LinkButton
import com.itmda3b33.eats.ui.common.LoadingView
import com.itmda3b33.eats.ui.common.MessageEffect
import com.itmda3b33.eats.ui.common.PrimaryButton
import com.itmda3b33.eats.ui.common.StateContent
import com.itmda3b33.eats.ui.common.UiState
import com.itmda3b33.eats.ui.common.asUiState
import com.itmda3b33.eats.ui.common.eatsViewModel
import com.itmda3b33.eats.ui.customer.OrderCard
import com.itmda3b33.eats.validation.MenuItemInput
import com.itmda3b33.eats.validation.MenuSaveResult
import com.itmda3b33.eats.validation.ServiceResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val NOT_LINKED = "Your account is not linked to a restaurant yet. Ask an admin to set restaurantId on your profile."

class RestaurantMenuViewModel(private val container: AppContainer, private val profile: UserProfile) : ViewModel() {
    val state: StateFlow<UiState<List<MenuItem>>> =
        (profile.restaurantId?.let { container.restaurantRepository.observeMenu(it) } ?: emptyFlow()).asUiState(viewModelScope)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setAvailable(item: MenuItem, available: Boolean) = perform(if (available) "${item.name} is available." else "${item.name} is sold out.") {
        container.menuService.setAvailable(profile, item, available)
    }

    fun delete(item: MenuItem) = perform("${item.name} deleted.") { container.menuService.delete(profile, item) }

    private fun perform(success: String, block: suspend () -> ServiceResult<Unit>) {
        viewModelScope.launch {
            _messages.send(
                when (val r = block()) {
                    is ServiceResult.Success -> success
                    is ServiceResult.Invalid -> r.failure.message
                    is ServiceResult.Error -> r.message
                },
            )
        }
    }
}

/** Module B: read, update availability and delete. Add and edit open [MenuItemEditScreen]. */
@Composable
fun RestaurantMenuScreen(profile: UserProfile, onAdd: () -> Unit, onEdit: (String) -> Unit, onSignOut: () -> Unit) {
    val vm = eatsViewModel { RestaurantMenuViewModel(it, profile) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm.messages, snackbar)
    var toDelete by remember { mutableStateOf<MenuItem?>(null) }

    EatsScaffold(
        title = "Your menu",
        snackbar = snackbar,
        actions = { LinkButton("Sign out", onClick = onSignOut) },
        floatingActionButton = {
            if (profile.restaurantId != null) {
                FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add item") }
            }
        },
    ) { padding ->
        if (profile.restaurantId == null) {
            ErrorView(NOT_LINKED)
            return@EatsScaffold
        }
        StateContent(state) { items ->
            if (items.isEmpty()) {
                EmptyView("Your menu is empty. Tap + to add your first item.")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        EatsCard(onClick = { onEdit(item.id) }) {
                            MenuItemRow(item, onAvailable = { vm.setAvailable(item, it) }, onDelete = { toDelete = item })
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${item.name}?") },
            text = { Text("Customers will no longer see this item.") },
            confirmButton = {
                TextButton(onClick = { toDelete = null; vm.delete(item) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MenuItemRow(item: MenuItem, onAvailable: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            if (item.description.isNotBlank()) Caption(item.description)
            Text(formatRand(item.priceCents), style = MaterialTheme.typography.titleSmall)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Switch(checked = item.available, onCheckedChange = onAvailable)
            Caption(if (item.available) "Available" else "Sold out")
        }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete ${item.name}") }
    }
}

data class MenuItemForm(
    val loaded: Boolean = false,
    val name: String = "",
    val description: String = "",
    val price: String = "",
    val available: Boolean = true,
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val loadError: String? = null,
)

class MenuItemEditViewModel(private val container: AppContainer, private val profile: UserProfile, private val itemId: String?) : ViewModel() {
    private val _form = MutableStateFlow(MenuItemForm(loaded = itemId == null))
    val form: StateFlow<MenuItemForm> = _form.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        val restaurantId = profile.restaurantId
        if (itemId != null && restaurantId != null) {
            viewModelScope.launch {
                try {
                    val item = container.restaurantRepository.getMenuItem(restaurantId, itemId)
                    _form.value = if (item == null) {
                        MenuItemForm(loaded = true, loadError = "This item no longer exists.")
                    } else {
                        MenuItemForm(
                            loaded = true,
                            name = item.name,
                            description = item.description,
                            price = "%d,%02d".format(item.priceCents / 100, item.priceCents % 100),
                            available = item.available,
                        )
                    }
                } catch (e: Exception) {
                    _form.value = MenuItemForm(loaded = true, loadError = "Could not load this item. Check your connection.")
                }
            }
        }
    }

    fun onName(v: String) = _form.update { it.copy(name = v, errors = it.errors - MenuItemInput.FIELD_NAME) }
    fun onDescription(v: String) = _form.update { it.copy(description = v, errors = it.errors - MenuItemInput.FIELD_DESCRIPTION) }
    fun onPrice(v: String) = _form.update { it.copy(price = v, errors = it.errors - MenuItemInput.FIELD_PRICE) }
    fun onAvailable(v: Boolean) = _form.update { it.copy(available = v) }

    fun save() {
        val restaurantId = profile.restaurantId ?: return
        val f = _form.value
        if (f.saving) return
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = container.menuService.save(profile, restaurantId, itemId, f.name, f.description, f.price, f.available)) {
                is ServiceResult.Success -> when (val saved = r.value) {
                    MenuSaveResult.Saved -> {
                        _messages.send("Saved.")
                        _form.update { it.copy(saving = false, saved = true) }
                    }
                    is MenuSaveResult.FieldErrors -> _form.update { it.copy(saving = false, errors = saved.errors) }
                }
                is ServiceResult.Invalid -> {
                    _messages.send(r.failure.message)
                    _form.update { it.copy(saving = false) }
                }
                is ServiceResult.Error -> {
                    _messages.send(r.message)
                    _form.update { it.copy(saving = false) }
                }
            }
        }
    }
}

/** Module B: create and update a menu item. */
@Composable
fun MenuItemEditScreen(profile: UserProfile, itemId: String?, onBack: () -> Unit) {
    val vm = eatsViewModel(key = "menu-item-${itemId ?: "new"}") { MenuItemEditViewModel(it, profile, itemId) }
    val form by vm.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm.messages, snackbar)
    LaunchedEffect(form.saved) { if (form.saved) onBack() }

    EatsScaffold(title = if (itemId == null) "Add item" else "Edit item", onBack = onBack, snackbar = snackbar) { padding ->
        when {
            profile.restaurantId == null -> ErrorView(NOT_LINKED)
            !form.loaded -> LoadingView()
            form.loadError != null -> ErrorView(form.loadError!!)
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                EatsTextField(form.name, vm::onName, "Name", error = form.errors[MenuItemInput.FIELD_NAME])
                EatsTextField(form.description, vm::onDescription, "Description", error = form.errors[MenuItemInput.FIELD_DESCRIPTION], singleLine = false, minLines = 2)
                EatsTextField(
                    form.price,
                    vm::onPrice,
                    "Price (R)",
                    error = form.errors[MenuItemInput.FIELD_PRICE],
                    keyboardType = KeyboardType.Decimal,
                    supportingText = "For example 79,00",
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Available to order", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
                    Switch(checked = form.available, onCheckedChange = vm::onAvailable)
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(if (itemId == null) "Add item" else "Save changes", onClick = vm::save, loading = form.saving)
            }
        }
    }
}

class RestaurantOrdersViewModel(container: AppContainer, profile: UserProfile) : ViewModel() {
    val state: StateFlow<UiState<List<Order>>> =
        (profile.restaurantId?.let { container.orderRepository.observeRestaurantOrders(it) } ?: emptyFlow()).asUiState(viewModelScope)
}

/** Orders for this restaurant, read-only. */
@Composable
fun RestaurantOrdersScreen(profile: UserProfile) {
    val vm = eatsViewModel { RestaurantOrdersViewModel(it, profile) }
    val state by vm.state.collectAsStateWithLifecycle()

    EatsScaffold(title = "Orders") { padding ->
        if (profile.restaurantId == null) {
            ErrorView(NOT_LINKED)
            return@EatsScaffold
        }
        StateContent(state) { orders ->
            if (orders.isEmpty()) {
                EmptyView("No orders yet.")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(orders, key = { it.id }) { OrderCard(it) }
                }
            }
        }
    }
}
