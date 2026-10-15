package com.itmda3b33.eats.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.ui.graphics.vector.ImageVector
import com.itmda3b33.eats.data.model.Role

/** Every screen's route. Patterns with {arguments} have a builder function next to them. */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGN_UP = "signup"
    const val FORGOT_PASSWORD = "forgot"

    // Customer
    const val RESTAURANTS = "restaurants"
    const val MENU = "menu/{restaurantId}"
    fun menu(restaurantId: String) = "menu/$restaurantId"
    const val ORDERS = "orders"
    const val ORDER = "order/{orderId}"
    fun order(orderId: String) = "order/$orderId"
    const val COMPLAINTS = "complaints"
    const val COMPLAINT = "complaint/{complaintId}"
    fun complaint(complaintId: String) = "complaint/$complaintId"
    const val NEW_COMPLAINT = "complaint/new/{orderId}"
    fun newComplaint(orderId: String) = "complaint/new/$orderId"
    const val CHAT = "chat?orderId={orderId}"
    fun chat(orderId: String? = null) = if (orderId == null) "chat" else "chat?orderId=$orderId"

    // Restaurant
    const val RESTAURANT_MENU = "restaurant/menu"
    const val MENU_ITEM_EDIT = "restaurant/menu/edit?itemId={itemId}"
    fun menuItemEdit(itemId: String? = null) = if (itemId == null) "restaurant/menu/edit" else "restaurant/menu/edit?itemId=$itemId"
    const val RESTAURANT_ORDERS = "restaurant/orders"

    // Agent and admin
    const val AGENT_QUEUE = "agent/queue"
    const val AGENT_COMPLAINT = "agent/complaint/{complaintId}"
    fun agentComplaint(complaintId: String) = "agent/complaint/$complaintId"
    const val STATS = "stats"

    // Driver
    const val DRIVER_HOME = "driver"

    val authRoutes = setOf(SPLASH, LOGIN, SIGN_UP, FORGOT_PASSWORD)

    fun homeFor(role: Role): String = when (role) {
        Role.CUSTOMER -> RESTAURANTS
        Role.RESTAURANT -> RESTAURANT_MENU
        Role.DRIVER -> DRIVER_HOME
        Role.AGENT, Role.ADMIN -> AGENT_QUEUE
    }
}

/** A bottom bar tab. [pattern] is the route pattern used to highlight the selected tab. */
data class Tab(val label: String, val icon: ImageVector, val route: String, val pattern: String = route)

fun tabsFor(role: Role): List<Tab> = when (role) {
    Role.CUSTOMER -> listOf(
        Tab("Restaurants", Icons.Outlined.Storefront, Routes.RESTAURANTS),
        Tab("Orders", Icons.AutoMirrored.Outlined.ReceiptLong, Routes.ORDERS),
        Tab("Complaints", Icons.Outlined.ReportProblem, Routes.COMPLAINTS),
        Tab("Help", Icons.Outlined.SupportAgent, Routes.chat(), Routes.CHAT),
    )
    Role.RESTAURANT -> listOf(
        Tab("Menu", Icons.Outlined.RestaurantMenu, Routes.RESTAURANT_MENU),
        Tab("Orders", Icons.AutoMirrored.Outlined.ReceiptLong, Routes.RESTAURANT_ORDERS),
    )
    Role.AGENT, Role.ADMIN -> listOf(
        Tab("Queue", Icons.Outlined.Inbox, Routes.AGENT_QUEUE),
        Tab("Stats", Icons.Outlined.BarChart, Routes.STATS),
    )
    Role.DRIVER -> emptyList()
}
