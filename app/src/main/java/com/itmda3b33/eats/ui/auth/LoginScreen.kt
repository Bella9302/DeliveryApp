package com.itmda3b33.eats.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itmda3b33.eats.ui.common.EatsScaffold
import com.itmda3b33.eats.ui.common.EatsTextField
import com.itmda3b33.eats.ui.common.EatsWordmark
import com.itmda3b33.eats.ui.common.LinkButton
import com.itmda3b33.eats.ui.common.PasswordField
import com.itmda3b33.eats.ui.common.PrimaryButton
import com.itmda3b33.eats.ui.common.eatsViewModel

@Composable
fun LoginScreen(onSignUp: () -> Unit, onForgotPassword: () -> Unit) {
    val vm = eatsViewModel { LoginViewModel(it.authRepository) }
    val form by vm.form.collectAsStateWithLifecycle()

    EatsScaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            EatsWordmark()
            Spacer(Modifier.height(40.dp))
            AuthHeading("Welcome back", "Sign in to order from restaurants near you.")
            Spacer(Modifier.height(24.dp))

            EatsTextField(
                value = form.email,
                onValueChange = vm::onEmail,
                label = "Email",
                error = form.emailError,
                keyboardType = KeyboardType.Email,
            )
            Spacer(Modifier.height(8.dp))
            PasswordField(value = form.password, onValueChange = vm::onPassword, error = form.passwordError, imeAction = ImeAction.Done)
            LinkButton("Forgot password?", onClick = onForgotPassword, modifier = Modifier.align(Alignment.End))

            FormError(form.formError)
            PrimaryButton("Sign in", onClick = vm::signIn, loading = form.loading)

            Spacer(Modifier.height(24.dp))
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Text("New to eats?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinkButton("Create account", onClick = onSignUp)
            }
        }
    }
}

/** Large heading and subtitle at the top of each auth screen. Uses theme colours so it shows in dark mode. */
@Composable
internal fun AuthHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun FormError(message: String?) {
    if (message != null) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}
