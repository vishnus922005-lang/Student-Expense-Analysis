package com.example.expensereader.data

import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

class FirestoreSavingsRepository {

    private val db = FirebaseRefs.db

    suspend fun addSavingsEvent(schemeId: String, amount: Double, note: String?) {
        val uid = FirebaseRefs.uidOrThrow()
        val doc = hashMapOf(
            "schemeId" to schemeId,
            "amount" to amount,
            "note" to note,
            "dateMillis" to System.currentTimeMillis(),
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("users").document(uid)
            .collection("savings_events")
            .add(doc).await()
    }
}
