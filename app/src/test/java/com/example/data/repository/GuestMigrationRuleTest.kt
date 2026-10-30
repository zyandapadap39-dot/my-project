package com.example.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.GuestFishLogDatabase
import com.example.data.model.CatchRecord
import com.example.data.model.FishingSession
import com.example.data.model.FishingSpot
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GuestMigrationRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var guestDb: GuestFishLogDatabase

    @Before
    fun setUpGuestDb() {
        guestDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GuestFishLogDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeGuestDb() {
        guestDb.close()
    }

    @Test
    fun guestLocalDataSource_persistsCatchesPhotosJournalsAndSpotsOffline() = runBlocking {
        val guestDataSource = GuestLocalDataSource(guestDb.guestDao())
        val guestId = "guest_local"

        val spotId = guestDataSource.createSpot(
            FishingSpot(
                name = "Hidden Pine Cove",
                description = "Shallow lily pad edge",
                waterBodyType = "Lake",
                latitude = 45.1234,
                longitude = -122.4567,
                isShared = false,
                isSuccessful = true,
                targetSpecies = "Largemouth Bass"
            )
        ).getOrThrow()

        val sessionId = guestDataSource.createSession(
            FishingSession(
                title = "Offline Morning Session",
                locationName = "Hidden Pine Cove",
                spotId = spotId,
                startedAtEpochMillis = 1730000000000L,
                durationMinutes = 120L,
                experienceNotes = "Calm water before sunrise.",
                photoBase64 = "BASE64_SESSION_PHOTO_DATA"
            )
        ).getOrThrow()

        val catchId = guestDataSource.createCatch(
            CatchRecord(
                species = "Largemouth Bass",
                weightKg = 3.4,
                lengthCm = 52.0,
                caughtAtEpochMillis = 1730001800000L,
                locationName = "Hidden Pine Cove",
                spotId = spotId,
                sessionId = sessionId,
                baitOrLure = "Hollow Body Frog",
                fishingMethod = "Baitcasting",
                waterConditions = "Stained, 68F",
                photoBase64 = "BASE64_FISH_PHOTO_DATA",
                notes = "Topwater strike!",
                released = true
            )
        ).getOrThrow()

        val catches = guestDataSource.observeCatches(guestId).first()
        val sessions = guestDataSource.observeSessions(guestId).first()
        val spots = guestDataSource.observeAllVisibleSpots(guestId).first()

        assertEquals(1, catches.size)
        assertEquals(catchId, catches.first().id)
        assertEquals("BASE64_FISH_PHOTO_DATA", catches.first().photoBase64)

        assertEquals(1, sessions.size)
        assertEquals(1L, sessions.first().catchCount)
        assertEquals("BASE64_SESSION_PHOTO_DATA", sessions.first().photoBase64)

        assertEquals(1, spots.size)
        assertEquals("Hidden Pine Cove", spots.first().name)
    }

    @Test
    fun guestToAccountMigration_transfersRecordsPreservesLinks_preventsDuplicatesAndNeverOverwritesCloud() = runBlocking {
        val dao = guestDb.guestDao()
        val guestDataSource = GuestLocalDataSource(dao)

        // 1. Author records while offline in Guest Mode
        guestDataSource.ensureOrSaveUserProfile(
            UserProfile(
                userId = "guest_local",
                displayName = "Temporary Guest Name",
                bio = "Guest local bio"
            )
        )

        val localSpotId = guestDataSource.createSpot(
            FishingSpot(
                name = "Emerald Riffle",
                description = "Deep tailout below boulder",
                waterBodyType = "River",
                latitude = 44.801,
                longitude = -121.102,
                isShared = false,
                isSuccessful = true,
                targetSpecies = "Brown Trout"
            )
        ).getOrThrow()

        val localSessionId = guestDataSource.createSession(
            FishingSession(
                title = "Evening Caddis Hatch",
                locationName = "Emerald Riffle",
                spotId = localSpotId,
                startedAtEpochMillis = 1730100000000L,
                durationMinutes = 150L,
                experienceNotes = "Steady surface rises at dusk.",
                photoBase64 = "SESSION_JPEG_BASE64_PAYLOAD"
            )
        ).getOrThrow()

        guestDataSource.createCatch(
            CatchRecord(
                species = "Brown Trout",
                weightKg = 2.9,
                lengthCm = 58.5,
                caughtAtEpochMillis = 1730102000000L,
                locationName = "Emerald Riffle",
                spotId = localSpotId,
                sessionId = localSessionId,
                baitOrLure = "Elk Hair Caddis #14",
                fishingMethod = "Fly Fishing",
                waterConditions = "Clear, 54F",
                photoBase64 = "CATCH_JPEG_BASE64_PAYLOAD",
                notes = "Wild butter-gold brown",
                released = true
            )
        ).getOrThrow()

        // 2. Sign in to an account that ALREADY has an existing cloud profile & 1 cloud catch
        val uid = signInTestUser("migrating.angler@test.com")
        val cloudRepo = FishLogRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            cloudRepo.ensureOrSaveUserProfile(
                UserProfile(
                    userId = uid,
                    displayName = "Existing Cloud Angler",
                    bio = "Existing Cloud Bio Must Not Be Overwritten",
                    homeWaters = "Deschutes River"
                )
            ).getOrThrow()

            cloudRepo.createCatch(
                CatchRecord(
                    species = "Steelhead",
                    weightKg = 5.4,
                    lengthCm = 76.0,
                    caughtAtEpochMillis = 1729900000000L,
                    locationName = "Lower Canyon",
                    baitOrLure = "Intruder Fly",
                    fishingMethod = "Fly Fishing",
                    waterConditions = "Green glacial tint",
                    released = true
                )
            ).getOrThrow()
        }

        // 3. Execute Guest-to-Account Migration
        val firstSummary = withTimeout(DEFAULT_TIMEOUT_MS) {
            cloudRepo.migrateGuestDataToAccount(dao).getOrThrow()
        }
        assertEquals(1, firstSummary.spotsTransferred)
        assertEquals(1, firstSummary.sessionsTransferred)
        assertEquals(1, firstSummary.catchesTransferred)

        // 4. Verify cloud state: 2 total catches (1 pre-existing + 1 migrated), photos & links preserved
        val cloudCatches = withTimeout(FLOW_TIMEOUT_MS) {
            cloudRepo.observeCatches(uid).first { it.size == 2 }
        }
        val migratedCatch = cloudCatches.first { it.species == "Brown Trout" }
        assertEquals("CATCH_JPEG_BASE64_PAYLOAD", migratedCatch.photoBase64)

        val cloudSpots = withTimeout(FLOW_TIMEOUT_MS) {
            cloudRepo.observeMySpots(uid).first { it.isNotEmpty() }
        }
        assertEquals(1, cloudSpots.size)
        assertEquals(cloudSpots.first().id, migratedCatch.spotId)

        val cloudSessions = withTimeout(FLOW_TIMEOUT_MS) {
            cloudRepo.observeSessions(uid).first { it.isNotEmpty() }
        }
        assertEquals(1, cloudSessions.size)
        assertEquals(cloudSessions.first().id, migratedCatch.sessionId)
        assertEquals("SESSION_JPEG_BASE64_PAYLOAD", cloudSessions.first().photoBase64)
        assertEquals(1L, cloudSessions.first().catchCount)

        // Verify existing cloud profile was NEVER overwritten by guest profile
        val cloudProfile = withTimeout(FLOW_TIMEOUT_MS) {
            cloudRepo.observeUserProfile(uid).first { it != null }
        }
        assertNotNull(cloudProfile)
        assertEquals("Existing Cloud Angler", cloudProfile?.displayName)
        assertEquals("Existing Cloud Bio Must Not Be Overwritten", cloudProfile?.bio)

        // 5. Re-run migration and verify idempotency (zero duplicate records)
        val secondSummary = withTimeout(DEFAULT_TIMEOUT_MS) {
            cloudRepo.migrateGuestDataToAccount(dao).getOrThrow()
        }
        assertEquals(0, secondSummary.catchesTransferred)
        assertEquals(0, secondSummary.sessionsTransferred)
        assertEquals(0, secondSummary.spotsTransferred)

        val postRetryCatches = withTimeout(FLOW_TIMEOUT_MS) {
            cloudRepo.observeCatches(uid).first()
        }
        assertEquals(2, postRetryCatches.size)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
