package com.itmda3b33.eats.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.itmda3b33.eats.AppContainer
import com.itmda3b33.eats.EatsApplication

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as EatsApplication).container

/** Creates a view model with access to the app container. */
@Composable
inline fun <reified VM : ViewModel> eatsViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
