package com.itmda3b33.eats.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope

/** Loading, ready or error: every list and detail screen shows one of these. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Ready<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

/** Turns a live data flow into screen state. Errors (for example a permission error) become [UiState.Error]. */
fun <T> Flow<T>.asUiState(scope: CoroutineScope): StateFlow<UiState<T>> =
    map<T, UiState<T>> { UiState.Ready(it) }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Could not load this. Check your connection.")) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

/** Shows [UiState] with the standard loading and error views, and [content] when ready. */
@Composable
fun <T> StateContent(state: UiState<T>, onRetry: (() -> Unit)? = null, content: @Composable (T) -> Unit) {
    when (state) {
        UiState.Loading -> LoadingView()
        is UiState.Error -> ErrorView(state.message, onRetry)
        is UiState.Ready -> content(state.data)
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorView(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
fun EmptyView(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Shows each message from a view model as a snackbar: success, error or validation feedback. */
@Composable
fun MessageEffect(messages: Flow<String>, snackbar: SnackbarHostState) {
    LaunchedEffect(messages) {
        messages.collect { snackbar.showSnackbar(it) }
    }
}
