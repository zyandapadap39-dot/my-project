package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CatchRecord
import com.example.data.model.FishingSession
import com.example.data.model.FishingSpot
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FishLogRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveCatch_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = FishLogRepository(firestore, auth)

        val catchRecord = CatchRecord(
            species = "Rainbow Trout",
            weightKg = 2.6,
            lengthCm = 55.0,
            caughtAtEpochMillis = System.currentTimeMillis(),
            locationName = "Blue Ribbon Creek",
            baitOrLure = "Pheasant Tail Nymph",
            fishingMethod = "Fly Fishing",
            waterConditions = "Clear, 52F",
            notes = "Strong fight near riffle",
            released = true
        )

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createCatch(catchRecord) }
        assertTrue("Expected catch creation to succeed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        val catchId = createResult.getOrThrow()

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCatches(aliceUid).first { list -> list.any { it.id == catchId } }
        }
        val fetched = observed.first { it.id == catchId }
        assertEquals("Rainbow Trout", fetched.species)

        // Update catch
        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateCatch(fetched.copy(weightKg = 2.8, notes = "Measured carefully"))
        }
        assertTrue("Expected catch update to succeed: ${updateResult.exceptionOrNull()}", updateResult.isSuccess)
    }

    @Test
    fun createSession_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = FishLogRepository(firestore, auth)

        val session = FishingSession(
            title = "Autumn Dawn Drift",
            locationName = "Upper Deschutes",
            startedAtEpochMillis = System.currentTimeMillis(),
            durationMinutes = 180L,
            weatherSummary = "Crisp 8C, light breeze",
            waterConditions = "Low & clear",
            experienceNotes = "Steady hatch around 8 AM, landed three healthy rainbows.",
            catchCount = 0L
        )

        val res = withTimeout(DEFAULT_TIMEOUT_MS) { repository.createSession(session) }
        assertTrue("Expected session creation to succeed: ${res.exceptionOrNull()}", res.isSuccess)
        val sessionId = res.getOrThrow()

        val list = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeSessions(aliceUid).first { it.any { s -> s.id == sessionId } }
        }
        assertTrue(list.any { it.id == sessionId })
    }

    @Test
    fun privateSpotIsolation_crossUserCannotReadPrivateSpot() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = FishLogRepository(firestore, auth)

        val privateSpot = FishingSpot(
            name = "Secret Timber Pocket",
            description = "Submerged cedar logs at 14ft",
            waterBodyType = "Reservoir",
            latitude = 44.512,
            longitude = -121.309,
            isShared = false,
            isSuccessful = true,
            targetSpecies = "Largemouth Bass"
        )

        val spotId = withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.createSpot(privateSpot).getOrThrow() }

        // Sign in as Bob
        signInTestUser(BOB_EMAIL)
        val bobRepo = FishLogRepository(firestore, auth)

        val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getSpotById(spotId) }
        assertTrue("Expected cross-user read of private spot to fail", crossReadResult.isFailure)
        val fsEx = crossReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun observeCatches_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val unauthRepo = FishLogRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                unauthRepo.observeCatches(aliceUid).first()
            }
            fail("Expected unauthenticated query to throw PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.fishlog@test.com"
        const val BOB_EMAIL = "bob.fishlog@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
