package com.example.data.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class OpenMeteoResponse(
    val current: CurrentWeatherDto? = null
)

data class CurrentWeatherDto(
    val temperature_2m: Double? = null,
    val wind_speed_10m: Double? = null,
    val surface_pressure: Double? = null,
    val weather_code: Int? = null
)

interface OpenMeteoService {
    @GET("v1/forecast?current=temperature_2m,wind_speed_10m,surface_pressure,weather_code")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): OpenMeteoResponse
}

data class LiveWeatherConditions(
    val temperatureCelsius: Double,
    val windSpeedKmh: Double,
    val pressureHpa: Double,
    val conditionDescription: String,
    val fishingConditionRating: String,
    val locationLabel: String,
    val latitude: Double,
    val longitude: Double
)

class WeatherLocationRepository(private val context: Context) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val weatherService: OpenMeteoService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenMeteoService::class.java)
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentGpsCoordinates(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
            val lastLoc = try {
                client.lastLocation.await()
            } catch (e: Exception) {
                null
            }
            if (lastLoc != null) {
                return@withContext Pair(lastLoc.latitude, lastLoc.longitude)
            }
            val tokenSource = CancellationTokenSource()
            val freshLoc = client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                tokenSource.token
            ).await()
            if (freshLoc != null) {
                Pair(freshLoc.latitude, freshLoc.longitude)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchWeatherForCoordinates(
        latitude: Double,
        longitude: Double,
        fallbackLabel: String? = null
    ): Result<LiveWeatherConditions> = withContext(Dispatchers.IO) {
        try {
            val response = weatherService.getCurrentWeather(latitude, longitude)
            val current = response.current
                ?: return@withContext Result.failure(IllegalStateException("Weather unavailable"))

            val tempC = current.temperature_2m ?: 15.0
            val windKmh = current.wind_speed_10m ?: 8.0
            val pressure = current.surface_pressure ?: 1013.0
            val code = current.weather_code ?: 0

            val conditionDesc = mapWmoCodeToText(code)
            val rating = evaluateBiteConditions(pressure, windKmh, code)
            val resolvedName = fallbackLabel ?: reverseGeocodeLabel(latitude, longitude)

            Result.success(
                LiveWeatherConditions(
                    temperatureCelsius = tempC,
                    windSpeedKmh = windKmh,
                    pressureHpa = pressure,
                    conditionDescription = conditionDesc,
                    fishingConditionRating = rating,
                    locationLabel = resolvedName,
                    latitude = latitude,
                    longitude = longitude
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocodeLabel(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val addr = addresses?.firstOrNull()
            when {
                !addr?.locality.isNullOrBlank() && !addr?.adminArea.isNullOrBlank() ->
                    "${addr?.locality}, ${addr?.adminArea}"
                !addr?.subAdminArea.isNullOrBlank() -> addr?.subAdminArea ?: formatCoords(lat, lon)
                else -> formatCoords(lat, lon)
            }
        } catch (e: Exception) {
            formatCoords(lat, lon)
        }
    }

    private fun formatCoords(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.2f°, %.2f°", lat, lon)

    private fun mapWmoCodeToText(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1, 2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Misty / Fog"
        51, 53, 55 -> "Light Drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Rain Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Fair Conditions"
    }

    private fun evaluateBiteConditions(pressureHpa: Double, windKmh: Double, wmoCode: Int): String {
        if (wmoCode >= 95) return "Unsafe (Storm Activity)"
        if (windKmh > 35.0) return "Challenging (High Wind)"
        if (pressureHpa in 1008.0..1022.0 && windKmh <= 18.0) return "Prime Bite Window"
        if (wmoCode in listOf(2, 3, 51)) return "High Activity (Low Light)"
        return "Steady Angler Conditions"
    }
}
