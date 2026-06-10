package com.example.expensereader.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // ✅ current user
    fun currentUser() = auth.currentUser

    // ✅ is signed in (anonymous OR email user)
    fun isSignedIn(): Boolean = auth.currentUser != null

    // ✅ uid (null if not signed in)
    fun uidOrNull(): String? = auth.currentUser?.uid

    // ✅ email user?
    fun isEmailUser(): Boolean = auth.currentUser?.isAnonymous == false

    // ✅ anonymous user?
    fun isAnonymous(): Boolean = auth.currentUser?.isAnonymous == true

    /**
     * ✅ OPTIONAL: Keep if you still want "guest mode"
     * If already logged in (any type), returns uid.
     * Else signs in anonymously.
     */
    suspend fun ensureAnonymousLogin(): String {
        val current = auth.currentUser
        if (current != null) return current.uid

        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: throw IllegalStateException("Anonymous login failed")
    }

    /**
     * ✅ Register with email + password
     */
    suspend fun register(email: String, password: String): String {
        val res = auth.createUserWithEmailAndPassword(email, password).await()
        return res.user?.uid ?: throw IllegalStateException("Register failed")
    }

    /**
     * ✅ Login with email + password
     */
    suspend fun login(email: String, password: String): String {
        val res = auth.signInWithEmailAndPassword(email, password).await()
        return res.user?.uid ?: throw IllegalStateException("Login failed")
    }

    /**
     * ✅ If currently anonymous and you want to convert to real account
     * (keeps same UID if link succeeds)
     */
    suspend fun linkAnonymousToEmail(email: String, password: String): String {
        val user = auth.currentUser ?: throw IllegalStateException("No user session")
        if (!user.isAnonymous) return user.uid // already email user

        val cred = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
        val res = user.linkWithCredential(cred).await()
        return res.user?.uid ?: throw IllegalStateException("Link failed")
    }

    /**
     * ✅ Logout
     */
    fun logout() {
        auth.signOut()
    }
}
