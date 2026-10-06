package com.itmda3b33.deliveryapp.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Runs a save or delete in the background and reports the result as a short message for a
 * snackbar, so every action gives visible feedback.
 */
fun ViewModel.runAction(
    successMessage: String,
    onDone: (message: String) -> Unit,
    block: suspend () -> Unit,
) {
    viewModelScope.launch {
        val message = try {
            block()
            successMessage
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toUserMessage()
        }
        onDone(message)
    }
}
