package com.itmda3b33.deliveryapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itmda3b33.deliveryapp.data.model.Role
import com.itmda3b33.deliveryapp.data.model.User
import com.itmda3b33.deliveryapp.ui.Routes
import com.itmda3b33.deliveryapp.ui.common.ScreenScaffold

/** A menu entry on the home screen. */
private data class HomeTile(val title: String, val subtitle: String, val route: String)

/** The tiles each role may see. Users only see screens for their role (Systems Brief, section 9). */
private fun tilesFor(role: Role?): List<HomeTile> = when (role) {
    Role.CUSTOMER -> listOf(
        HomeTile("My orders", "Track your orders", Routes.ORDERS),
        HomeTile("Report a problem", "Chat with our assistant", Routes.chat(null)),
        HomeTile("My complaints", "See, edit or cancel complaints", Routes.COMPLAINTS),
    )
    Role.RESTAURANT -> listOf(HomeTile("Menu", "Add, edit and remove menu items", Routes.MENU))
    Role.AGENT -> listOf(
        HomeTile("Escalated complaints", "Complaints waiting for a person", Routes.AGENT),
        HomeTile("Stats", "Complaints and refunds at a glance", Routes.STATS),
    )
    Role.ADMIN -> listOf(
        HomeTile("Escalated complaints", "Complaints waiting for a person", Routes.AGENT),
        HomeTile("Stats", "Complaints and refunds at a glance", Routes.STATS),
    )
    Role.DRIVER, null -> emptyList()
}

@Composable
fun HomeScreen(user: User, onOpen: (route: String) -> Unit, onSignOut: () -> Unit) {
    val role = Role.from(user.role)
    val tiles = tilesFor(role)

    ScreenScaffold(
        title = "Hi, ${user.name.ifBlank { "there" }}",
        actions = { TextButton(onClick = onSignOut) { Text("Sign out") } },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Signed in as ${role?.label ?: user.role}", style = MaterialTheme.typography.labelLarge)
            if (tiles.isEmpty()) {
                Text("There are no screens for your role in this version of the app.")
            }
            tiles.forEach { tile ->
                Card(onClick = { onOpen(tile.route) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tile.title, style = MaterialTheme.typography.titleMedium)
                        Text(tile.subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
