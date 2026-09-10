package com.aa.android.weather.repository

import com.aa.android.weather.db.CityForecast
import com.aa.android.weather.error.AppError

data class CityLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

fun interface CityLookup {
    suspend fun find(cityName: String): CityLocation?
}

sealed class WeatherResult {
    data class Success(val forecast: CityForecast, val fromCache: Boolean) : WeatherResult()
    object Empty : WeatherResult()
    data class Failure(val error: AppError, val cached: CityForecast?) : WeatherResult()
}

interface WeatherRepository {
    suspend fun getCityWeather(cityName: String): WeatherResult
}
