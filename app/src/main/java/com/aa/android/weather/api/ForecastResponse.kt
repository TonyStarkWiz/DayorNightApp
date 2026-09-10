package com.aa.android.weather.api

import com.squareup.moshi.Json

data class ForecastResponse(
    val current: CurrentWeather,
    val hourly: HourlyForecast? = null
)

data class CurrentWeather(
    @Json(name = "is_day") val isDay: Int
)

data class HourlyForecast(
    val time: List<String> = emptyList(),
    val rain: List<Double> = emptyList()
)
