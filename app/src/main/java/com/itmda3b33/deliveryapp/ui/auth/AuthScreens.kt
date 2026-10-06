package com.itmda3b33.deliveryapp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.itmda3b33.deliveryapp.ui.common.ScreenScaffold

@Composable
fun LoginScreen(
    form: AuthFormState,
    onSignIn: (email: String, password: String) -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AuthForm(title = "Sign in", form = form) {
        EmailField(email) { email = it }
        PasswordField("Password", password) { password = it }
        SubmitButton("Sign in", form.busy) { onSignIn(email, password) }
        TextButton(onClick = onForgotPassword) { Text("Forgot password?") }
        TextButton(onClick = onCreateAccount) { Text("New here? Create an account") }
    }
}

@Composable
fun SignUpScreen(
    form: AuthFormState,
    onSignUp: (name: String, email: String, password: String, confirm: String) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    AuthForm(title = "Create account", form = form, onBack = onBack) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        EmailField(email) { email = it }
        PasswordField("Password", password) { password = it }
        PasswordField("Confirm password", confirm) { confirm = it }
        SubmitButton("Create account", form.busy) { onSignUp(name, email, password, confirm) }
    }
}

@Composable
fun ResetPasswordScreen(form: AuthFormState, onSend: (email: String) -> Unit, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }

    AuthForm(title = "Reset password", form = form, onBack = onBack) {
        Text("We'll email you a link to choose a new password.")
        EmailField(email) { email = it }
        SubmitButton("Send reset link", form.busy) { onSend(email) }
    }
}

@Composable
private fun AuthForm(
    title: String,
    form: AuthFormState,
    onBack: (() -> Unit)? = null,
    fields: @Composable () -> Unit,
) {
    ScreenScaffold(title = title, onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            fields()
            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            form.info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text("Email") },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    modifier = Modifier.fillMaxWidth(),
)

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    modifier = Modifier.fillMaxWidth(),
)

@Composable
private fun SubmitButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        if (busy) CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp) else Text(label)
    }
}
