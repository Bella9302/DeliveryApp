package com.itmda3b33.eats.validation

import com.itmda3b33.eats.data.model.Role
import com.itmda3b33.eats.data.model.UserProfile

/** A menu item form after checking. [errors] maps a field name to its message. */
data class MenuItemInput(
    val name: String,
    val description: String,
    val priceCents: Long?,
    val errors: Map<String, String>,
) {
    val isValid: Boolean get() = errors.isEmpty()

    companion object {
        const val FIELD_NAME = "name"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_PRICE = "price"
    }
}

/** Rules for restaurant menu items (Module B). */
object MenuRules {

    const val MAX_NAME = 60
    const val MAX_DESCRIPTION = 200
    const val MAX_PRICE_CENTS = 1_000_000L // R10 000

    /** Only the restaurant's own user (or an admin) may change its menu. */
    fun validateCanEdit(user: UserProfile, restaurantId: String): RuleFailure? = when {
        user.role == Role.ADMIN -> null
        user.role == Role.RESTAURANT && user.restaurantId == restaurantId -> null
        else -> RuleFailure(Check.PERMISSION, "not_your_menu", "You can only change your own restaurant's menu.")
    }

    fun validateInput(name: String, description: String, priceText: String): MenuItemInput {
        val errors = mutableMapOf<String, String>()
        val cleanName = name.trim()
        val cleanDescription = description.trim()
        val price = parsePriceCents(priceText)

        if (cleanName.isEmpty()) errors[MenuItemInput.FIELD_NAME] = "Enter a name."
        else if (cleanName.length > MAX_NAME) errors[MenuItemInput.FIELD_NAME] = "Keep the name under $MAX_NAME characters."

        if (cleanDescription.length > MAX_DESCRIPTION) {
            errors[MenuItemInput.FIELD_DESCRIPTION] = "Keep the description under $MAX_DESCRIPTION characters."
        }

        when {
            price == null -> errors[MenuItemInput.FIELD_PRICE] = "Enter a price like 79,00."
            price <= 0 -> errors[MenuItemInput.FIELD_PRICE] = "The price must be more than R0."
            price > MAX_PRICE_CENTS -> errors[MenuItemInput.FIELD_PRICE] = "The price must be R10 000 or less."
        }

        return MenuItemInput(cleanName, cleanDescription, price, errors)
    }

    /** Reads "79", "79,5", "79.50" or "R79,00" as cents. Returns null when the text is not a price. */
    fun parsePriceCents(text: String): Long? {
        val cleaned = text.trim().removePrefix("R").removePrefix("r").replace(" ", "").replace(',', '.')
        if (!Regex("""\d{1,7}(\.\d{1,2})?""").matches(cleaned)) return null
        val parts = cleaned.split('.')
        val rands = parts[0].toLong()
        val cents = parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0
        return rands * 100 + cents
    }
}
