package com.aa.android.weather.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastResponseTest {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(ForecastResponse::class.java)

    @Test
    fun parsesDaytimeAndNextHourRain() {
        val json = """
            {
              "current": { "is_day": 1 },
              "hourly": {
                "time": ["2026-09-02T16:00", "2026-09-02T17:00"],
                "rain": [0.0, 1.2]
              }
            }
        """.trimIndent()

        val forecast = adapter.fromJson(json)!!

        assertEquals(1, forecast.current.isDay)
        assertTrue((forecast.hourly?.rain?.getOrNull(1) ?: 0.0) > 0.0)
    }

    @Test
    fun parsesNightWithNoRain() {
        val json = """
            {
              "current": { "is_day": 0 },
              "hourly": {
                "time": ["2026-09-02T22:00", "2026-09-02T23:00"],
                "rain": [0.0, 0.0]
              }
            }
        """.trimIndent()

        val forecast = adapter.fromJson(json)!!

        assertEquals(0, forecast.current.isDay)
        assertEquals(0.0, forecast.hourly?.rain?.getOrNull(1) ?: 0.0, 0.0)
    }
}
