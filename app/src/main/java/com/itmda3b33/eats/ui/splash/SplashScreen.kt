package com.itmda3b33.eats.ui.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itmda3b33.eats.ui.common.EatsPlateLogo
import com.itmda3b33.eats.ui.common.PrimaryButton
import com.itmda3b33.eats.ui.common.SecondaryButton
import com.itmda3b33.eats.ui.session.SessionState

/** Shown while the app checks who is signed in. */
@Composable
fun SplashScreen(state: SessionState, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EatsPlateLogo()
        Spacer(Modifier.height(32.dp))
        if (state is SessionState.ProfileError) {
            Text(state.message, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Try again", onClick = onRetry)
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Sign out", onClick = onSignOut)
        } else {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}
