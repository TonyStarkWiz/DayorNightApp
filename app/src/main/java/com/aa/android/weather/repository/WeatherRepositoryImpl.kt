package com.aa.android.weather.repository

import com.aa.android.weather.api.WeatherRemoteDataSource
import com.aa.android.weather.db.CityForecast
import com.aa.android.weather.db.ForecastDao
import com.aa.android.weather.dispatchers.DispatcherProvider
import com.aa.android.weather.error.AppError
import com.aa.android.weather.error.AppException
import com.aa.android.weather.error.ErrorMapper
import com.aa.android.weather.network.NetworkChecker
import kotlinx.coroutines.withContext

class WeatherRepositoryImpl(
    private val remoteDataSource: WeatherRemoteDataSource,
    private val cityLookup: CityLookup,
    private val forecastDao: ForecastDao,
    private val networkChecker: NetworkChecker,
    private val dispatchers: DispatcherProvider,
    private val errorMapper: ErrorMapper,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : WeatherRepository {

    override suspend fun getCityWeather(cityName: String): WeatherResult {
        return withContext(dispatchers.io) {
            val cacheKey = cityName.trim().lowercase()
            val cached = forecastDao.getByQuery(cacheKey)
            val now = timeProvider()

            // No radio: never call Retrofit. Prefer the last saved row for this city query.
            if (!networkChecker.isOnline()) {
                return@withContext if (cached != null) {
                    WeatherResult.Success(cached, fromCache = true)
                } else {
                    WeatherResult.Failure(AppError.NoInternet, cached = null)
                }
            }

            // Online but the row is still fresh: skip Open-Meteo to avoid extra calls around sunrise checks.
            if (cached != null && now - cached.savedAt < CACHE_TTL_MS) {
                return@withContext WeatherResult.Success(cached, fromCache = true)
            }

            val city = cityLookup.find(cityName)
            if (city == null) {
                return@withContext if (cached != null) {
                    WeatherResult.Success(cached, fromCache = true)
                } else {
                    WeatherResult.Empty
                }
            }

            try {
                val forecast = remoteDataSource.getForecast(city.latitude, city.longitude)
                val nextHourRain = forecast.hourly?.rain?.getOrNull(1) ?: 0.0
                val fresh = CityForecast(
                    cityQuery = cacheKey,
                    cityName = city.name,
                    isDay = forecast.current.isDay == 1,
                    mightRain = nextHourRain > 0.0,
                    savedAt = now
                )
                forecastDao.upsert(fresh)
                WeatherResult.Success(fresh, fromCache = false)
            } catch (e: Exception) {
                val error = if (e is AppException) e.error else errorMapper.map(e)
                // Keep the old row on screen when the live call fails.
                if (cached != null) {
                    WeatherResult.Failure(error, cached)
                } else {
                    WeatherResult.Failure(error, null)
                }
            }
        }
    }

    companion object {
        const val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes; shorter = more API calls, longer = stale day/night near sunrise
    }
}
