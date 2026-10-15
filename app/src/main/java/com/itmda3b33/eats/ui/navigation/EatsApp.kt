package com.itmda3b33.eats.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.itmda3b33.eats.data.model.Role
import com.itmda3b33.eats.data.model.UserProfile
import com.itmda3b33.eats.ui.agent.AgentComplaintScreen
import com.itmda3b33.eats.ui.agent.AgentQueueScreen
import com.itmda3b33.eats.ui.auth.ForgotPasswordScreen
import com.itmda3b33.eats.ui.auth.LoginScreen
import com.itmda3b33.eats.ui.auth.SignUpScreen
import com.itmda3b33.eats.ui.chat.ChatScreen
import com.itmda3b33.eats.ui.common.ErrorView
import com.itmda3b33.eats.ui.common.LoadingView
import com.itmda3b33.eats.ui.common.eatsViewModel
import com.itmda3b33.eats.ui.customer.ComplaintDetailScreen
import com.itmda3b33.eats.ui.customer.ComplaintsScreen
import com.itmda3b33.eats.ui.customer.MenuScreen
import com.itmda3b33.eats.ui.customer.NewComplaintScreen
import com.itmda3b33.eats.ui.customer.OrderDetailScreen
import com.itmda3b33.eats.ui.customer.OrdersScreen
import com.itmda3b33.eats.ui.customer.RestaurantsScreen
import com.itmda3b33.eats.ui.driver.DriverHomeScreen
import com.itmda3b33.eats.ui.restaurant.MenuItemEditScreen
import com.itmda3b33.eats.ui.restaurant.RestaurantMenuScreen
import com.itmda3b33.eats.ui.restaurant.RestaurantOrdersScreen
import com.itmda3b33.eats.ui.session.SessionState
import com.itmda3b33.eats.ui.session.SessionViewModel
import com.itmda3b33.eats.ui.splash.SplashScreen
import com.itmda3b33.eats.ui.stats.StatsScreen

private val CUSTOMER = setOf(Role.CUSTOMER)
private val RESTAURANT = setOf(Role.RESTAURANT)
private val STAFF = setOf(Role.AGENT, Role.ADMIN)
private val DRIVER = setOf(Role.DRIVER)

/** The app shell: session handling, role-based bottom bar and every screen in one navigation graph. */
@Composable
fun EatsApp() {
    val session = eatsViewModel { SessionViewModel(it.authRepository) }
    val state by session.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val profile = (state as? SessionState.SignedIn)?.profile

    // Send people to the right place when they sign in or out.
    LaunchedEffect(state) {
        val route = nav.currentDestination?.route
        when (val current = state) {
            SessionState.SignedOut ->
                if (route == null || route == Routes.SPLASH || route !in Routes.authRoutes) nav.resetTo(Routes.LOGIN)
            is SessionState.SignedIn ->
                if (route == null || route in Routes.authRoutes) nav.resetTo(Routes.homeFor(current.profile.role))
            // The splash screen shows the error with "Try again" and "Sign out".
            is SessionState.ProfileError -> if (route != Routes.SPLASH) nav.resetTo(Routes.SPLASH)
            SessionState.Loading -> Unit
        }
    }

    val tabs = profile?.let { tabsFor(it.role) }.orEmpty()
    val showBottomBar = tabs.any { it.pattern == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar && profile != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = tab.pattern == currentRoute,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Routes.homeFor(profile.role)) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            // The graph captures only stable objects, so NavHost keeps the same graph between recompositions.
            authGraph(nav, session)
            customerGraph(nav, session)
            restaurantGraph(nav, session)
            staffGraph(nav, session)
        }
    }
}

