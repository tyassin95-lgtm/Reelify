package app.reelify.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.reelify.AppContainer
import app.reelify.ReelifyApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as ReelifyApp).container

@Composable
inline fun <reified VM : ViewModel> rfViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
