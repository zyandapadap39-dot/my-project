package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.CatchRecord
import com.example.data.model.FishingSession
import com.example.data.model.FishingSpot
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
import java.util.Date
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "guest_profile")
data class GuestProfileEntity(
    @PrimaryKey val id: String = "guest_profile",
    val displayName: String = "Guest Angler",
    val bio: String = "Fishing locally in Guest Mode.",
    val homeWaters: String = "",
    val sharePublicSpots: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toDomain(userId: String = "guest_local"): UserProfile = UserProfile(
        id = id,
        userId = userId,
        displayName = displayName,
        bio = bio,
        homeWaters = homeWaters,
        sharePublicSpots = sharePublicSpots,
        createdAt = Timestamp(Date(updatedAtMillis)),
        updatedAt = Timestamp(Date(updatedAtMillis))
    )
}

@Entity(tableName = "guest_catches")
data class GuestCatchEntity(
    @PrimaryKey val id: String,
    val species: String,
    val weightKg: Double,
    val lengthCm: Double,
    val caughtAtEpochMillis: Long,
    val locationName: String,
    val spotId: String?,
    val sessionId: String?,
    val latitude: Double?,
    val longitude: Double?,
    val baitOrLure: String,
    val fishingMethod: String,
    val waterConditions: String,
    val photoBase64: String?,
    val notes: String?,
    val released: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val migratedToUid: String? = null
) {
    fun toDomain(userId: String = "guest_local"): CatchRecord = CatchRecord(
        id = id,
        userId = userId,
        species = species,
        weightKg = weightKg,
        lengthCm = lengthCm,
        caughtAtEpochMillis = caughtAtEpochMillis,
        locationName = locationName,
        spotId = spotId,
        sessionId = sessionId,
        latitude = latitude,
        longitude = longitude,
        baitOrLure = baitOrLure,
        fishingMethod = fishingMethod,
        waterConditions = waterConditions,
        photoBase64 = photoBase64,
        notes = notes,
        released = released,
        createdAt = Timestamp(Date(createdAtMillis)),
        updatedAt = Timestamp(Date(updatedAtMillis))
    )

    companion object {
        fun fromDomain(record: CatchRecord, idOverride: String? = null): GuestCatchEntity {
            val now = System.currentTimeMillis()
            val resolvedId = idOverride ?: record.id.ifBlank { "guest_catch_${java.util.UUID.randomUUID()}" }
            return GuestCatchEntity(
                id = resolvedId,
                species = record.species.trim(),
                weightKg = record.weightKg,
                lengthCm = record.lengthCm,
                caughtAtEpochMillis = record.caughtAtEpochMillis,
                locationName = record.locationName.trim(),
                spotId = record.spotId?.takeIf { it.isNotBlank() },
                sessionId = record.sessionId?.takeIf { it.isNotBlank() },
                latitude = record.latitude,
                longitude = record.longitude,
                baitOrLure = record.baitOrLure.trim(),
                fishingMethod = record.fishingMethod.trim(),
                waterConditions = record.waterConditions.trim(),
                photoBase64 = record.photoBase64?.takeIf { it.isNotBlank() },
                notes = record.notes?.trim()?.takeIf { it.isNotBlank() },
                released = record.released,
                createdAtMillis = record.createdAt?.toDate()?.time ?: now,
                updatedAtMillis = now
            )
        }
    }
}

@Entity(tableName = "guest_sessions")
data class GuestSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val locationName: String,
    val spotId: String?,
    val startedAtEpochMillis: Long,
    val durationMinutes: Long,
    val weatherSummary: String?,
    val waterConditions: String?,
    val experienceNotes: String,
    val photoBase64: String?,
    val catchCount: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val migratedToUid: String? = null
) {
    fun toDomain(userId: String = "guest_local"): FishingSession = FishingSession(
        id = id,
        userId = userId,
        title = title,
        locationName = locationName,
        spotId = spotId,
        startedAtEpochMillis = startedAtEpochMillis,
        durationMinutes = durationMinutes,
        weatherSummary = weatherSummary,
        waterConditions = waterConditions,
        experienceNotes = experienceNotes,
        photoBase64 = photoBase64,
        catchCount = catchCount,
        createdAt = Timestamp(Date(createdAtMillis)),
        updatedAt = Timestamp(Date(updatedAtMillis))
    )

    companion object {
        fun fromDomain(session: FishingSession, idOverride: String? = null): GuestSessionEntity {
            val now = System.currentTimeMillis()
            val resolvedId = idOverride ?: session.id.ifBlank { "guest_session_${java.util.UUID.randomUUID()}" }
            return GuestSessionEntity(
                id = resolvedId,
                title = session.title.trim(),
                locationName = session.locationName.trim(),
                spotId = session.spotId?.takeIf { it.isNotBlank() },
                startedAtEpochMillis = session.startedAtEpochMillis,
                durationMinutes = session.durationMinutes,
                weatherSummary = session.weatherSummary?.trim()?.takeIf { it.isNotBlank() },
                waterConditions = session.waterConditions?.trim()?.takeIf { it.isNotBlank() },
                experienceNotes = session.experienceNotes.trim(),
                photoBase64 = session.photoBase64?.takeIf { it.isNotBlank() },
                catchCount = session.catchCount,
                createdAtMillis = session.createdAt?.toDate()?.time ?: now,
                updatedAtMillis = now
            )
        }
    }
}

