package com.itmda3b33.deliveryapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.itmda3b33.deliveryapp.ui.AppNavHost
import com.itmda3b33.deliveryapp.ui.theme.DeliveryTheme

/** The only activity. Compose navigation handles every screen. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DeliveryApp).container
        setContent {
            DeliveryTheme {
                AppNavHost(container)
            }
        }
    }
}
