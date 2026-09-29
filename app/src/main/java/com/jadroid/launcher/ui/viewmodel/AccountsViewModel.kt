package com.jadroid.launcher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jadroid.launcher.data.auth.Account
import com.jadroid.launcher.data.auth.DeviceCodeResponse
import com.jadroid.launcher.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface SignInState {
    data object Idle : SignInState
    data object Starting : SignInState
    data class Waiting(
        val device: DeviceCodeResponse,
        val status: String = "Waiting for your approval ..."
    ) : SignInState
    data class Success(val username: String) : SignInState
    data class Failed(val message: String) : SignInState
}

class AccountsViewModel(private val container: AppContainer) : ViewModel() {

    private val auth = container.auth

    val accounts: StateFlow<List<Account>> = auth.accounts
    val activeAccount: StateFlow<Account?> = auth.activeAccount

    private val _signInState = MutableStateFlow<SignInState>(SignInState.Idle)
    val signInState: StateFlow<SignInState> = _signInState

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private var signInJob: Job? = null

    val canCreateLocalAccounts: Boolean get() = auth.hasConnectedMinecraftAccount()

    fun signInWithMicrosoft() {
        if (signInJob?.isActive == true) return
        signInJob = viewModelScope.launch {
            try {
                _signInState.value = SignInState.Starting
                val device = auth.beginMicrosoftLogin()
                _signInState.value = SignInState.Waiting(device)
                val account = auth.completeMicrosoftLogin(device) { status ->
                    _signInState.value = SignInState.Waiting(device, status)
                }
                _signInState.value = SignInState.Success(account.username)
                _message.value = "Connected ${account.username}"
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _signInState.value = SignInState.Failed(t.message ?: "Microsoft sign-in failed")
            }
        }
    }

    fun cancelSignIn() {
        signInJob?.cancel()
        signInJob = null
        _signInState.value = SignInState.Idle
    }

    fun dismissSignIn() {
        if (_signInState.value is SignInState.Waiting || _signInState.value is SignInState.Starting) {
            signInJob?.cancel()
        }
        _signInState.value = SignInState.Idle
    }

    fun createLocalAccount(username: String) {
        viewModelScope.launch {
            _message.value = try {
                val account = auth.createLocalAccount(username)
                "Local account \"${account.username}\" created"
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                t.message ?: "Could not create the local account"
            }
        }
    }

    fun removeAccount(accountId: String) {
        auth.removeAccount(accountId)
        _message.value = "Account removed"
    }

    fun setActiveAccount(accountId: String) = auth.setActiveAccount(accountId)

    fun clearMessage() {
        _message.value = null
    }
}
