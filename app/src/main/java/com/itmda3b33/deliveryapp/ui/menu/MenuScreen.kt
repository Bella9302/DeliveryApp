package com.itmda3b33.deliveryapp.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itmda3b33.deliveryapp.data.model.MenuItem
import com.itmda3b33.deliveryapp.data.model.Money
import com.itmda3b33.deliveryapp.ui.common.LoadingBox
import com.itmda3b33.deliveryapp.ui.common.MessageBox
import com.itmda3b33.deliveryapp.ui.common.ScreenScaffold

/** Marks "add a new item" in the editor state, as opposed to editing an existing one. */
private val NewItem = MenuItem()

@Composable
fun MenuScreen(vm: MenuViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<MenuItem?>(null) }
    var deleting by remember { mutableStateOf<MenuItem?>(null) }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); vm.noticeShown() }
    }

    ScreenScaffold(
        title = state.restaurant?.name ?: "Menu",
        onBack = onBack,
        snackbar = snackbar,
        floatingAction = {
            if (state.restaurant != null) {
                ExtendedFloatingActionButton(onClick = { editing = NewItem }) { Text("Add item") }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.loading -> LoadingBox(modifier)
            state.error != null -> MessageBox(state.error!!, modifier, isError = true, actionLabel = "Try again", onAction = vm::load)
            state.items.isEmpty() -> MessageBox("Your menu is empty. Tap Add item to start.", modifier)
            else -> LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.items, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${item.name} · ${Money.format(item.priceCents)}", style = MaterialTheme.typography.titleMedium)
                            if (item.description.isNotBlank()) Text(item.description)
                            if (!item.available) Text("Not available", color = MaterialTheme.colorScheme.error)
                            Row {
                                TextButton(onClick = { editing = item }) { Text("Edit") }
                                TextButton(onClick = { deleting = item }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        MenuItemDialog(
            item = item.takeUnless { it === NewItem },
            onSave = { name, description, price, available ->
                vm.save(item.takeUnless { it === NewItem }, name, description, price, available)
                    .also { if (it == null) editing = null }
            },
            onDismiss = { editing = null },
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${item.name}?") },
            text = { Text("Customers will no longer see this item.") },
            confirmButton = { TextButton(onClick = { vm.delete(item); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Add or edit form. [onSave] returns an error message, or null when the dialog can close. */
@Composable
private fun MenuItemDialog(
    item: MenuItem?,
    onSave: (name: String, description: String, price: String, available: Boolean) -> String?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var description by remember { mutableStateOf(item?.description.orEmpty()) }
    var price by remember { mutableStateOf(item?.let { Money.format(it.priceCents).removePrefix("R") }.orEmpty()) }
    var available by remember { mutableStateOf(item?.available ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add menu item" else "Edit menu item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; error = null }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") })
                OutlinedTextField(
                    price,
                    { price = it; error = null },
                    label = { Text("Price (R)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = available, onCheckedChange = { available = it })
                    Text("Available", modifier = Modifier.padding(start = 8.dp))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { error = onSave(name, description, price, available) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
