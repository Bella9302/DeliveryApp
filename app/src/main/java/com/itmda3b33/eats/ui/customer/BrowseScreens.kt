package com.itmda3b33.eats.ui.customer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.itmda3b33.eats.data.RestaurantRepository
import com.itmda3b33.eats.data.model.MenuItem
import com.itmda3b33.eats.data.model.Restaurant
import com.itmda3b33.eats.data.model.formatRand
import com.itmda3b33.eats.ui.common.Caption
import com.itmda3b33.eats.ui.common.EatsCard
import com.itmda3b33.eats.ui.common.EatsPlateLogo
import com.itmda3b33.eats.ui.common.EatsScaffold
import com.itmda3b33.eats.ui.common.EmptyView
import com.itmda3b33.eats.ui.common.LinkButton
import com.itmda3b33.eats.ui.common.StateContent
import com.itmda3b33.eats.ui.common.StatusPill
import com.itmda3b33.eats.ui.common.Tone
import com.itmda3b33.eats.ui.common.UiState
import com.itmda3b33.eats.ui.common.asUiState
import com.itmda3b33.eats.ui.common.eatsViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

class RestaurantsViewModel(restaurants: RestaurantRepository) : ViewModel() {
    val state: StateFlow<UiState<List<Restaurant>>> = restaurants.observeRestaurants().asUiState(viewModelScope)
}

/** Customer home: the restaurant list. */
@Composable
fun RestaurantsScreen(onOpen: (String) -> Unit, onSignOut: () -> Unit) {
    val vm = eatsViewModel { RestaurantsViewModel(it.restaurantRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    EatsScaffold { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    EatsPlateLogo(plateSize = 36.dp)
                    Spacer(Modifier.weight(1f))
                    LinkButton("Sign out", onClick = onSignOut)
                }
            }
            item {
                Text(
                    "Restaurants near you",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            when (val s = state) {
                is UiState.Ready -> {
                    if (s.data.isEmpty()) item { EmptyView("No restaurants yet.") }
                    items(s.data, key = { it.id }) { restaurant ->
                        EatsCard(onClick = { onOpen(restaurant.id) }) {
                            Text(restaurant.name, style = MaterialTheme.typography.titleMedium)
                            Caption("${restaurant.cuisine} • ${restaurant.etaMinMinutes}-${restaurant.etaMaxMinutes} min • ★ ${restaurant.rating}")
                        }
                    }
                }
                else -> item { StateContent(s) {} }
            }
        }
    }
}

data class MenuState(val restaurant: Restaurant?, val items: List<MenuItem>)

class MenuViewModel(restaurants: RestaurantRepository, restaurantId: String) : ViewModel() {
    val state: StateFlow<UiState<MenuState>> =
        combine(restaurants.observeRestaurant(restaurantId), restaurants.observeMenu(restaurantId)) { r, items -> MenuState(r, items) }
            .asUiState(viewModelScope)
}

/** A restaurant's menu, read-only for customers. Placing orders is in the "if time allows" scope. */
@Composable
fun MenuScreen(restaurantId: String, onBack: () -> Unit) {
    val vm = eatsViewModel(key = "menu-$restaurantId") { MenuViewModel(it.restaurantRepository, restaurantId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val title = (state as? UiState.Ready)?.data?.restaurant?.name ?: "Menu"

    EatsScaffold(title = title, onBack = onBack) { padding ->
        StateContent(state) { menu ->
            if (menu.items.isEmpty()) {
                EmptyView("This menu is empty.")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(menu.items, key = { it.id }) { item -> MenuItemCard(item) }
                }
            }
        }
    }
}

@Composable
internal fun MenuItemCard(item: MenuItem, trailing: @Composable () -> Unit = {}) {
    EatsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                if (item.description.isNotBlank()) Caption(item.description)
                Text(formatRand(item.priceCents), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            }
            if (!item.available) {
                StatusPill("Sold out", Tone.DANGER)
                Spacer(Modifier.width(8.dp))
            }
            trailing()
        }
    }
}