private fun NavHostController.resetTo(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** Shows [content] only to the allowed roles. Firestore rules enforce the same on the data. */
@Composable
private fun Gate(session: SessionViewModel, allowed: Set<Role>, content: @Composable (UserProfile) -> Unit) {
    val state by session.state.collectAsStateWithLifecycle()
    val profile = (state as? SessionState.SignedIn)?.profile
    when {
        profile == null -> LoadingView()
        profile.role in allowed -> content(profile)
        else -> ErrorView("This screen is not available for your account.")
    }
}

private fun NavBackStackEntry.arg(name: String): String = arguments?.getString(name).orEmpty()

private fun NavGraphBuilder.authGraph(nav: NavHostController, session: SessionViewModel) {
    composable(Routes.SPLASH) {
        val state by session.state.collectAsStateWithLifecycle()
        SplashScreen(state = state, onRetry = session::retry, onSignOut = session::signOut)
    }
    composable(Routes.LOGIN) {
        LoginScreen(
            onSignUp = { nav.navigate(Routes.SIGN_UP) },
            onForgotPassword = { nav.navigate(Routes.FORGOT_PASSWORD) },
        )
    }
    composable(Routes.SIGN_UP) {
        SignUpScreen(onBack = { nav.popBackStack() })
    }
    composable(Routes.FORGOT_PASSWORD) {
        ForgotPasswordScreen(onBack = { nav.popBackStack() })
    }
}

private fun NavGraphBuilder.customerGraph(nav: NavHostController, session: SessionViewModel) {
    composable(Routes.RESTAURANTS) {
        Gate(session, CUSTOMER) { RestaurantsScreen(onOpen = { nav.navigate(Routes.menu(it)) }, onSignOut = session::signOut) }
    }
    composable(Routes.MENU, arguments = listOf(navArgument("restaurantId") { type = NavType.StringType })) { entry ->
        Gate(session, CUSTOMER) { MenuScreen(restaurantId = entry.arg("restaurantId"), onBack = { nav.popBackStack() }) }
    }
    composable(Routes.ORDERS) {
        Gate(session, CUSTOMER) { profile -> OrdersScreen(profile = profile, onOpen = { nav.navigate(Routes.order(it)) }) }
    }
    composable(Routes.ORDER, arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { entry ->
        Gate(session, CUSTOMER) {
            OrderDetailScreen(
                orderId = entry.arg("orderId"),
                onBack = { nav.popBackStack() },
                onChat = { nav.navigate(Routes.chat(it)) },
                onNewComplaint = { nav.navigate(Routes.newComplaint(it)) },
            )
        }
    }
    composable(Routes.COMPLAINTS) {
        Gate(session, CUSTOMER) { profile ->
            ComplaintsScreen(profile = profile, onOpen = { nav.navigate(Routes.complaint(it)) }, onChat = { nav.navigate(Routes.chat()) })
        }
    }
    composable(Routes.COMPLAINT, arguments = listOf(navArgument("complaintId") { type = NavType.StringType })) { entry ->
        Gate(session, CUSTOMER) { profile ->
            ComplaintDetailScreen(profile = profile, complaintId = entry.arg("complaintId"), onBack = { nav.popBackStack() })
        }
    }
    composable(Routes.NEW_COMPLAINT, arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { entry ->
        Gate(session, CUSTOMER) { profile ->
            NewComplaintScreen(
                profile = profile,
                orderId = entry.arg("orderId"),
                onBack = { nav.popBackStack() },
                onCreated = { id ->
                    nav.popBackStack()
                    nav.navigate(Routes.complaint(id))
                },
            )
        }
    }
    composable(
        Routes.CHAT,
        arguments = listOf(navArgument("orderId") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) { entry ->
        Gate(session, CUSTOMER) { profile ->
            val orderId = entry.arguments?.getString("orderId")
            val back: () -> Unit = { nav.popBackStack() }
            ChatScreen(
                profile = profile,
                orderId = orderId,
                // Opened from an order: show a back arrow. Opened from the Help tab: no back arrow.
                onBack = if (orderId != null) back else null,
                onOpenComplaint = { nav.navigate(Routes.complaint(it)) },
            )
        }
    }
}

private fun NavGraphBuilder.restaurantGraph(nav: NavHostController, session: SessionViewModel) {
    composable(Routes.RESTAURANT_MENU) {
        Gate(session, RESTAURANT) { profile ->
            RestaurantMenuScreen(
                profile = profile,
                onAdd = { nav.navigate(Routes.menuItemEdit()) },
                onEdit = { nav.navigate(Routes.menuItemEdit(it)) },
                onSignOut = session::signOut,
            )
        }
    }
    composable(
        Routes.MENU_ITEM_EDIT,
        arguments = listOf(navArgument("itemId") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) { entry ->
        Gate(session, RESTAURANT) { profile ->
            MenuItemEditScreen(profile = profile, itemId = entry.arguments?.getString("itemId"), onBack = { nav.popBackStack() })
        }
    }
    composable(Routes.RESTAURANT_ORDERS) {
        Gate(session, RESTAURANT) { profile -> RestaurantOrdersScreen(profile = profile) }
    }
    composable(Routes.DRIVER_HOME) {
        Gate(session, DRIVER) { profile -> DriverHomeScreen(profile = profile, onSignOut = session::signOut) }
    }
}

private fun NavGraphBuilder.staffGraph(nav: NavHostController, session: SessionViewModel) {
    composable(Routes.AGENT_QUEUE) {
        Gate(session, STAFF) { AgentQueueScreen(onOpen = { nav.navigate(Routes.agentComplaint(it)) }, onSignOut = session::signOut) }
    }
    composable(Routes.AGENT_COMPLAINT, arguments = listOf(navArgument("complaintId") { type = NavType.StringType })) { entry ->
        Gate(session, STAFF) { profile ->
            AgentComplaintScreen(profile = profile, complaintId = entry.arg("complaintId"), onBack = { nav.popBackStack() })
        }
    }
    composable(Routes.STATS) {
        Gate(session, STAFF) { StatsScreen(onSignOut = session::signOut) }
    }
}
