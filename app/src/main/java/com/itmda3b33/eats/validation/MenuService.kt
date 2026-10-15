package com.itmda3b33.eats.validation

import com.itmda3b33.eats.data.RestaurantRepository
import com.itmda3b33.eats.data.model.MenuItem
import com.itmda3b33.eats.data.model.UserProfile

/** Restaurant menu actions (Module B). The permission and the form are checked before anything is saved. */
class MenuService(private val restaurants: RestaurantRepository) {

    /** Adds a new item when [itemId] is null, otherwise updates it. Field errors come back as [MenuSaveResult.FieldErrors]. */
    suspend fun save(
        user: UserProfile,
        restaurantId: String,
        itemId: String?,
        name: String,
        description: String,
        priceText: String,
        available: Boolean,
    ): ServiceResult<MenuSaveResult> = guarded {
        MenuRules.validateCanEdit(user, restaurantId)?.let { return@guarded ServiceResult.Invalid(it) }
        val input = MenuRules.validateInput(name, description, priceText)
        if (!input.isValid) return@guarded ServiceResult.Success(MenuSaveResult.FieldErrors(input.errors))

        val item = MenuItem(
            id = itemId.orEmpty(),
            restaurantId = restaurantId,
            name = input.name,
            description = input.description,
            priceCents = input.priceCents!!,
            available = available,
        )
        if (itemId == null) restaurants.addMenuItem(item) else restaurants.updateMenuItem(item)
        ServiceResult.Success(MenuSaveResult.Saved)
    }

    suspend fun setAvailable(user: UserProfile, item: MenuItem, available: Boolean): ServiceResult<Unit> = guarded {
        MenuRules.validateCanEdit(user, item.restaurantId)?.let { return@guarded ServiceResult.Invalid(it) }
        restaurants.updateMenuItem(item.copy(available = available))
        ServiceResult.Success(Unit)
    }

    suspend fun delete(user: UserProfile, item: MenuItem): ServiceResult<Unit> = guarded {
        MenuRules.validateCanEdit(user, item.restaurantId)?.let { return@guarded ServiceResult.Invalid(it) }
        restaurants.deleteMenuItem(item.restaurantId, item.id)
        ServiceResult.Success(Unit)
    }
}

sealed interface MenuSaveResult {
    data object Saved : MenuSaveResult
    data class FieldErrors(val errors: Map<String, String>) : MenuSaveResult
}
