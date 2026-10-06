package com.itmda3b33.deliveryapp.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itmda3b33.deliveryapp.data.OrderRepository
import com.itmda3b33.deliveryapp.data.model.Order
import com.itmda3b33.deliveryapp.ui.common.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OrdersState(
    val loading: Boolean = true,
    val orders: List<Order> = emptyList(),
    val selected: Order? = null,
    val error: String? = null,
)

/** Order tracking (read only) for the signed-in customer. */
class OrdersViewModel(private val customerId: String, private val orders: OrderRepository) : ViewModel() {

    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state.asStateFlow()

    fun loadOrders() = load { _state.value = OrdersState(loading = false, orders = orders.forCustomer(customerId)) }

    fun loadOrder(orderId: String) = load {
        val order = orders.get(orderId)?.takeIf { it.customerId == customerId }
        _state.value = if (order == null) {
            OrdersState(loading = false, error = "This order could not be found.")
        } else {
            OrdersState(loading = false, selected = order)
        }
    }

    private fun load(block: suspend () -> Unit) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = OrdersState(loading = false, error = e.toUserMessage())
            }
        }
    }
}
