package com.itmda3b33.eats.validation

/** The result of a checked action started from a screen. Screens turn each case into visible feedback. */
sealed interface ServiceResult<out T> {
    data class Success<T>(val value: T) : ServiceResult<T>

    /** A rule refused the action. Show [failure].message to the user. */
    data class Invalid(val failure: RuleFailure) : ServiceResult<Nothing>

    /** A technical error such as no network. */
    data class Error(val message: String) : ServiceResult<Nothing>
}

/** Runs [block] and turns unexpected exceptions into [ServiceResult.Error]. */
internal suspend fun <T> guarded(block: suspend () -> ServiceResult<T>): ServiceResult<T> =
    try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        ServiceResult.Error(friendlyError(e))
    }

internal fun friendlyError(e: Exception): String = when (e) {
    is com.google.firebase.firestore.FirebaseFirestoreException -> when (e.code) {
        com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You don't have permission to do that."
        com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Can't reach the server. Check your connection and try again."
        else -> "Something went wrong saving that. Please try again."
    }
    else -> e.localizedMessage ?: "Something went wrong. Please try again."
}
