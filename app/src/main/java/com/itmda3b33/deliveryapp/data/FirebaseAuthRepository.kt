package com.itmda3b33.deliveryapp.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/** Sign-up, login, logout and password reset through Firebase Authentication. */
class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {

    override val currentUserId: String? get() = auth.currentUser?.uid

    override suspend fun signIn(email: String, password: String): String {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        return result.user?.uid ?: error("Sign-in returned no user")
    }

    override suspend fun signUp(email: String, password: String): String {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        return result.user?.uid ?: error("Sign-up returned no user")
    }

    override suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    override fun signOut() = auth.signOut()
}
