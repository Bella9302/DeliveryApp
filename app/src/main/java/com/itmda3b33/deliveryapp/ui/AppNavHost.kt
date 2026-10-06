package com.itmda3b33.deliveryapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.itmda3b33.deliveryapp.AppContainer
import com.itmda3b33.deliveryapp.data.model.Role
import com.itmda3b33.deliveryapp.ui.agent.AgentComplaintScreen
import com.itmda3b33.deliveryapp.ui.agent.AgentQueueScreen
import com.itmda3b33.deliveryapp.ui.agent.AgentViewModel
import com.itmda3b33.deliveryapp.ui.auth.LoginScreen
import com.itmda3b33.deliveryapp.ui.auth.ResetPasswordScreen
import com.itmda3b33.deliveryapp.ui.auth.SessionState
import com.itmda3b33.deliveryapp.ui.auth.SessionViewModel
import com.itmda3b33.deliveryapp.ui.auth.SignUpScreen
import com.itmda3b33.deliveryapp.ui.chat.ChatScreen
import com.itmda3b33.deliveryapp.ui.chat.ChatViewModel
import com.itmda3b33.deliveryapp.ui.common.LoadingBox
import com.itmda3b33.deliveryapp.ui.common.MessageBox
import com.itmda3b33.deliveryapp.ui.complaints.ComplaintsScreen
import com.itmda3b33.deliveryapp.ui.complaints.ComplaintsViewModel
import com.itmda3b33.deliveryapp.ui.home.HomeScreen
import com.itmda3b33.deliveryapp.ui.menu.MenuScreen
import com.itmda3b33.deliveryapp.ui.menu.MenuViewModel
import com.itmda3b33.deliveryapp.ui.orders.OrderDetailScreen
import com.itmda3b33.deliveryapp.ui.orders.OrdersScreen
import com.itmda3b33.deliveryapp.ui.orders.OrdersViewModel
import com.itmda3b33.deliveryapp.ui.stats.StatsScreen
import com.itmda3b33.deliveryapp.ui.stats.StatsViewModel

/** Route names for every screen. */
object Routes {
    const val LOGIN = "login"
    const val SIGN_UP = "signup"
    const val RESET = "reset"
    const val HOME = "home"
    const val ORDERS = "orders"
    const val ORDER = "order/{orderId}"
    const val CHAT = "chat?orderId={orderId}"
    const val COMPLAINTS = "complaints"
    const val MENU = "menu"
    const val AGENT = "agent"
    const val AGENT_COMPLAINT = "agent/{complaintId}"
    const val STATS = "stats"

    fun order(orderId: String) = "order/$orderId"
    fun chat(orderId: String?) = if (orderId == null) "chat" else "chat?orderId=$orderId"
    fun agentComplaint(complaintId: String) = "agent/$complaintId"
}

/** Which roles may open each signed-in screen. */
private val customerOnly = setOf(Role.CUSTOMER)
private val staffOnly = setOf(Role.AGENT, Role.ADMIN)
private val restaurantOnly = setOf(Role.RESTAURANT)

/**
 * All screens and how to move between them. Signed-out users only reach the login screens, and
 * signed-in users only reach screens for their role.
 */
