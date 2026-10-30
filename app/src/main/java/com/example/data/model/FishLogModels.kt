package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val bio: String? = null,
    val homeWaters: String? = null,
    val avatarUrl: String? = null,
    val sharePublicSpots: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "displayName" to displayName,
            "bio" to bio?.takeIf { it.isNotBlank() },
            "homeWaters" to homeWaters?.takeIf { it.isNotBlank() },
            "avatarUrl" to avatarUrl?.takeIf { it.isNotBlank() },
            "sharePublicSpots" to sharePublicSpots,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        return mapOf(
            "displayName" to displayName,
            "bio" to (bio ?: ""),
            "homeWaters" to (homeWaters ?: ""),
            "avatarUrl" to avatarUrl?.takeIf { it.isNotBlank() },
            "sharePublicSpots" to sharePublicSpots,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }
}

@IgnoreExtraProperties
data class CatchRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val species: String = "",
    val weightKg: Double = 0.0,
    val lengthCm: Double = 0.0,
    val caughtAtEpochMillis: Long = 0L,
    val locationName: String = "",
    val spotId: String? = null,
    val sessionId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val baitOrLure: String = "",
    val fishingMethod: String = "",
    val waterConditions: String = "",
    val photoBase64: String? = null,
    val notes: String? = null,
    val released: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "species" to species.trim(),
            "weightKg" to weightKg,
            "lengthCm" to lengthCm,
            "caughtAtEpochMillis" to caughtAtEpochMillis,
            "locationName" to locationName.trim(),
            "spotId" to spotId?.takeIf { it.isNotBlank() },
            "sessionId" to sessionId?.takeIf { it.isNotBlank() },
            "latitude" to latitude,
            "longitude" to longitude,
            "baitOrLure" to baitOrLure.trim(),
            "fishingMethod" to fishingMethod.trim(),
            "waterConditions" to waterConditions.trim(),
            "photoBase64" to photoBase64?.takeIf { it.isNotBlank() },
            "notes" to notes?.trim()?.takeIf { it.isNotBlank() },
            "released" to released,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        return mapOf(
            "species" to species.trim(),
            "weightKg" to weightKg,
            "lengthCm" to lengthCm,
            "caughtAtEpochMillis" to caughtAtEpochMillis,
            "locationName" to locationName.trim(),
            "spotId" to (spotId ?: ""),
            "sessionId" to (sessionId ?: ""),
            "latitude" to latitude,
            "longitude" to longitude,
            "baitOrLure" to baitOrLure.trim(),
            "fishingMethod" to fishingMethod.trim(),
            "waterConditions" to waterConditions.trim(),
            "photoBase64" to (photoBase64 ?: ""),
            "notes" to (notes?.trim() ?: ""),
            "released" to released,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }
}

@IgnoreExtraProperties
data class FishingSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val locationName: String = "",
    val spotId: String? = null,
    val startedAtEpochMillis: Long = 0L,
    val durationMinutes: Long = 60L,
    val weatherSummary: String? = null,
    val waterConditions: String? = null,
    val experienceNotes: String = "",
    val photoBase64: String? = null,
    val catchCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "title" to title.trim(),
            "locationName" to locationName.trim(),
            "spotId" to spotId?.takeIf { it.isNotBlank() },
            "startedAtEpochMillis" to startedAtEpochMillis,
            "durationMinutes" to durationMinutes,
            "weatherSummary" to weatherSummary?.trim()?.takeIf { it.isNotBlank() },
            "waterConditions" to waterConditions?.trim()?.takeIf { it.isNotBlank() },
            "experienceNotes" to experienceNotes.trim(),
            "photoBase64" to photoBase64?.takeIf { it.isNotBlank() },
            "catchCount" to catchCount,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        return mapOf(
            "title" to title.trim(),
            "locationName" to locationName.trim(),
            "spotId" to (spotId ?: ""),
            "startedAtEpochMillis" to startedAtEpochMillis,
            "durationMinutes" to durationMinutes,
            "weatherSummary" to (weatherSummary?.trim() ?: ""),
            "waterConditions" to (waterConditions?.trim() ?: ""),
            "experienceNotes" to experienceNotes.trim(),
            "photoBase64" to (photoBase64 ?: ""),
            "catchCount" to catchCount,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }
}

@IgnoreExtraProperties
data class FishingSpot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ownerName: String? = null,
    val name: String = "",
    val description: String = "",
    val waterBodyType: String = "Lake",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isShared: Boolean = false,
    val isSuccessful: Boolean = true,
    val targetSpecies: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "ownerName" to ownerName?.trim()?.takeIf { it.isNotBlank() },
            "name" to name.trim(),
            "description" to description.trim(),
            "waterBodyType" to waterBodyType.trim(),
            "latitude" to latitude,
            "longitude" to longitude,
            "isShared" to isShared,
            "isSuccessful" to isSuccessful,
            "targetSpecies" to targetSpecies?.trim()?.takeIf { it.isNotBlank() },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        return mapOf(
            "ownerName" to (ownerName?.trim() ?: ""),
            "name" to name.trim(),
            "description" to description.trim(),
            "waterBodyType" to waterBodyType.trim(),
            "latitude" to latitude,
            "longitude" to longitude,
            "isShared" to isShared,
            "isSuccessful" to isSuccessful,
            "targetSpecies" to (targetSpecies?.trim() ?: ""),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
