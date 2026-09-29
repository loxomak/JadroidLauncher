package com.jadroid.launcher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jadroid.launcher.di.AppContainer

/** Resolves ViewModels from the [AppContainer] without a DI framework. */
class JadroidViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(AccountsViewModel::class.java) -> AccountsViewModel(container)
            modelClass.isAssignableFrom(InstancesViewModel::class.java) -> InstancesViewModel(container)
            modelClass.isAssignableFrom(VersionsViewModel::class.java) -> VersionsViewModel(container)
            modelClass.isAssignableFrom(ModSearchViewModel::class.java) -> ModSearchViewModel(container)
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
        return viewModel as T
    }
}
