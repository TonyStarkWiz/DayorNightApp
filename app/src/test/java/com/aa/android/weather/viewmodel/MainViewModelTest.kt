package com.aa.android.weather.viewmodel

import com.aa.android.weather.db.CityForecast
import com.aa.android.weather.error.AppError
import com.aa.android.weather.repository.WeatherRepository
import com.aa.android.weather.repository.WeatherResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun showsEmptyWhenCityIsBlank() = runTest {
        val viewModel = MainViewModel(FakeWeatherRepository())

        viewModel.onCityEntered("  ")

        val state = viewModel.uiState.value as DayNightUiState.Empty
        assertEquals("Please enter a city name", state.message)
    }

    @Test
    fun showsEmptyWhenCityIsNotFound() = runTest {
        val viewModel = MainViewModel(FakeWeatherRepository { WeatherResult.Empty })

        viewModel.onCityEntered("Nowhere")

        assertTrue(viewModel.uiState.value is DayNightUiState.Empty)
    }

    @Test
    fun showsSuccess() = runTest {
        val forecast = CityForecast("dallas", "Dallas", true, true, 1L)
        val viewModel = MainViewModel(
            FakeWeatherRepository { WeatherResult.Success(forecast, fromCache = false) }
        )

        viewModel.onCityEntered("Dallas")

        val state = viewModel.uiState.value as DayNightUiState.Success
        assertEquals("Dallas", state.forecast.cityName)
        assertEquals(false, state.fromCache)
    }

    @Test
    fun showsErrorWithCachedForecast() = runTest {
        val cached = CityForecast("dallas", "Dallas", false, false, 1L)
        val viewModel = MainViewModel(
            FakeWeatherRepository {
                WeatherResult.Failure(AppError.NoInternet, cached)
            }
        )

        viewModel.onCityEntered("Dallas")

        val state = viewModel.uiState.value as DayNightUiState.Error
        assertEquals(AppError.NoInternet, state.error)
        assertEquals("Dallas", state.cached?.cityName)
    }

    private class FakeWeatherRepository(
        private val onGet: suspend (String) -> WeatherResult = { WeatherResult.Empty }
    ) : WeatherRepository {
        override suspend fun getCityWeather(cityName: String): WeatherResult = onGet(cityName)
    }
}
