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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itmda3b33.eats.ui.common.EatsScaffold
import com.itmda3b33.eats.ui.common.EatsTextField
import com.itmda3b33.eats.ui.common.LinkButton
import com.itmda3b33.eats.ui.common.PasswordField
import com.itmda3b33.eats.ui.common.PrimaryButton
import com.itmda3b33.eats.ui.common.eatsViewModel
import com.itmda3b33.eats.validation.AuthRules

@Composable
fun SignUpScreen(onBack: () -> Unit) {
    val vm = eatsViewModel { SignUpViewModel(it.authRepository) }
    val form by vm.form.collectAsStateWithLifecycle()

    EatsScaffold(title = "", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            AuthHeading("Create your account", "Tell us a little about you to get started.")
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EatsTextField(form.firstName, vm::onFirstName, "First name", Modifier.weight(1f), error = form.errors[SignUpForm.FIRST])
                EatsTextField(form.lastName, vm::onLastName, "Last name", Modifier.weight(1f), error = form.errors[SignUpForm.LAST])
            }
            EatsTextField(form.mobile, vm::onMobile, "Mobile number", error = form.errors[SignUpForm.MOBILE], keyboardType = KeyboardType.Phone)
            EatsTextField(form.email, vm::onEmail, "Email", error = form.errors[SignUpForm.EMAIL], keyboardType = KeyboardType.Email)
            PasswordField(
                value = form.password,
                onValueChange = vm::onPassword,
                error = form.errors[SignUpForm.PASSWORD],
                supportingText = "At least ${AuthRules.MIN_PASSWORD} characters",
            )

            // The label takes the remaining width so it wraps neatly next to the checkbox.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = form.acceptedTerms, onCheckedChange = vm::onTerms)
                Text(
                    buildAnnotatedString {
                        append("I agree to the ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)) { append("Terms of Use") }
                        append(" and ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)) { append("Privacy Policy") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }
            form.errors[SignUpForm.TERMS]?.let { FormError(it) }

            FormError(form.formError)
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Create account", onClick = vm::signUp, loading = form.loading)

            Spacer(Modifier.height(16.dp))
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinkButton("Sign in", onClick = onBack)
            }
        }
    }
}
