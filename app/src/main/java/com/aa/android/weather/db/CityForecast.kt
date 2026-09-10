package com.aa.android.weather.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forecasts")
data class CityForecast(
    @PrimaryKey val cityQuery: String, // lowercase search text so "Dallas" and "dallas" share a row
    val cityName: String,
    val isDay: Boolean,
    val mightRain: Boolean,
    val savedAt: Long
)
