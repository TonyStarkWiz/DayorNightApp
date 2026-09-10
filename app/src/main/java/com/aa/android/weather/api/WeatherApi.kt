package com.aa.android.weather.api

import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "rain",
        @Query("current") current: String = "is_day",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_hours") forecastHours: Int = 2
    ): ForecastResponse
}
