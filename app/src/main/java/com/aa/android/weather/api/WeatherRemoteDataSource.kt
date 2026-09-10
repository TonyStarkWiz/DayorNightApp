package com.aa.android.weather.api

import com.aa.android.weather.error.AppException
import com.aa.android.weather.error.ErrorMapper

class WeatherRemoteDataSource(
    private val weatherApi: WeatherApi,
    private val errorMapper: ErrorMapper
) {

    suspend fun getForecast(latitude: Double, longitude: Double): ForecastResponse {
        return try {
            weatherApi.getForecast(latitude, longitude)
        } catch (e: Exception) {
            throw AppException(errorMapper.map(e), e)
        }
    }
}
