package com.itmda3b33.deliveryapp.data.model

/*
 * The single set of status values from the Systems Brief, section 7.
 * Firestore stores the lowercase [value]; code compares through these enums so a typo
 * cannot introduce a new status by accident.
 */

enum class OrderStatus(val value: String, val label: String) {
    PLACED("placed", "Placed"),
    ACCEPTED("accepted", "Accepted"),
    PREPARING("preparing", "Preparing"),
    OUT_FOR_DELIVERY("out_for_delivery", "Out for delivery"),
    DELIVERED("delivered", "Delivered"),
    CANCELLED("cancelled", "Cancelled");

    companion object {
        fun from(value: String?): OrderStatus? = entries.firstOrNull { it.value == value }
    }
}

enum class ComplaintStatus(val value: String, val label: String) {
    LOGGED("logged", "Logged"),
    IN_PROGRESS("in_progress", "In progress"),
    ESCALATED("escalated", "Escalated"),
    RESOLVED("resolved", "Resolved");

    companion object {
        fun from(value: String?): ComplaintStatus? = entries.firstOrNull { it.value == value }
    }
}

enum class RefundStatus(val value: String, val label: String) {
    REQUESTED("requested", "Requested"),
    PROCESSING("processing", "Processing"),
    APPROVED("approved", "Approved"),
    COMPLETED("completed", "Completed"),
    REJECTED("rejected", "Rejected");

    companion object {
        fun from(value: String?): RefundStatus? = entries.firstOrNull { it.value == value }
    }
}

enum class Role(val value: String, val label: String) {
    CUSTOMER("customer", "Customer"),
    RESTAURANT("restaurant", "Restaurant"),
    DRIVER("driver", "Driver"),
    AGENT("agent", "Support agent"),
    ADMIN("admin", "Admin");

    companion object {
        fun from(value: String?): Role? = entries.firstOrNull { it.value == value }
    }
}

/** What a complaint is about. Only [MISSING_ITEM] can be resolved without a human. */
enum class ComplaintCategory(val value: String, val label: String) {
    MISSING_ITEM("missing_item", "Missing item"),
    WRONG_ITEM("wrong_item", "Wrong item"),
    LATE_DELIVERY("late_delivery", "Late delivery"),
    FOOD_QUALITY("food_quality", "Food quality"),
    SAFETY_HEALTH("safety_health", "Safety or health"),
    OTHER("other", "Other");

    companion object {
        fun from(value: String?): ComplaintCategory? = entries.firstOrNull { it.value == value }
    }
}
