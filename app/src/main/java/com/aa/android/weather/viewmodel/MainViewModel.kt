package com.aa.android.weather.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aa.android.weather.db.CityForecast
import com.aa.android.weather.error.AppError
import com.aa.android.weather.repository.WeatherRepository
import com.aa.android.weather.repository.WeatherResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DayNightUiState {
    object Idle : DayNightUiState()
    object Loading : DayNightUiState()
    data class Empty(val message: String) : DayNightUiState()
    data class Success(val forecast: CityForecast, val fromCache: Boolean) : DayNightUiState()
    data class Error(
        val error: AppError,
        val cached: CityForecast? // last Room row when the live call failed
    ) : DayNightUiState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DayNightUiState>(DayNightUiState.Idle)
    val uiState: StateFlow<DayNightUiState> = _uiState.asStateFlow()

    fun onCityEntered(cityName: String) {
        val query = cityName.trim()
        if (query.isEmpty()) {
            _uiState.value = DayNightUiState.Empty("Please enter a city name")
            return
        }

        viewModelScope.launch {
            _uiState.value = DayNightUiState.Loading
            try {
                _uiState.value = when (val result = weatherRepository.getCityWeather(query)) {
                    WeatherResult.Empty -> DayNightUiState.Empty("Couldn't find that city")
                    is WeatherResult.Success -> DayNightUiState.Success(result.forecast, result.fromCache)
                    is WeatherResult.Failure -> DayNightUiState.Error(result.error, result.cached)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "🧪 Unable to get the forecast", e)
                _uiState.value = DayNightUiState.Error(AppError.Unexpected, cached = null)
            }
        }
    }
}
