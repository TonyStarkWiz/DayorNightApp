package com.aa.android.weather.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ForecastDao {

    @Query("SELECT * FROM forecasts WHERE cityQuery = :query LIMIT 1")
    suspend fun getByQuery(query: String): CityForecast?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(forecast: CityForecast)
}
