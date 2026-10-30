package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.GuestCatchEntity
import com.example.data.local.GuestFishLogDao
import com.example.data.local.GuestProfileEntity
import com.example.data.local.GuestSessionEntity
import com.example.data.local.GuestSpotEntity
import com.example.data.model.CatchRecord
import com.example.data.model.FishingSession
import com.example.data.model.FishingSpot
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

interface FishLogDataSource {
    val isGuestDataSource: Boolean
    fun observeUserProfile(userId: String): Flow<UserProfile?>
    suspend fun ensureOrSaveUserProfile(profile: UserProfile): Result<Unit>

    fun observeCatches(userId: String): Flow<List<CatchRecord>>
    suspend fun createCatch(record: CatchRecord): Result<String>
    suspend fun updateCatch(record: CatchRecord, previousSessionId: String? = null): Result<Unit>
    suspend fun deleteCatch(catchId: String, sessionId: String? = null): Result<Unit>

    fun observeSessions(userId: String): Flow<List<FishingSession>>
    suspend fun createSession(session: FishingSession): Result<String>
    suspend fun updateSession(session: FishingSession): Result<Unit>
    suspend fun deleteSession(sessionId: String): Result<Unit>

    fun observeAllVisibleSpots(userId: String): Flow<List<FishingSpot>>
    suspend fun createSpot(spot: FishingSpot): Result<String>
    suspend fun updateSpot(spot: FishingSpot): Result<Unit>
    suspend fun deleteSpot(spotId: String): Result<Unit>
}

class FishLogRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : FishLogDataSource {

