package com.itmda3b33.deliveryapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itmda3b33.deliveryapp.data.AuthRepository
import com.itmda3b33.deliveryapp.data.UserRepository
import com.itmda3b33.deliveryapp.data.model.Role
import com.itmda3b33.deliveryapp.data.model.User
import com.itmda3b33.deliveryapp.ui.common.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Who is signed in. Navigation shows screens based on [SessionState.SignedIn.user]'s role. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState {
        val role: Role? get() = Role.from(user.role)
    }
}

/** State of the login, sign-up and reset forms. */
data class AuthFormState(
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

/** Handles sign-up, login, logout and password reset, and keeps track of the signed-in user. */
class SessionViewModel(
    private val auth: AuthRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val _session = MutableStateFlow<SessionState>(SessionState.Loading)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    init {
        val uid = auth.currentUserId
        if (uid == null) _session.value = SessionState.SignedOut else runForm { loadProfile(uid) }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) return showError("Enter your email and password.")
        runForm { loadProfile(auth.signIn(email, password)) }
    }

    fun signUp(name: String, email: String, password: String, confirm: String) {
        when {
            name.isBlank() -> return showError("Enter your name.")
            !email.contains("@") -> return showError("Enter a valid email address.")
            password.length < 6 -> return showError("Use at least 6 characters for your password.")
            password != confirm -> return showError("The passwords do not match.")
        }
        runForm {
            val uid = auth.signUp(email, password)
            // New accounts are always customers. Other roles are set by an admin or the seed script.
            users.create(User(id = uid, name = name.trim(), email = email.trim(), role = Role.CUSTOMER.value))
            loadProfile(uid)
        }
    }

    fun sendPasswordReset(email: String) {
        if (!email.contains("@")) return showError("Enter the email address you signed up with.")
        runForm {
            auth.sendPasswordReset(email)
            _form.update { it.copy(info = "Check your email for a link to reset your password.") }
        }
    }

    fun signOut() {
        auth.signOut()
        _session.value = SessionState.SignedOut
        _form.value = AuthFormState()
    }

    fun clearMessages() = _form.update { it.copy(error = null, info = null) }

    private suspend fun loadProfile(uid: String) {
        val user = users.get(uid)
        if (user == null) {
            auth.signOut()
            _session.value = SessionState.SignedOut
            showError("Your profile could not be found. Contact support.")
        } else {
            _session.value = SessionState.SignedIn(user)
        }
    }

    private fun showError(message: String) = _form.update { it.copy(error = message, info = null) }

    private fun runForm(block: suspend () -> Unit) {
        _form.value = AuthFormState(busy = true)
        viewModelScope.launch {
            try {
                block()
                _form.update { it.copy(busy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _form.value = AuthFormState(error = e.toUserMessage())
                if (_session.value == SessionState.Loading) _session.value = SessionState.SignedOut
            }
        }
    }
}
