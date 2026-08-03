package com.example.expensereader.data

import com.example.expensereader.model.FbScheme
import com.example.expensereader.model.FbSchemeUi
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirestoreSchemesRepository {

    private val db = FirebaseRefs.db

    suspend fun getUserStateOrNull(): String? {
        val uid = FirebaseRefs.uidOrThrow()
        val snap = db.collection("users").document(uid)
            .collection("profile").document("main")
            .get().await()
        return snap.getString("state")?.takeIf { it.isNotBlank() }
    }

    suspend fun fetchSchemesForUser(): List<FbSchemeUi> {
        val userState = getUserStateOrNull()
        val states = if (userState.isNullOrBlank()) listOf("ALL") else listOf("ALL", userState)

        var q: Query = db.collection("schemes")
            .whereEqualTo("status", "active")
            .whereEqualTo("studentOnly", true)
            .whereEqualTo("nonCaste", true)
            .whereArrayContainsAny("state", states)

        val snap = q.get().await()

        return snap.documents.mapNotNull { d ->
            val scheme = d.toObject(FbScheme::class.java) ?: return@mapNotNull null
            FbSchemeUi(id = d.id, data = scheme)
        }
    }
}