@Entity(tableName = "guest_spots")
data class GuestSpotEntity(
    @PrimaryKey val id: String,
    val ownerName: String?,
    val name: String,
    val description: String,
    val waterBodyType: String,
    val latitude: Double,
    val longitude: Double,
    val isShared: Boolean,
    val isSuccessful: Boolean,
    val targetSpecies: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val migratedToUid: String? = null
) {
    fun toDomain(userId: String = "guest_local"): FishingSpot = FishingSpot(
        id = id,
        userId = userId,
        ownerName = ownerName,
        name = name,
        description = description,
        waterBodyType = waterBodyType,
        latitude = latitude,
        longitude = longitude,
        isShared = false, // Guest spots are always local/private until synced to an account
        isSuccessful = isSuccessful,
        targetSpecies = targetSpecies,
        createdAt = Timestamp(Date(createdAtMillis)),
        updatedAt = Timestamp(Date(updatedAtMillis))
    )

    companion object {
        fun fromDomain(spot: FishingSpot, idOverride: String? = null): GuestSpotEntity {
            val now = System.currentTimeMillis()
            val resolvedId = idOverride ?: spot.id.ifBlank { "guest_spot_${java.util.UUID.randomUUID()}" }
            return GuestSpotEntity(
                id = resolvedId,
                ownerName = spot.ownerName?.trim()?.takeIf { it.isNotBlank() },
                name = spot.name.trim(),
                description = spot.description.trim(),
                waterBodyType = spot.waterBodyType.trim(),
                latitude = spot.latitude,
                longitude = spot.longitude,
                isShared = false,
                isSuccessful = spot.isSuccessful,
                targetSpecies = spot.targetSpecies?.trim()?.takeIf { it.isNotBlank() },
                createdAtMillis = spot.createdAt?.toDate()?.time ?: now,
                updatedAtMillis = now
            )
        }
    }
}

@Dao
interface GuestFishLogDao {
    // Profile
    @Query("SELECT * FROM guest_profile WHERE id = 'guest_profile' LIMIT 1")
    fun observeProfile(): Flow<GuestProfileEntity?>

    @Query("SELECT * FROM guest_profile WHERE id = 'guest_profile' LIMIT 1")
    suspend fun getProfileOnce(): GuestProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: GuestProfileEntity)

    // Catches
    @Query("SELECT * FROM guest_catches WHERE migratedToUid IS NULL ORDER BY caughtAtEpochMillis DESC")
    fun observeActiveCatches(): Flow<List<GuestCatchEntity>>

    @Query("SELECT * FROM guest_catches WHERE migratedToUid IS NULL")
    suspend fun getUnmigratedCatches(): List<GuestCatchEntity>

    @Query("SELECT COUNT(*) FROM guest_catches WHERE migratedToUid IS NULL AND sessionId = :sessionId")
    suspend fun countActiveCatchesForSession(sessionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatch(entity: GuestCatchEntity)

    @Query("DELETE FROM guest_catches WHERE id = :catchId")
    suspend fun deleteCatchById(catchId: String)

    @Query("UPDATE guest_catches SET migratedToUid = :uid WHERE id = :catchId")
    suspend fun markCatchMigrated(catchId: String, uid: String)

    // Sessions
    @Query("SELECT * FROM guest_sessions WHERE migratedToUid IS NULL ORDER BY startedAtEpochMillis DESC")
    fun observeActiveSessions(): Flow<List<GuestSessionEntity>>

    @Query("SELECT * FROM guest_sessions WHERE migratedToUid IS NULL")
    suspend fun getUnmigratedSessions(): List<GuestSessionEntity>

    @Query("SELECT * FROM guest_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): GuestSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(entity: GuestSessionEntity)

    @Query("UPDATE guest_sessions SET catchCount = :count, updatedAtMillis = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionCatchCount(sessionId: String, count: Long, updatedAt: Long)

    @Query("DELETE FROM guest_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Query("UPDATE guest_sessions SET migratedToUid = :uid WHERE id = :sessionId")
    suspend fun markSessionMigrated(sessionId: String, uid: String)

    // Spots
    @Query("SELECT * FROM guest_spots WHERE migratedToUid IS NULL ORDER BY createdAtMillis DESC")
    fun observeActiveSpots(): Flow<List<GuestSpotEntity>>

    @Query("SELECT * FROM guest_spots WHERE migratedToUid IS NULL")
    suspend fun getUnmigratedSpots(): List<GuestSpotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSpot(entity: GuestSpotEntity)

    @Query("DELETE FROM guest_spots WHERE id = :spotId")
    suspend fun deleteSpotById(spotId: String)

    @Query("UPDATE guest_spots SET migratedToUid = :uid WHERE id = :spotId")
    suspend fun markSpotMigrated(spotId: String, uid: String)
}

@Database(
    entities = [
        GuestProfileEntity::class,
        GuestCatchEntity::class,
        GuestSessionEntity::class,
        GuestSpotEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GuestFishLogDatabase : RoomDatabase() {
    abstract fun guestDao(): GuestFishLogDao

    companion object {
        @Volatile
        private var INSTANCE: GuestFishLogDatabase? = null

        fun getInstance(context: Context): GuestFishLogDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GuestFishLogDatabase::class.java,
                    "fishlog_guest_offline.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
