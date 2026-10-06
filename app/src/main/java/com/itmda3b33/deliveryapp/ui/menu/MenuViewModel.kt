package com.itmda3b33.deliveryapp.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itmda3b33.deliveryapp.data.MenuRepository
import com.itmda3b33.deliveryapp.data.model.MenuItem
import com.itmda3b33.deliveryapp.data.model.Restaurant
import com.itmda3b33.deliveryapp.ui.common.runAction
import com.itmda3b33.deliveryapp.ui.common.toUserMessage
import com.itmda3b33.deliveryapp.validation.EditRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MenuState(
    val loading: Boolean = true,
    val restaurant: Restaurant? = null,
    val items: List<MenuItem> = emptyList(),
    val error: String? = null,
    val notice: String? = null,
)

/** Module B: a restaurant owner creates, reads, updates and deletes their menu items. */
class MenuViewModel(private val ownerId: String, private val menus: MenuRepository) : ViewModel() {

    private val _state = MutableStateFlow(MenuState())
    val state: StateFlow<MenuState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val restaurant = menus.restaurantOwnedBy(ownerId)
                if (restaurant == null) {
                    _state.update { it.copy(loading = false, error = "No restaurant is linked to your account.") }
                } else {
                    val items = menus.items(restaurant.id)
                    _state.update { it.copy(loading = false, restaurant = restaurant, items = items) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    /**
     * Adds a new item when [existing] is null, otherwise updates it.
     * Returns an error message for the form, or null once the save has started.
     */
    fun save(existing: MenuItem?, name: String, description: String, priceText: String, available: Boolean): String? {
        val restaurantId = _state.value.restaurant?.id ?: return "No restaurant is linked to your account."
        val price = when (val check = EditRules.checkMenuItem(name, priceText)) {
            is EditRules.MenuCheck.Invalid -> return check.message
            is EditRules.MenuCheck.Valid -> check.priceCents
        }
        val item = MenuItem(
            id = existing?.id.orEmpty(),
            name = name.trim(),
            description = description.trim(),
            priceCents = price,
            available = available,
        )
        if (existing == null) {
            runAction("${item.name} added.", ::finish) { menus.add(restaurantId, item) }
        } else {
            runAction("${item.name} updated.", ::finish) { menus.update(restaurantId, item) }
        }
        return null
    }

    fun delete(item: MenuItem) {
        val restaurantId = _state.value.restaurant?.id ?: return
        runAction("${item.name} deleted.", ::finish) { menus.delete(restaurantId, item.id) }
    }

    fun noticeShown() = _state.update { it.copy(notice = null) }

    private fun finish(message: String) {
        _state.update { it.copy(notice = message) }
        load()
    }
}
