package com.autotrading.common

import java.time.Instant

/** API 에러 본문. [code]는 flat string(예: "bad-request", "not-found"). */
data class ApiError(val code: String, val message: String)

/**
 * 공통 응답 래퍼. 성공이면 [data], 실패면 [error]. [timestamp]는 응답 생성 시각(UTC).
 * 둘 중 하나만 채워진다.
 */
data class ApiResponse<T>(
    val data: T? = null,
    val error: ApiError? = null,
    val timestamp: Instant = Instant.now(),
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(data = data)
        fun fail(code: String, message: String): ApiResponse<Nothing> =
            ApiResponse(error = ApiError(code, message))
    }
}
