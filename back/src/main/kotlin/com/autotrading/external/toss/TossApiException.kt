package com.autotrading.external.toss

import org.springframework.http.HttpStatusCode

/**
 * 토스 API 호출 실패. 공통 error envelope(`error.code`)와 HTTP 상태를 담는다.
 * `code`는 flat string이며 unknown code도 그대로 보관한다(스펙: 클라이언트는 unknown code 허용).
 */
class TossApiException(
    val status: HttpStatusCode,
    val code: String,
    val requestId: String?,
    message: String?,
    /** 429 Retry-After(초). 없으면 null. */
    val retryAfterSeconds: Long? = null,
) : RuntimeException("toss api error [$status] code=$code requestId=$requestId: $message") {

    /** 만료 토큰. 재발급 후 1회 재시도 대상. */
    val isExpiredToken: Boolean get() = code == "expired-token" || code == "invalid-token"

    val isRateLimited: Boolean get() = status.value() == 429
}
