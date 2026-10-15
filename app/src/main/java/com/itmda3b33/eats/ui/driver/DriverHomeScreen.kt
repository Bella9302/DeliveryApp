package com.itmda3b33.eats.ui.driver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itmda3b33.eats.data.model.UserProfile
import com.itmda3b33.eats.ui.common.EatsPlateLogo
import com.itmda3b33.eats.ui.common.SecondaryButton

/** Courier screens are in the "build if time allows" scope. Drivers can sign in and see this until then. */
@Composable
fun DriverHomeScreen(profile: UserProfile, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EatsPlateLogo()
        Spacer(Modifier.height(24.dp))
        Text("Hi ${profile.firstName}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Courier screens are coming soon. Your deliveries will show here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
        SecondaryButton("Sign out", onClick = onSignOut)
    }
}
