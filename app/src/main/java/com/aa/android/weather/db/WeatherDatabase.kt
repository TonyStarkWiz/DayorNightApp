package com.aa.android.weather.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CityForecast::class], version = 1, exportSchema = false)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun forecastDao(): ForecastDao
}
