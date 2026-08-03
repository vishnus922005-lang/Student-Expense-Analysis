package com.example.expensereader.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun currentUser() = auth.currentUser

    fun isSignedIn(): Boolean = auth.currentUser != null

    fun uidOrNull(): String? = auth.currentUser?.uid

    fun isEmailUser(): Boolean = auth.currentUser?.isAnonymous == false

    fun isAnonymous(): Boolean = auth.currentUser?.isAnonymous == true

    suspend fun ensureAnonymousLogin(): String {
        val current = auth.currentUser
        if (current != null) return current.uid

        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: throw IllegalStateException("Anonymous login failed")
    }

    suspend fun register(email: String, password: String): String {
        val res = auth.createUserWithEmailAndPassword(email, password).await()
        return res.user?.uid ?: throw IllegalStateException("Register failed")
    }

    suspend fun login(email: String, password: String): String {
        val res = auth.signInWithEmailAndPassword(email, password).await()
        return res.user?.uid ?: throw IllegalStateException("Login failed")
    }

    suspend fun linkAnonymousToEmail(email: String, password: String): String {
        val user = auth.currentUser ?: throw IllegalStateException("No user session")
        if (!user.isAnonymous) return user.uid // already email user

        val cred = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
        val res = user.linkWithCredential(cred).await()
        return res.user?.uid ?: throw IllegalStateException("Link failed")
    }
    
    fun logout() {
        auth.signOut()
    }
}
