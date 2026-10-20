package com.orbit.core.domain.model

enum class OrbitErrorCode {
    IO_ERROR,
    STORAGE_ERROR,
    VALIDATION_ERROR,
    CHECKSUM_MISMATCH,
    UNSUPPORTED_SCHEMA_VERSION,
    ENCRYPTION_ERROR,
    DECRYPTION_ERROR,
    PERMISSION_DENIED,
    NOT_FOUND,
    UNKNOWN_ERROR
}

sealed interface OrbitResult<out T> {
    data class Success<out T>(val data: T) : OrbitResult<T>
    data class Error(
        val code: OrbitErrorCode,
        val message: String,
        val cause: Throwable? = null
    ) : OrbitResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isError: Boolean
        get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw IllegalStateException("OrbitResult was Error: $code: $message", cause)
    }
}
