package com.aa.android.weather.error

sealed class AppError {
    object NoInternet : AppError()
    data class Server(val code: Int) : AppError()
    object Unauthorized : AppError()
    object Parsing : AppError()
    object Unexpected : AppError()

    fun toMessage(): String {
        return when (this) {
            NoInternet -> "No internet connection"
            is Server -> "Server error ($code)"
            Unauthorized -> "Unauthorized request"
            Parsing -> "Couldn't read the forecast"
            Unexpected -> "Something went wrong"
        }
    }
}

class AppException(
    val error: AppError,
    cause: Throwable? = null
) : Exception(error.toMessage(), cause)
