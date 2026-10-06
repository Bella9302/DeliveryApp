package com.itmda3b33.deliveryapp

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.itmda3b33.deliveryapp.ai.ChatbotService
import com.itmda3b33.deliveryapp.data.AuthRepository
import com.itmda3b33.deliveryapp.data.ComplaintRepository
import com.itmda3b33.deliveryapp.data.FirebaseAuthRepository
import com.itmda3b33.deliveryapp.data.FirestoreComplaintRepository
import com.itmda3b33.deliveryapp.data.FirestoreMenuRepository
import com.itmda3b33.deliveryapp.data.FirestoreOrderRepository
import com.itmda3b33.deliveryapp.data.FirestoreRefundRepository
import com.itmda3b33.deliveryapp.data.FirestoreUserRepository
import com.itmda3b33.deliveryapp.data.MenuRepository
import com.itmda3b33.deliveryapp.data.OrderRepository
import com.itmda3b33.deliveryapp.data.RefundRepository
import com.itmda3b33.deliveryapp.data.UserRepository
import com.itmda3b33.deliveryapp.validation.ActionGateway

/** Creates the app's shared objects once, so screens get them without knowing about Firebase. */
class AppContainer {
    private val db = FirebaseFirestore.getInstance()

    val auth: AuthRepository = FirebaseAuthRepository(FirebaseAuth.getInstance())
    val users: UserRepository = FirestoreUserRepository(db)
    val orders: OrderRepository = FirestoreOrderRepository(db)
    val complaints: ComplaintRepository = FirestoreComplaintRepository(db)
    val refunds: RefundRepository = FirestoreRefundRepository(db)
    val menus: MenuRepository = FirestoreMenuRepository(db)

    /** A new chatbot for one chat. Its actions are checked against [customerId]. */
    fun newChatbot(customerId: String, orderId: String?) = ChatbotService(
        handler = ActionGateway(customerId, orders, complaints, refunds),
        contextOrderId = orderId,
    )
}
