package com.itmda3b33.eats

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.itmda3b33.eats.ai.ChatbotService
import com.itmda3b33.eats.data.AuthRepository
import com.itmda3b33.eats.data.ComplaintRepository
import com.itmda3b33.eats.data.OrderRepository
import com.itmda3b33.eats.data.RefundRepository
import com.itmda3b33.eats.data.RestaurantRepository
import com.itmda3b33.eats.data.model.TranscriptLine
import com.itmda3b33.eats.validation.ActionGateway
import com.itmda3b33.eats.validation.ComplaintService
import com.itmda3b33.eats.validation.MenuService
import com.itmda3b33.eats.validation.RefundPolicy
import com.itmda3b33.eats.validation.StaffService

/** Creates the app's single instances and wires the four layers together: ui -> ai / validation -> data. */
class AppContainer {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Data layer: the only code that talks to Firebase.
    val authRepository = AuthRepository(auth, db)
    val restaurantRepository = RestaurantRepository(db)
    val orderRepository = OrderRepository(db)
    val complaintRepository = ComplaintRepository(db)
    val refundRepository = RefundRepository(db)

    // Validation layer.
    val refundPolicy = RefundPolicy.DEFAULT
    val complaintService = ComplaintService(orderRepository, complaintRepository, refundRepository)
    val staffService = StaffService(complaintRepository, refundRepository)
    val menuService = MenuService(restaurantRepository)

    // Chatbot layer.
    val chatbotService = ChatbotService()

    /** A new gateway for one chat conversation. */
    fun newActionGateway(customerId: String, startOrderId: String?, transcript: () -> List<TranscriptLine>) =
        ActionGateway(customerId, orderRepository, complaintRepository, refundRepository, refundPolicy, transcript, startOrderId)
}
