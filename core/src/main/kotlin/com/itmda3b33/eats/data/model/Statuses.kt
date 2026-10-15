package com.itmda3b33.eats.data.model

/*
 * The single sets of status values used everywhere (Systems Brief, section 7).
 * Each enum stores the exact string written to Firestore in [value].
 */

/** A user's role. Decides which screens they can see. */
enum class Role(val value: String) {
    CUSTOMER("customer"),
    RESTAURANT("restaurant"),
    DRIVER("driver"),
    AGENT("agent"),
    ADMIN("admin");

    /** Agents and admins handle escalated complaints and see stats. */
    val isStaff: Boolean get() = this == AGENT || this == ADMIN

    companion object {
        fun from(value: String?): Role? = entries.firstOrNull { it.value == value }
    }
}

enum class OrderStatus(val value: String, val label: String) {
    PLACED("placed", "Placed"),
    ACCEPTED("accepted", "Accepted"),
    PREPARING("preparing", "Preparing"),
    OUT_FOR_DELIVERY("out_for_delivery", "Out for delivery"),
    DELIVERED("delivered", "Delivered"),
    CANCELLED("cancelled", "Cancelled");

    companion object {
        fun from(value: String?): OrderStatus? = entries.firstOrNull { it.value == value }

        /** The normal path an order follows, used by the tracking timeline. */
        val deliveryPath = listOf(PLACED, ACCEPTED, PREPARING, OUT_FOR_DELIVERY, DELIVERED)
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

/** What the complaint is about. Only missing-item claims can be refunded automatically. */
enum class ComplaintCategory(val value: String, val label: String) {
    MISSING_ITEM("missing_item", "Missing item"),
    WRONG_ITEM("wrong_item", "Wrong item"),
    LATE_DELIVERY("late_delivery", "Late delivery"),
    FOOD_QUALITY("food_quality", "Food quality"),
    SAFETY_HEALTH("safety_health", "Safety or health issue"),
    OTHER("other", "Other");

    companion object {
        fun from(value: String?): ComplaintCategory? = entries.firstOrNull { it.value == value }
    }
}

/** Who wrote a message in a complaint's chat history. */
enum class MessageSender(val value: String) {
    CUSTOMER("customer"),
    BOT("bot"),
    AGENT("agent"),
    SYSTEM("system");

    companion object {
        fun from(value: String?): MessageSender = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

/** How a complaint was created. */
enum class ComplaintSource(val value: String) {
    CHAT("chat"),
    FORM("form");

    companion object {
        fun from(value: String?): ComplaintSource = entries.firstOrNull { it.value == value } ?: FORM
    }
}
