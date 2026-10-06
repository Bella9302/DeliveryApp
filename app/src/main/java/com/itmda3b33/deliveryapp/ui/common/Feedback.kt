package com.itmda3b33.deliveryapp.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException

/*
 * Shared pieces that give every screen visible feedback: loading, error, empty and success
 * (Systems Brief, section 9).
 */

/** Standard screen frame with a title, an optional back arrow and a snackbar for messages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
    actions: @Composable () -> Unit = {},
    floatingAction: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = { actions() },
            )
        },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        floatingActionButton = floatingAction,
        content = content,
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** A centred message, with an optional button such as "Try again". */
@Composable
fun MessageBox(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                message,
                textAlign = TextAlign.Center,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null) Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Turns an exception into a sentence the user can act on. */
fun Throwable.toUserMessage(): String = when (this) {
    is FirebaseNetworkException -> "No internet connection. Check your connection and try again."
    is FirebaseAuthWeakPasswordException -> "Choose a stronger password (at least 6 characters)."
    is FirebaseAuthUserCollisionException -> "An account with this email already exists."
    is FirebaseAuthInvalidUserException,
    is FirebaseAuthInvalidCredentialsException -> "The email or password is incorrect."
    is FirebaseAuthException -> "Sign-in failed. Please try again."
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "You don't have access to this."
        FirebaseFirestoreException.Code.UNAVAILABLE -> "The server can't be reached. Try again soon."
        else -> "Something went wrong saving or loading data. Please try again."
    }
    else -> "Something went wrong. Please try again."
}
