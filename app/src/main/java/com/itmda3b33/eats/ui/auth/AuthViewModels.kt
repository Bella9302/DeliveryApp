package com.itmda3b33.eats.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itmda3b33.eats.data.AuthRepository
import com.itmda3b33.eats.validation.AuthRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * Form state for the sign-in, sign-up and password reset screens.
 * After a successful sign-in or sign-up, the session flow moves the user to their role's home screen.
 */

data class LoginForm(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val formError: String? = null,
    val loading: Boolean = false,
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _form = MutableStateFlow(LoginForm())
    val form: StateFlow<LoginForm> = _form.asStateFlow()

    fun onEmail(value: String) = _form.update { it.copy(email = value, emailError = null, formError = null) }
    fun onPassword(value: String) = _form.update { it.copy(password = value, passwordError = null, formError = null) }

    fun signIn() {
        val current = _form.value
        val emailError = AuthRules.checkEmail(current.email)
        val passwordError = AuthRules.checkLoginPassword(current.password)
        if (emailError != null || passwordError != null) {
            _form.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        _form.update { it.copy(loading = true, formError = null) }
        viewModelScope.launch {
            try {
                auth.signIn(current.email, current.password)
                // Keep the spinner: the app navigates away when the profile loads.
            } catch (e: Exception) {
                _form.update { it.copy(loading = false, formError = e.message) }
            }
        }
    }
}

data class SignUpForm(
    val firstName: String = "",
    val lastName: String = "",
    val mobile: String = "",
    val email: String = "",
    val password: String = "",
    val acceptedTerms: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val formError: String? = null,
    val loading: Boolean = false,
) {
    companion object {
        const val FIRST = "first"
        const val LAST = "last"
        const val MOBILE = "mobile"
        const val EMAIL = "email"
        const val PASSWORD = "password"
        const val TERMS = "terms"
    }
}

class SignUpViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _form = MutableStateFlow(SignUpForm())
    val form: StateFlow<SignUpForm> = _form.asStateFlow()

    private fun edit(field: String, change: (SignUpForm) -> SignUpForm) =
        _form.update { change(it).copy(errors = it.errors - field, formError = null) }

    fun onFirstName(v: String) = edit(SignUpForm.FIRST) { it.copy(firstName = v) }
    fun onLastName(v: String) = edit(SignUpForm.LAST) { it.copy(lastName = v) }
    fun onMobile(v: String) = edit(SignUpForm.MOBILE) { it.copy(mobile = v) }
    fun onEmail(v: String) = edit(SignUpForm.EMAIL) { it.copy(email = v) }
    fun onPassword(v: String) = edit(SignUpForm.PASSWORD) { it.copy(password = v) }
    fun onTerms(v: Boolean) = edit(SignUpForm.TERMS) { it.copy(acceptedTerms = v) }

    fun signUp() {
        val f = _form.value
        val errors = buildMap {
            AuthRules.checkName(f.firstName, "first name")?.let { put(SignUpForm.FIRST, it) }
            AuthRules.checkName(f.lastName, "last name")?.let { put(SignUpForm.LAST, it) }
            AuthRules.checkMobile(f.mobile)?.let { put(SignUpForm.MOBILE, it) }
            AuthRules.checkEmail(f.email)?.let { put(SignUpForm.EMAIL, it) }
            AuthRules.checkPassword(f.password)?.let { put(SignUpForm.PASSWORD, it) }
            if (!f.acceptedTerms) put(SignUpForm.TERMS, "Please accept the Terms of Use and Privacy Policy.")
        }
        if (errors.isNotEmpty()) {
            _form.update { it.copy(errors = errors) }
            return
        }
        _form.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                auth.signUp(f.firstName, f.lastName, AuthRules.normaliseMobile(f.mobile), f.email, f.password)
            } catch (e: Exception) {
                _form.update { it.copy(loading = false, formError = e.message) }
            }
        }
    }
}

data class ResetForm(
    val email: String = "",
    val emailError: String? = null,
    val formError: String? = null,
    val sent: Boolean = false,
    val loading: Boolean = false,
)

class ForgotPasswordViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _form = MutableStateFlow(ResetForm())
    val form: StateFlow<ResetForm> = _form.asStateFlow()

    fun onEmail(value: String) = _form.update { it.copy(email = value, emailError = null, formError = null, sent = false) }

    fun send() {
        val error = AuthRules.checkEmail(_form.value.email)
        if (error != null) {
            _form.update { it.copy(emailError = error) }
            return
        }
        _form.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                auth.sendPasswordReset(_form.value.email)
                _form.update { it.copy(loading = false, sent = true) }
            } catch (e: Exception) {
                _form.update { it.copy(loading = false, formError = e.message) }
            }
        }
    }
}
