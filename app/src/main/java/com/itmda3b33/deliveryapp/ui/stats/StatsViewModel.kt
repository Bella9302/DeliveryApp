package com.itmda3b33.deliveryapp.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itmda3b33.deliveryapp.data.ComplaintRepository
import com.itmda3b33.deliveryapp.data.RefundRepository
import com.itmda3b33.deliveryapp.data.model.Stats
import com.itmda3b33.deliveryapp.ui.common.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsState(val loading: Boolean = true, val stats: Stats? = null, val error: String? = null)

/** Loads all complaints and refunds and summarises them for the stats screen. */
class StatsViewModel(
    private val complaints: ComplaintRepository,
    private val refunds: RefundRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    fun load() {
        _state.value = StatsState(loading = true)
        viewModelScope.launch {
            _state.value = try {
                // coroutineScope keeps a failed query inside this try instead of crashing the app.
                coroutineScope {
                    val allComplaints = async { complaints.all() }
                    val allRefunds = async { refunds.all() }
                    StatsState(loading = false, stats = Stats.from(allComplaints.await(), allRefunds.await()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StatsState(loading = false, error = e.toUserMessage())
            }
        }
    }
}
