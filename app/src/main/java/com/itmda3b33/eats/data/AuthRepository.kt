package com.itmda3b33.eats.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.FirebaseNetworkException
import com.itmda3b33.eats.data.model.Role
import com.itmda3b33.eats.data.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** Sign-up, login, logout and password reset with Firebase Authentication, plus the users collection. */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) {
    val currentUserId: String? get() = auth.currentUser?.uid

    /** Emits the signed-in user's ID, or null when signed out. */
    fun observeUserId(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    suspend fun signIn(email: String, password: String) {
        runAuth { auth.signInWithEmailAndPassword(email.trim(), password).await() }
    }

    /** Creates the account and its users document. New accounts are always customers. */
    suspend fun signUp(firstName: String, lastName: String, mobile: String, email: String, password: String) {
        runAuth {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val uid = result.user?.uid ?: error("Sign-up did not return a user")
            db.collection(Collections.USERS).document(uid).set(
                mapOf(
                    "firstName" to firstName.trim(),
                    "lastName" to lastName.trim(),
                    "email" to email.trim(),
                    "mobile" to mobile,
                    "role" to Role.CUSTOMER.value,
                    Fields.CREATED_AT to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
    }

    suspend fun sendPasswordReset(email: String) {
        runAuth { auth.sendPasswordResetEmail(email.trim()).await() }
    }

    fun signOut() = auth.signOut()

    suspend fun loadProfile(uid: String): UserProfile? =
        db.collection(Collections.USERS).document(uid).get().await().toUserProfile()

    /** Turns Firebase errors into sentences a user understands. */
    private suspend fun runAuth(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: FirebaseAuthUserCollisionException) {
            throw AuthError("An account with this email already exists.")
        } catch (e: FirebaseAuthInvalidUserException) {
            throw AuthError("No account matches these details.")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            throw AuthError("The email or password is incorrect.")
        } catch (e: FirebaseNetworkException) {
            throw AuthError("No internet connection. Check your connection and try again.")
        } catch (e: FirebaseAuthException) {
            throw AuthError(e.localizedMessage ?: "Something went wrong. Please try again.")
        }
    }
}

class AuthError(message: String) : Exception(message)
