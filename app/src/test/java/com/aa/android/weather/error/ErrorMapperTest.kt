package com.aa.android.weather.error

import com.squareup.moshi.JsonDataException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.UnknownHostException

class ErrorMapperTest {

    private val mapper = ErrorMapper()

    @Test
    fun mapsUnknownHostToNoInternet() {
        assertEquals(AppError.NoInternet, mapper.map(UnknownHostException()))
    }

    @Test
    fun mapsUnauthorized() {
        assertEquals(AppError.Unauthorized, mapper.map(httpException(401)))
    }

    @Test
    fun mapsServerError() {
        val error = mapper.map(httpException(503))
        assertTrue(error is AppError.Server)
        assertEquals(503, (error as AppError.Server).code)
    }

    @Test
    fun mapsParsingError() {
        assertEquals(AppError.Parsing, mapper.map(JsonDataException("bad json")))
    }

    @Test
    fun mapsUnexpected() {
        assertEquals(AppError.Unexpected, mapper.map(IllegalStateException("boom")))
    }

    private fun httpException(code: Int): HttpException {
        val body = "{}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Unit>(code, body))
    }
}
