package com.itmda3b33.eats.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itmda3b33.eats.data.AuthRepository
import com.itmda3b33.eats.data.model.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val profile: UserProfile) : SessionState

    /** Signed in, but the profile could not be loaded. The user can retry or sign out. */
    data class ProfileError(val message: String) : SessionState
}

/** Who is signed in and with which role. Shared by the whole app; drives role-based navigation. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel(private val auth: AuthRepository) : ViewModel() {

    private val retry = MutableStateFlow(0)

    val state: StateFlow<SessionState> = combine(auth.observeUserId(), retry) { uid, _ -> uid }
        .flatMapLatest { uid -> if (uid == null) flowOf(SessionState.SignedOut) else loadProfile(uid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    private fun loadProfile(uid: String) = flow {
        emit(SessionState.Loading)
        // Right after sign-up the users document may not be written yet, so try a few times.
        repeat(PROFILE_ATTEMPTS) { attempt ->
            val profile = try {
                auth.loadProfile(uid)
            } catch (e: Exception) {
                if (attempt == PROFILE_ATTEMPTS - 1) {
                    emit(SessionState.ProfileError("Could not load your account. Check your connection."))
                    return@flow
                }
                null
            }
            if (profile != null) {
                emit(SessionState.SignedIn(profile))
                return@flow
            }
            delay(700)
        }
        emit(SessionState.ProfileError("Your account has no profile or role yet. Ask an admin to set it up."))
    }

    fun retry() {
        retry.value++
    }

    fun signOut() = auth.signOut()

    private companion object {
        const val PROFILE_ATTEMPTS = 5
    }
}
