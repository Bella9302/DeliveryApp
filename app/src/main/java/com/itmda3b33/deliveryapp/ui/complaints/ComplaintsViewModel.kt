package com.itmda3b33.deliveryapp.ui.complaints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itmda3b33.deliveryapp.data.ComplaintRepository
import com.itmda3b33.deliveryapp.data.model.Complaint
import com.itmda3b33.deliveryapp.ui.common.runAction
import com.itmda3b33.deliveryapp.ui.common.toUserMessage
import com.itmda3b33.deliveryapp.validation.EditRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComplaintsState(
    val loading: Boolean = true,
    val complaints: List<Complaint> = emptyList(),
    val error: String? = null,
    /** A one-off message for the snackbar, such as "Complaint updated". */
    val notice: String? = null,
)

/**
 * Module A: the customer's complaints. Complaints are created through the chat; here the customer
 * reads them, edits the description, or cancels (deletes) them while they are still "logged".
 */
class ComplaintsViewModel(
    private val customerId: String,
    private val complaints: ComplaintRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ComplaintsState())
    val state: StateFlow<ComplaintsState> = _state.asStateFlow()

    fun canEdit(complaint: Complaint) = EditRules.customerCanEdit(complaint)

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val list = complaints.forCustomer(customerId)
                _state.update { it.copy(loading = false, complaints = list) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toUserMessage()) }
            }
        }
    }

    /** Returns an error message to show in the edit dialog, or null once the save has started. */
    fun updateDescription(complaint: Complaint, description: String): String? {
        EditRules.checkDescription(description)?.let { return it }
        if (!canEdit(complaint)) return "This complaint is already being handled and can't be changed."
        runAction("Complaint updated.", ::finish) { complaints.updateDescription(complaint.id, description.trim()) }
        return null
    }

    fun delete(complaint: Complaint) {
        if (!canEdit(complaint)) {
            _state.update { it.copy(notice = "This complaint is already being handled and can't be cancelled.") }
            return
        }
        runAction("Complaint cancelled.", ::finish) { complaints.delete(complaint.id) }
    }

    fun noticeShown() = _state.update { it.copy(notice = null) }

    private fun finish(message: String) {
        _state.update { it.copy(notice = message) }
        load()
    }
}
