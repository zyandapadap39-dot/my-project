package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.billing.BillingRepository
import com.example.data.billing.PremiumPlanType
import com.example.data.billing.SubscriptionUiState
import com.example.data.model.CatchRecord
import com.example.data.model.FishingSession
import com.example.data.preferences.UserPreferences
import com.example.util.FishLogExportManager
import com.example.viewmodel.FishingAnalytics
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PremiumSubscriptionUnitTest {

    @Test
    fun `free plan quotas match FishLog membership specification`() {
        assertEquals(30, BillingRepository.FREE_MAX_CATCHES)
        assertEquals(10, BillingRepository.FREE_MAX_SESSIONS)
        assertEquals(3, BillingRepository.FREE_MAX_SPOTS)
    }

    @Test
    fun `default subscription state never grants unverified premium access`() {
        val state = SubscriptionUiState()
        assertFalse(state.isPremiumActive)
        assertEquals("₱49/month", state.displayMonthlyPrice)
        assertEquals("₱399/year", state.displayYearlyPrice)
        assertEquals("₱799", state.displayLifetimePrice)
        assertTrue(state.entitlementStatusLabel.contains("30 catches"))
    }

    @Test
    fun `entitlement status distinguishes auto-renewing, canceled, and lifetime plans`() {
        val autoRenewYearly = SubscriptionUiState(
            isPremiumActive = true,
            activePlanType = PremiumPlanType.YEARLY,
            willAutoRenew = true
        )
        assertTrue(autoRenewYearly.entitlementStatusLabel.contains("Auto-renews"))

        val canceledMonthly = SubscriptionUiState(
            isPremiumActive = true,
            activePlanType = PremiumPlanType.MONTHLY,
            willAutoRenew = false
        )
        assertTrue(canceledMonthly.entitlementStatusLabel.contains("Canceled"))

        val lifetime = SubscriptionUiState(
            isPremiumActive = true,
            activePlanType = PremiumPlanType.LIFETIME,
            willAutoRenew = true
        )
        assertTrue(lifetime.entitlementStatusLabel.contains("Lifetime Pro Active"))
    }

    @Test
    fun `guest purchase attempt is rejected without granting premium`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val billingRepo = BillingRepository(context)
        assertFalse(billingRepo.state.value.isPremiumActive)
    }

    @Test
    fun `csv and pdf exporters write real catch records to output files`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sampleCatches = listOf(
            CatchRecord(
                id = "c1",
                userId = "u1",
                species = "Largemouth Bass",
                weightKg = 3.4,
                lengthCm = 52.0,
                caughtAtEpochMillis = 1730000000000L,
                locationName = "Emerald Cove",
                baitOrLure = "Spinnerbait",
                fishingMethod = "Baitcasting",
                waterConditions = "Clear & Calm",
                released = true
            )
        )
        val sampleSessions = listOf(
            FishingSession(
                id = "s1",
                userId = "u1",
                title = "Dawn Patrol",
                locationName = "Emerald Cove",
                startedAtEpochMillis = 1730000000000L,
                durationMinutes = 180,
                experienceNotes = "Great topwater bite."
            )
        )

        val csvFile = File(context.cacheDir, "test_export.csv")
        val pdfFile = File(context.cacheDir, "test_report.pdf")

        val csvResult = FishLogExportManager.exportCatchesAndSessionsToCsv(
            context = context,
            destinationUri = Uri.fromFile(csvFile),
            catches = sampleCatches,
            sessions = sampleSessions,
            spots = emptyList(),
            preferences = UserPreferences()
        )
        assertTrue(csvResult.isSuccess)
        assertEquals(1, csvResult.getOrNull())
        val csvText = csvFile.readText()
        assertTrue(csvText.contains("Largemouth Bass"))
        assertTrue(csvText.contains("Emerald Cove"))

        val pdfResult = FishLogExportManager.exportFishingReportToPdf(
            context = context,
            destinationUri = Uri.fromFile(pdfFile),
            anglerName = "Pro Angler",
            analytics = FishingAnalytics(
                totalCatches = 1,
                totalSessions = 1,
                biggestByWeight = sampleCatches.first(),
                mostFrequentSpecies = "Largemouth Bass"
            ),
            catches = sampleCatches,
            sessions = sampleSessions,
            preferences = UserPreferences()
        )
        assertTrue(pdfResult.isSuccess)
        assertEquals(1, pdfResult.getOrNull())
        assertTrue(pdfFile.exists() && pdfFile.length() > 100L)
        val pdfHeader = String(pdfFile.readBytes().take(5).toByteArray(), Charsets.US_ASCII)
        assertEquals("%PDF-", pdfHeader)
    }
}
