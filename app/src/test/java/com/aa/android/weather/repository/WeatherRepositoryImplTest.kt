package com.aa.android.weather.repository

import com.aa.android.weather.api.CurrentWeather
import com.aa.android.weather.api.ForecastResponse
import com.aa.android.weather.api.HourlyForecast
import com.aa.android.weather.api.WeatherApi
import com.aa.android.weather.api.WeatherRemoteDataSource
import com.aa.android.weather.db.CityForecast
import com.aa.android.weather.db.ForecastDao
import com.aa.android.weather.dispatchers.DispatcherProvider
import com.aa.android.weather.error.AppError
import com.aa.android.weather.error.ErrorMapper
import com.aa.android.weather.network.NetworkChecker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    @Test
    fun returnsCachedDataWhenOffline() = runTest {
        val dao = FakeForecastDao(
            CityForecast("dallas", "Dallas", true, false, savedAt = 1L)
        )
        val repository = repository(
            dao = dao,
            online = false,
            cityLookup = { null }
        )

        val result = repository.getCityWeather("Dallas") as WeatherResult.Success

        assertEquals("Dallas", result.forecast.cityName)
        assertEquals(true, result.fromCache)
    }

    @Test
    fun returnsNoInternetWhenOfflineAndEmpty() = runTest {
        val result = repository(online = false, cityLookup = { null }).getCityWeather("Dallas")

        val failure = result as WeatherResult.Failure
        assertEquals(AppError.NoInternet, failure.error)
    }

    @Test
    fun skipsApiWhenCacheIsFresh() = runTest {
        val now = 100_000L
        val dao = FakeForecastDao(
            CityForecast("dallas", "Dallas", false, false, savedAt = now - 1_000L)
        )
        val api = FakeWeatherApi()
        val repository = repository(
            api = api,
            dao = dao,
            now = now,
            cityLookup = { error("lookup should not run") }
        )

        val result = repository.getCityWeather("Dallas") as WeatherResult.Success

        assertEquals(true, result.fromCache)
        assertEquals(0, api.calls)
    }

    @Test
    fun fetchesAndCachesWhenOnlineAndStale() = runTest {
        val dao = FakeForecastDao()
        val api = FakeWeatherApi(
            ForecastResponse(
                current = CurrentWeather(isDay = 1),
                hourly = HourlyForecast(rain = listOf(0.0, 2.0))
            )
        )
        val repository = repository(
            api = api,
            dao = dao,
            cityLookup = { CityLocation("Dallas", 32.78, -96.80) }
        )

        val result = repository.getCityWeather("Dallas") as WeatherResult.Success

        assertEquals(false, result.fromCache)
        assertEquals(true, result.forecast.isDay)
        assertEquals(true, result.forecast.mightRain)
        assertEquals("dallas", dao.saved?.cityQuery)
        assertEquals(1, api.calls)
    }

    @Test
    fun fallsBackToCacheWhenApiFails() = runTest {
        val dao = FakeForecastDao(
            CityForecast("dallas", "Dallas", true, false, savedAt = 0L)
        )
        val api = FakeWeatherApi(error = RuntimeException("timeout"))
        val repository = repository(
            api = api,
            dao = dao,
            cityLookup = { CityLocation("Dallas", 32.78, -96.80) }
        )

        val result = repository.getCityWeather("Dallas") as WeatherResult.Failure

        assertEquals("Dallas", result.cached?.cityName)
        assertTrue(result.error is AppError.Unexpected || result.error is AppError.NoInternet)
    }

    @Test
    fun returnsEmptyWhenCityMissingAndNoCache() = runTest {
        val result = repository(cityLookup = { null }).getCityWeather("Nowhere")
        assertEquals(WeatherResult.Empty, result)
    }

    private fun repository(
        api: FakeWeatherApi = FakeWeatherApi(),
        dao: FakeForecastDao = FakeForecastDao(),
        online: Boolean = true,
        now: Long = 1_000_000L,
        cityLookup: CityLookup
    ): WeatherRepositoryImpl {
        return WeatherRepositoryImpl(
            remoteDataSource = WeatherRemoteDataSource(api, ErrorMapper()),
            cityLookup = cityLookup,
            forecastDao = dao,
            networkChecker = NetworkChecker { online },
            dispatchers = dispatchers,
            errorMapper = ErrorMapper(),
            timeProvider = { now }
        )
    }

    private class FakeForecastDao(
        initial: CityForecast? = null
    ) : ForecastDao {
        var saved: CityForecast? = initial

        override suspend fun getByQuery(query: String): CityForecast? {
            return saved?.takeIf { it.cityQuery == query }
        }

        override suspend fun upsert(forecast: CityForecast) {
            saved = forecast
        }
    }

    private class FakeWeatherApi(
        private val forecast: ForecastResponse = ForecastResponse(CurrentWeather(isDay = 1)),
        private val error: Throwable? = null
    ) : WeatherApi {
        var calls = 0

        override suspend fun getForecast(
            latitude: Double,
            longitude: Double,
            hourly: String,
            current: String,
            timezone: String,
            forecastHours: Int
        ): ForecastResponse {
            calls += 1
            error?.let { throw it }
            return forecast
        }
    }
}
