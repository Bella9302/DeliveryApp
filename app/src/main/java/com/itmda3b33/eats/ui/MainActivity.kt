package com.itmda3b33.eats.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.itmda3b33.eats.ui.navigation.EatsApp
import com.itmda3b33.eats.ui.theme.EatsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EatsTheme {
                EatsApp()
            }
        }
    }
}
