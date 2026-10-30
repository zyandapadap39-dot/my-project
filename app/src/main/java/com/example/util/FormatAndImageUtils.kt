package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.preferences.LengthUnit
import com.example.data.preferences.WeightUnit
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageEncodingUtils {

    suspend fun encodeUriToBase64Thumbnail(
        context: Context,
        uri: Uri,
        maxDimensionPx: Int = 720,
        jpegQuality: Int = 72
    ): String? = withContext(Dispatchers.IO) {
        try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            val origW = boundsOptions.outWidth
            val origH = boundsOptions.outHeight
            if (origW <= 0 || origH <= 0) return@withContext null

            var sampleSize = 1
            while ((origW / sampleSize) > maxDimensionPx * 2 || (origH / sampleSize) > maxDimensionPx * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            val ratio = minOf(
                maxDimensionPx.toFloat() / decoded.width.toFloat(),
                maxDimensionPx.toFloat() / decoded.height.toFloat(),
                1f
            )
            val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
            val scaled = if (targetW != decoded.width || targetH != decoded.height) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            } else {
                decoded
            }

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            val bytes = out.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun decodeBase64ToImageBitmap(base64: String?): ImageBitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

object UnitFormatter {
    private const val KG_TO_LB = 2.2046226
    private const val CM_TO_IN = 0.39370078

    fun formatWeight(weightKg: Double, unit: WeightUnit): String {
        val converted = if (unit == WeightUnit.LB) weightKg * KG_TO_LB else weightKg
        return String.format(Locale.US, "%.2f %s", converted, unit.symbol)
    }

    fun displayWeightValue(weightKg: Double, unit: WeightUnit): Double {
        return if (unit == WeightUnit.LB) weightKg * KG_TO_LB else weightKg
    }

    fun inputWeightToKg(inputValue: Double, unit: WeightUnit): Double {
        return if (unit == WeightUnit.LB) inputValue / KG_TO_LB else inputValue
    }

    fun formatLength(lengthCm: Double, unit: LengthUnit): String {
        val converted = if (unit == LengthUnit.IN) lengthCm * CM_TO_IN else lengthCm
        return String.format(Locale.US, "%.1f %s", converted, unit.symbol)
    }

    fun displayLengthValue(lengthCm: Double, unit: LengthUnit): Double {
        return if (unit == LengthUnit.IN) lengthCm * CM_TO_IN else lengthCm
    }

    fun inputLengthToCm(inputValue: Double, unit: LengthUnit): Double {
        return if (unit == LengthUnit.IN) inputValue / CM_TO_IN else inputValue
    }

    fun formatTemperature(tempCelsius: Double, useImperial: Boolean): String {
        return if (useImperial) {
            val f = (tempCelsius * 9.0 / 5.0) + 32.0
            String.format(Locale.US, "%.0f°F", f)
        } else {
            String.format(Locale.US, "%.0f°C", tempCelsius)
        }
    }

    fun formatDateTime(epochMillis: Long): String {
        if (epochMillis <= 0L) return "Unknown date"
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }

    fun formatShortDate(epochMillis: Long): String {
        if (epochMillis <= 0L) return ""
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }

    fun formatDuration(minutes: Long): String {
        val hrs = minutes / 60
        val mins = minutes % 60
        return when {
            hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
            hrs > 0 -> "${hrs}h"
            else -> "${mins}m"
        }
    }
}