@Composable
fun AppNavHost(container: AppContainer, nav: NavHostController = rememberNavController()) {
    val sessionVm: SessionViewModel = viewModel { SessionViewModel(container.auth, container.users) }
    val session by sessionVm.session.collectAsStateWithLifecycle()
    val form by sessionVm.form.collectAsStateWithLifecycle()

    if (session == SessionState.Loading) {
        LoadingBox()
        return
    }
    val signedIn = session as? SessionState.SignedIn

    // Send the user to the right start screen whenever they sign in or out.
    LaunchedEffect(signedIn?.user?.id) {
        val target = if (signedIn == null) Routes.LOGIN else Routes.HOME
        nav.navigate(target) { popUpTo(nav.graph.id) { inclusive = true } }
    }

    /** Shows [content] only if the signed-in user has one of [roles]. */
    @Composable
    fun RoleGate(roles: Set<Role>, content: @Composable (uid: String) -> Unit) {
        val user = signedIn?.user
        if (user != null && Role.from(user.role) in roles) {
            content(user.id)
        } else {
            MessageBox("This screen is not available for your account.", actionLabel = "Go home") {
                nav.navigate(Routes.HOME) { popUpTo(nav.graph.id) { inclusive = true } }
            }
        }
    }

    val back: () -> Unit = { nav.popBackStack() }

    NavHost(navController = nav, startDestination = if (signedIn == null) Routes.LOGIN else Routes.HOME) {
        composable(Routes.LOGIN) {
            LoginScreen(
                form = form,
                onSignIn = sessionVm::signIn,
                onCreateAccount = { sessionVm.clearMessages(); nav.navigate(Routes.SIGN_UP) },
                onForgotPassword = { sessionVm.clearMessages(); nav.navigate(Routes.RESET) },
            )
        }
        composable(Routes.SIGN_UP) {
            SignUpScreen(form = form, onSignUp = sessionVm::signUp, onBack = { sessionVm.clearMessages(); back() })
        }
        composable(Routes.RESET) {
            ResetPasswordScreen(form = form, onSend = sessionVm::sendPasswordReset, onBack = { sessionVm.clearMessages(); back() })
        }
        composable(Routes.HOME) {
            val user = signedIn?.user
            if (user == null) LoadingBox() else HomeScreen(user, onOpen = { nav.navigate(it) }, onSignOut = sessionVm::signOut)
        }

        // Customer screens
        composable(Routes.ORDERS) {
            RoleGate(customerOnly) { uid ->
                val vm: OrdersViewModel = viewModel { OrdersViewModel(uid, container.orders) }
                OrdersScreen(vm, onOpen = { nav.navigate(Routes.order(it)) }, onBack = back)
            }
        }
        composable(Routes.ORDER, arguments = listOf(navArgument("orderId") { type = NavType.StringType })) { entry ->
            val orderId = entry.arguments?.getString("orderId").orEmpty()
            RoleGate(customerOnly) { uid ->
                val vm: OrdersViewModel = viewModel { OrdersViewModel(uid, container.orders) }
                OrderDetailScreen(vm, orderId, onReportProblem = { nav.navigate(Routes.chat(orderId)) }, onBack = back)
            }
        }
        composable(
            Routes.CHAT,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val orderId = entry.arguments?.getString("orderId")
            RoleGate(customerOnly) { uid ->
                val vm: ChatViewModel = viewModel { ChatViewModel(container.newChatbot(uid, orderId)) }
                ChatScreen(vm, onOpenComplaints = { nav.navigate(Routes.COMPLAINTS) }, onBack = back)
            }
        }
        composable(Routes.COMPLAINTS) {
            RoleGate(customerOnly) { uid ->
                val vm: ComplaintsViewModel = viewModel { ComplaintsViewModel(uid, container.complaints) }
                ComplaintsScreen(vm, onNewComplaint = { nav.navigate(Routes.chat(null)) }, onBack = back)
            }
        }

        // Restaurant screens
        composable(Routes.MENU) {
            RoleGate(restaurantOnly) { uid ->
                val vm: MenuViewModel = viewModel { MenuViewModel(uid, container.menus) }
                MenuScreen(vm, onBack = back)
            }
        }

        // Agent and admin screens
        composable(Routes.AGENT) {
            RoleGate(staffOnly) { uid ->
                val vm: AgentViewModel = viewModel { AgentViewModel(uid, container.complaints, container.refunds, container.orders) }
                AgentQueueScreen(vm, onOpen = { nav.navigate(Routes.agentComplaint(it)) }, onBack = back)
            }
        }
        composable(Routes.AGENT_COMPLAINT, arguments = listOf(navArgument("complaintId") { type = NavType.StringType })) { entry ->
            val complaintId = entry.arguments?.getString("complaintId").orEmpty()
            RoleGate(staffOnly) { uid ->
                val vm: AgentViewModel = viewModel { AgentViewModel(uid, container.complaints, container.refunds, container.orders) }
                AgentComplaintScreen(vm, complaintId, onBack = back)
            }
        }
        composable(Routes.STATS) {
            RoleGate(staffOnly) {
                val vm: StatsViewModel = viewModel { StatsViewModel(container.complaints, container.refunds) }
                StatsScreen(vm, onBack = back)
            }
        }
    }
}
