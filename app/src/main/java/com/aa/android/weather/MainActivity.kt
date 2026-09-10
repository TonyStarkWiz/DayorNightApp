package com.aa.android.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aa.android.weather.db.CityForecast
import com.aa.android.weather.ui.theme.LibraryTheme
import com.aa.android.weather.viewmodel.DayNightUiState
import com.aa.android.weather.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            LibraryTheme {
                val uiState by mainViewModel.uiState.collectAsState()
                var searchTerm by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            modifier = Modifier.weight(1f),
                            value = searchTerm,
                            onValueChange = {
                                searchTerm = it
                            },
                            placeholder = { Text("Enter City Name") },
                        )
                        IconButton(
                            onClick = {
                                mainViewModel.onCityEntered(searchTerm)
                            },
                        ) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_menu_search),
                                contentDescription = "Search",
                            )
                        }
                    }

                    when (val state = uiState) {
                        DayNightUiState.Idle -> { }
                        DayNightUiState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                        }
                        is DayNightUiState.Empty -> {
                            Text(
                                text = state.message,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        is DayNightUiState.Success -> {
                            ForecastResult(
                                forecast = state.forecast,
                                fromCache = state.fromCache
                            )
                        }
                        is DayNightUiState.Error -> {
                            Text(
                                text = state.error.toMessage(),
                                color = MaterialTheme.colors.error,
                                modifier = Modifier.padding(16.dp)
                            )
                            state.cached?.let { cached ->
                                ForecastResult(forecast = cached, fromCache = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastResult(forecast: CityForecast, fromCache: Boolean) {
    Image(
        painter = painterResource(
            if (forecast.isDay) R.drawable.ic_sun else R.drawable.ic_moon
        ),
        contentDescription = if (forecast.isDay) "Sun" else "Moon",
        modifier = Modifier
            .padding(top = 32.dp)
            .size(140.dp)
    )
    Text(
        text = if (forecast.isDay) "Day" else "Night",
        style = MaterialTheme.typography.h4,
        modifier = Modifier.padding(top = 12.dp)
    )
    Text(
        text = forecast.cityName,
        style = MaterialTheme.typography.subtitle1,
        modifier = Modifier.padding(top = 4.dp)
    )
    if (fromCache) {
        Text(
            text = "Showing saved data",
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    if (forecast.mightRain) {
        Text(
            text = "It might rain in the next hour",
            style = MaterialTheme.typography.body1,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
