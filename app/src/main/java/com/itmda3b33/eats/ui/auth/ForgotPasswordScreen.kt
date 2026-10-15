package com.itmda3b33.eats.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itmda3b33.eats.ui.common.EatsScaffold
import com.itmda3b33.eats.ui.common.EatsTextField
import com.itmda3b33.eats.ui.common.PrimaryButton
import com.itmda3b33.eats.ui.common.eatsViewModel

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    val vm = eatsViewModel { ForgotPasswordViewModel(it.authRepository) }
    val form by vm.form.collectAsStateWithLifecycle()

    EatsScaffold(title = "", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 8.dp)) {
            AuthHeading("Reset your password", "Enter your email and we'll send you a link to choose a new password.")
            Spacer(Modifier.height(24.dp))
            EatsTextField(
                value = form.email,
                onValueChange = vm::onEmail,
                label = "Email",
                error = form.emailError,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            )
            FormError(form.formError)
            if (form.sent) {
                Text(
                    "Check your inbox. If an account uses this email, a reset link is on its way.",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Send reset link", onClick = vm::send, loading = form.loading)
        }
    }
}
