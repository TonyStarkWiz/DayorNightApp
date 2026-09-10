package com.aa.android.weather.error

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMapper {

    fun map(throwable: Throwable): AppError {
        return when (throwable) {
            is AppException -> throwable.error
            is UnknownHostException, is SocketTimeoutException -> AppError.NoInternet
            is JsonDataException, is JsonEncodingException -> AppError.Parsing
            is HttpException -> {
                when (throwable.code()) {
                    401, 403 -> AppError.Unauthorized // Open-Meteo rarely sends these; still mapped so the UI can say "unauthorized"
                    else -> AppError.Server(throwable.code())
                }
            }
            is IOException -> AppError.NoInternet
            else -> AppError.Unexpected
        }
    }
}
