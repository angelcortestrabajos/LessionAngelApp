package com.example.lessionangel

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

private const val TRAINING_SESSIONS_COLLECTION = "training_sessions"

data class SessionReading(
    val heartRate: Int = 0,
    val effort: Int = 0,
    val fatigue: Int = 0,
    val steps: Int = 0
)

data class TrainingSessionCloud(
    val id: String = "",
    val userId: String = "",
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val readings: List<SessionReading> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class LocalTrainingSession(
    val id: String = "",
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val readings: List<SessionReading> = emptyList()
)

class SessionRepository(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val userIdProvider: () -> String? = { Firebase.auth.currentUser?.uid }
) {
    suspend fun ensureSignedIn(): String {
        val currentUid = userIdProvider()
        if (currentUid != null) return currentUid

        Firebase.auth.signInAnonymously().await()
        return Firebase.auth.currentUser?.uid ?: throw IllegalStateException("No se pudo autenticar en Firebase")
    }

    private fun currentUserIdOrNull(): String? = userIdProvider()

    suspend fun saveSession(session: TrainingSessionCloud) {
        val uid = ensureSignedIn()
        val sessionWithUser = session.copy(userId = uid)
        db.collection(TRAINING_SESSIONS_COLLECTION)
            .document(sessionWithUser.id.ifBlank { System.currentTimeMillis().toString() })
            .set(sessionWithUser)
            .await()
    }

    suspend fun getSessions(): List<TrainingSessionCloud> {
        val uid = currentUserIdOrNull() ?: return emptyList()
        val result = db.collection(TRAINING_SESSIONS_COLLECTION)
            .whereEqualTo("userId", uid)
            .orderBy("startedAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return result.documents.mapNotNull { doc ->
            doc.toObject(TrainingSessionCloud::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun deleteSession(sessionId: String) {
        val uid = currentUserIdOrNull() ?: return
        val document = db.collection(TRAINING_SESSIONS_COLLECTION).document(sessionId)
        val snapshot = document.get().await()
        if (snapshot.exists() && snapshot.getString("userId") == uid) {
            document.delete().await()
        }
    }

    suspend fun syncLocalSessions(localSessions: List<LocalTrainingSession>) {
        val uid = ensureSignedIn()
        val batch = db.batch()

        localSessions.forEach { local ->
            val docId = local.id.ifBlank { System.currentTimeMillis().toString() }
            val data = TrainingSessionCloud(
                id = docId,
                userId = uid,
                startedAt = local.startedAt,
                finishedAt = local.finishedAt,
                readings = local.readings,
                createdAt = System.currentTimeMillis()
            )
            batch.set(db.collection(TRAINING_SESSIONS_COLLECTION).document(docId), data)
        }

        batch.commit().await()
    }
}