    override val isGuestDataSource: Boolean = false

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing cloud records.")
    }

    // --- User Profile ---

    override fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    override suspend fun ensureOrSaveUserProfile(profile: UserProfile): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            val validProfile = profile.copy(userId = uid)
            if (!snap.exists()) {
                docRef.set(validProfile.toCreateMap()).await()
            } else {
                docRef.update(validProfile.toUpdateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Ensures a cloud profile exists on initial login WITHOUT overwriting an existing cloud profile.
     */
    suspend fun initializeProfileIfAbsent(defaultProfile: UserProfile): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(defaultProfile.copy(userId = uid).toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Catches ---

    override fun observeCatches(userId: String): Flow<List<CatchRecord>> {
        val path = "users/$userId/catches"
        return db.collection("users").document(userId).collection("catches")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(CatchRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.caughtAtEpochMillis }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun getCatchById(ownerUserId: String, catchId: String): Result<CatchRecord?> {
        val docRef = db.collection("users").document(ownerUserId).collection("catches").document(catchId)
        return try {
            val snap = docRef.get().await()
            val item = snap.toObject(CatchRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            Result.success(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    override suspend fun createCatch(record: CatchRecord): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("catches")
        return try {
            val payload = record.copy(userId = uid).toCreateMap()
            val docRef = colRef.add(payload).await()
            val sessionId = record.sessionId?.takeIf { it.isNotBlank() }
            if (sessionId != null) {
                recalculateSessionCatchCount(uid, sessionId)
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    override suspend fun updateCatch(record: CatchRecord, previousSessionId: String?): Result<Unit> {
        val uid = requireUserId()
        require(record.id.isNotBlank()) { "Catch ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("catches").document(record.id)
        return try {
            docRef.update(record.copy(userId = uid).toUpdateMap()).await()
            val oldSid = previousSessionId?.takeIf { it.isNotBlank() }
            val newSid = record.sessionId?.takeIf { it.isNotBlank() }
            if (oldSid != null && oldSid != newSid) {
                recalculateSessionCatchCount(uid, oldSid)
            }
            if (newSid != null) {
                recalculateSessionCatchCount(uid, newSid)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    override suspend fun deleteCatch(catchId: String, sessionId: String?): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("catches").document(catchId)
        return try {
            docRef.delete().await()
            val sid = sessionId?.takeIf { it.isNotBlank() }
            if (sid != null) {
                recalculateSessionCatchCount(uid, sid)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    private suspend fun recalculateSessionCatchCount(uid: String, sessionId: String) {
        try {
            val sessionRef = db.collection("users").document(uid).collection("sessions").document(sessionId)
            val sessionSnap = sessionRef.get().await()
            if (sessionSnap.exists()) {
                val catchesSnap = db.collection("users").document(uid).collection("catches")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("sessionId", sessionId)
                    .get()
                    .await()
                sessionRef.update(
                    mapOf(
                        "catchCount" to catchesSnap.size().toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/sessions/$sessionId")
        }
    }

    // --- Fishing Journal Sessions ---

    override fun observeSessions(userId: String): Flow<List<FishingSession>> {
        val path = "users/$userId/sessions"
        return db.collection("users").document(userId).collection("sessions")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FishingSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.startedAtEpochMillis }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    override suspend fun createSession(session: FishingSession): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("sessions")
        return try {
            val payload = session.copy(userId = uid).toCreateMap()
            val docRef = colRef.add(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    override suspend fun updateSession(session: FishingSession): Result<Unit> {
        val uid = requireUserId()
        require(session.id.isNotBlank()) { "Session ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("sessions").document(session.id)
        return try {
            docRef.update(session.copy(userId = uid).toUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("sessions").document(sessionId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Fishing Map Spots ---

    fun observeMySpots(userId: String): Flow<List<FishingSpot>> {
        val path = "spots"
        return db.collection("spots")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FishingSpot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeCommunitySharedSpots(): Flow<List<FishingSpot>> {
        val path = "spots"
        return db.collection("spots")
            .whereEqualTo("isShared", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FishingSpot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    override fun observeAllVisibleSpots(userId: String): Flow<List<FishingSpot>> {
        return combine(
            observeMySpots(userId),
            observeCommunitySharedSpots()
        ) { mySpots, sharedSpots ->
            (mySpots + sharedSpots)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
    }

    suspend fun getSpotById(spotId: String): Result<FishingSpot?> {
        val docRef = db.collection("spots").document(spotId)
        return try {
            val snap = docRef.get().await()
            Result.success(snap.toObject(FishingSpot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    override suspend fun createSpot(spot: FishingSpot): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("spots")
        return try {
            val payload = spot.copy(userId = uid).toCreateMap()
            val docRef = colRef.add(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    override suspend fun updateSpot(spot: FishingSpot): Result<Unit> {
        val uid = requireUserId()
        require(spot.id.isNotBlank()) { "Spot ID must not be blank for update" }
        val docRef = db.collection("spots").document(spot.id)
        return try {
            docRef.update(spot.copy(userId = uid).toUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    override suspend fun deleteSpot(spotId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("spots").document(spotId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Safe, Idempotent Guest-to-Account Cloud Migration ---

    data class MigrationSummary(
        val spotsTransferred: Int,
        val sessionsTransferred: Int,
        val catchesTransferred: Int
    )

    /**
     * Safely transfers unmigrated guest records from Room to the authenticated user's cloud account.
     * - Deterministic document IDs prevent duplicate records on retry.
     * - Checks docRef.get().exists() before writing so existing cloud records are NEVER overwritten.
     * - Preserves relational spotId and sessionId links and catch photos.
     * - Marks each guest record as migrated ONLY after its cloud write succeeds.
     */
    suspend fun migrateGuestDataToAccount(guestDao: GuestFishLogDao): Result<MigrationSummary> {
        val uid = requireUserId()
        return try {
            val unmigratedSpots = guestDao.getUnmigratedSpots()
            val unmigratedSessions = guestDao.getUnmigratedSessions()
            val unmigratedCatches = guestDao.getUnmigratedCatches()

            if (unmigratedSpots.isEmpty() && unmigratedSessions.isEmpty() && unmigratedCatches.isEmpty()) {
                return Result.success(MigrationSummary(0, 0, 0))
            }

            // Migrate guest profile ONLY if cloud profile does not already exist
            val guestProfile = guestDao.getProfileOnce()
            if (guestProfile != null) {
                val userDoc = db.collection("users").document(uid)
                val existingCloudProfile = userDoc.get().await()
                if (!existingCloudProfile.exists()) {
                    val domainProfile = guestProfile.toDomain(uid).copy(
                        displayName = guestProfile.displayName.takeIf {
                            it.isNotBlank() && it != "Guest Angler"
                        } ?: (auth.currentUser?.displayName ?: "Angler")
                    )
                    userDoc.set(domainProfile.toCreateMap()).await()
                }
            }

            // Map old local IDs -> deterministic cloud IDs to preserve relational links
            val spotIdMap = mutableMapOf<String, String>()
            var spotsMigrated = 0
            for (spotEntity in unmigratedSpots) {
                val cloudSpotId = deterministicMigrationId("spot", uid, spotEntity.id)
                spotIdMap[spotEntity.id] = cloudSpotId
                val spotRef = db.collection("spots").document(cloudSpotId)
                val existingSnap = spotRef.get().await()
                if (!existingSnap.exists()) {
                    val domainSpot = spotEntity.toDomain(uid).copy(isShared = false)
                    spotRef.set(domainSpot.toCreateMap()).await()
                }
                // Mark migrated ONLY after confirmed cloud existence/write
                guestDao.markSpotMigrated(spotEntity.id, uid)
                spotsMigrated++
            }

            val sessionIdMap = mutableMapOf<String, String>()
            var sessionsMigrated = 0
            for (sessionEntity in unmigratedSessions) {
                val cloudSessionId = deterministicMigrationId("session", uid, sessionEntity.id)
                sessionIdMap[sessionEntity.id] = cloudSessionId
                val remappedSpotId = sessionEntity.spotId?.let { spotIdMap[it] ?: deterministicMigrationId("spot", uid, it) }
                val sessionRef = db.collection("users").document(uid).collection("sessions").document(cloudSessionId)
                val existingSnap = sessionRef.get().await()
                if (!existingSnap.exists()) {
                    val domainSession = sessionEntity.toDomain(uid).copy(spotId = remappedSpotId)
                    sessionRef.set(domainSession.toCreateMap()).await()
                }
                guestDao.markSessionMigrated(sessionEntity.id, uid)
                sessionsMigrated++
            }

            var catchesMigrated = 0
            val affectedCloudSessionIds = mutableSetOf<String>()
            for (catchEntity in unmigratedCatches) {
                val cloudCatchId = deterministicMigrationId("catch", uid, catchEntity.id)
                val remappedSpotId = catchEntity.spotId?.let { spotIdMap[it] ?: deterministicMigrationId("spot", uid, it) }
                val remappedSessionId = catchEntity.sessionId?.let { sessionIdMap[it] ?: deterministicMigrationId("session", uid, it) }

                val catchRef = db.collection("users").document(uid).collection("catches").document(cloudCatchId)
                val existingSnap = catchRef.get().await()
                if (!existingSnap.exists()) {
                    val domainCatch = catchEntity.toDomain(uid).copy(
                        spotId = remappedSpotId,
                        sessionId = remappedSessionId
                    )
                    catchRef.set(domainCatch.toCreateMap()).await()
                }
                if (!remappedSessionId.isNullOrBlank()) {
                    affectedCloudSessionIds.add(remappedSessionId)
                }
                guestDao.markCatchMigrated(catchEntity.id, uid)
                catchesMigrated++
            }

            // Refresh catch counts on migrated sessions
            for (cloudSid in affectedCloudSessionIds) {
                recalculateSessionCatchCount(uid, cloudSid)
            }

            Result.success(
                MigrationSummary(
                    spotsTransferred = spotsMigrated,
                    sessionsTransferred = sessionsMigrated,
                    catchesTransferred = catchesMigrated
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/migration")
            Result.failure(e)
        }
    }

    private fun deterministicMigrationId(prefix: String, uid: String, localId: String): String {
        val raw = "$uid:$prefix:$localId"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(28)
        return "mig_${prefix}_$hex"
    }
}

/**
 * Fully offline Room-backed data source for Guest Mode.
 * Requires zero internet connectivity and persists across app restarts.
 */
class GuestLocalDataSource(
    private val dao: GuestFishLogDao
) : FishLogDataSource {

    override val isGuestDataSource: Boolean = true

    override fun observeUserProfile(userId: String): Flow<UserProfile?> {
        return dao.observeProfile().map { entity ->
            entity?.toDomain(userId) ?: UserProfile(
                id = "guest_profile",
                userId = userId,
                displayName = "Guest Angler",
                bio = "Fishing locally in Guest Mode."
            )
        }
    }

    override suspend fun ensureOrSaveUserProfile(profile: UserProfile): Result<Unit> {
        return runCatching {
            dao.upsertProfile(
                GuestProfileEntity(
                    id = "guest_profile",
                    displayName = profile.displayName.ifBlank { "Guest Angler" },
                    bio = profile.bio ?: "",
                    homeWaters = profile.homeWaters ?: "",
                    sharePublicSpots = false,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    override fun observeCatches(userId: String): Flow<List<CatchRecord>> {
        return dao.observeActiveCatches().map { list ->
            list.map { it.toDomain(userId) }
        }
    }

    override suspend fun createCatch(record: CatchRecord): Result<String> {
        return runCatching {
            val entity = GuestCatchEntity.fromDomain(record)
            dao.upsertCatch(entity)
            entity.sessionId?.let { recalculateLocalSessionCount(it) }
            entity.id
        }
    }

    override suspend fun updateCatch(record: CatchRecord, previousSessionId: String?): Result<Unit> {
        return runCatching {
            require(record.id.isNotBlank()) { "Catch ID required for update" }
            val entity = GuestCatchEntity.fromDomain(record, idOverride = record.id)
            dao.upsertCatch(entity)
            if (!previousSessionId.isNullOrBlank() && previousSessionId != entity.sessionId) {
                recalculateLocalSessionCount(previousSessionId)
            }
            entity.sessionId?.let { recalculateLocalSessionCount(it) }
        }
    }

    override suspend fun deleteCatch(catchId: String, sessionId: String?): Result<Unit> {
        return runCatching {
            dao.deleteCatchById(catchId)
            if (!sessionId.isNullOrBlank()) {
                recalculateLocalSessionCount(sessionId)
            }
        }
    }

    private suspend fun recalculateLocalSessionCount(sessionId: String) {
        val existing = dao.getSessionById(sessionId) ?: return
        val count = dao.countActiveCatchesForSession(sessionId).toLong()
        dao.updateSessionCatchCount(existing.id, count, System.currentTimeMillis())
    }

    override fun observeSessions(userId: String): Flow<List<FishingSession>> {
        return dao.observeActiveSessions().map { list ->
            list.map { it.toDomain(userId) }
        }
    }

    override suspend fun createSession(session: FishingSession): Result<String> {
        return runCatching {
            val entity = GuestSessionEntity.fromDomain(session)
            dao.upsertSession(entity)
            entity.id
        }
    }

    override suspend fun updateSession(session: FishingSession): Result<Unit> {
        return runCatching {
            require(session.id.isNotBlank()) { "Session ID required for update" }
            val entity = GuestSessionEntity.fromDomain(session, idOverride = session.id)
            dao.upsertSession(entity)
        }
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        return runCatching {
            dao.deleteSessionById(sessionId)
        }
    }

    override fun observeAllVisibleSpots(userId: String): Flow<List<FishingSpot>> {
        return dao.observeActiveSpots().map { list ->
            list.map { it.toDomain(userId) }
        }
    }

    override suspend fun createSpot(spot: FishingSpot): Result<String> {
        return runCatching {
            val entity = GuestSpotEntity.fromDomain(spot)
            dao.upsertSpot(entity)
            entity.id
        }
    }

    override suspend fun updateSpot(spot: FishingSpot): Result<Unit> {
        return runCatching {
            require(spot.id.isNotBlank()) { "Spot ID required for update" }
            val entity = GuestSpotEntity.fromDomain(spot, idOverride = spot.id)
            dao.upsertSpot(entity)
        }
    }

    override suspend fun deleteSpot(spotId: String): Result<Unit> {
        return runCatching {
            dao.deleteSpotById(spotId)
        }
    }
}
