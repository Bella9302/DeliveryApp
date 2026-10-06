package com.itmda3b33.deliveryapp.validation

import com.itmda3b33.deliveryapp.data.model.Complaint
import com.itmda3b33.deliveryapp.data.model.ComplaintStatus
import com.itmda3b33.deliveryapp.data.model.Money
import com.itmda3b33.deliveryapp.data.model.Refund
import com.itmda3b33.deliveryapp.data.model.RefundStatus

/** Rules for the screens where people edit records by hand (Modules A and B, and the agent screen). */
object EditRules {

    /** Customers may change or delete a complaint only until someone starts working on it. */
    fun customerCanEdit(complaint: Complaint): Boolean =
        ComplaintStatus.from(complaint.status) == ComplaintStatus.LOGGED

    /** Agents decide only refunds that are still waiting for a person. */
    fun agentCanDecide(refund: Refund): Boolean =
        RefundStatus.from(refund.status) == RefundStatus.REQUESTED

    /** Returns an error message for a complaint description, or null if it is valid. */
    fun checkDescription(text: String): String? = when {
        text.isBlank() -> "Please describe the problem."
        text.length > 1000 -> "Please keep the description under 1000 characters."
        else -> null
    }

    /** Checks a menu item form. Returns the price in cents, or an error message. */
    fun checkMenuItem(name: String, priceText: String): MenuCheck {
        if (name.isBlank()) return MenuCheck.Invalid("Enter a name.")
        val cents = Money.parse(priceText) ?: return MenuCheck.Invalid("Enter a price such as 45.50.")
        if (cents <= 0) return MenuCheck.Invalid("The price must be more than R0.")
        return MenuCheck.Valid(cents)
    }

    sealed interface MenuCheck {
        data class Valid(val priceCents: Long) : MenuCheck
        data class Invalid(val message: String) : MenuCheck
    }
}
