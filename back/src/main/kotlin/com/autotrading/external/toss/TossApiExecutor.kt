package com.autotrading.external.toss

import com.autotrading.external.toss.dto.TossErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI

/**
 * 인증이 필요한 토스 GET 호출의 공통 실행기.
 *
 * 책임:
 * - `Authorization: Bearer` 주입 (+ 계좌 컨텍스트 API의 `X-Tossinvest-Account`)
 * - 공통 error envelope → [TossApiException] 변환 (requestId 로깅)
 * - 만료 토큰(401/expired-token) 시 재발급 후 **1회 재시도**
 * - 429 시 `Retry-After` 만큼 대기 후 재시도(최대 [MAX_RATE_LIMIT_RETRY]회)
 */
@Component
class TossApiExecutor(
    private val tossRestClient: RestClient,
    private val tokenProvider: TossTokenProvider,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun <T> get(uri: URI, responseType: ParameterizedTypeReference<T>, accountSeq: Long? = null): T {
        var triedExpired = false
        var rateLimitRetries = 0
        while (true) {
            try {
                return execute(uri, responseType, accountSeq)
            } catch (e: TossApiException) {
                when {
                    e.isExpiredToken && !triedExpired -> {
                        triedExpired = true
                        tokenProvider.invalidate()
                        log.warn("만료 토큰 감지 → 재발급 후 재시도. uri={}", uri)
                    }
                    e.isRateLimited && rateLimitRetries < MAX_RATE_LIMIT_RETRY -> {
                        rateLimitRetries++
                        val waitMs = (e.retryAfterSeconds ?: 1) * 1000
                        log.warn("429 rate limited → {}ms 후 재시도({}/{}). uri={}", waitMs, rateLimitRetries, MAX_RATE_LIMIT_RETRY, uri)
                        Thread.sleep(waitMs)
                    }
                    else -> throw e
                }
            }
        }
    }

    private fun <T> execute(uri: URI, responseType: ParameterizedTypeReference<T>, accountSeq: Long?): T =
        tossRestClient.get()
            .uri(uri)
            .headers { h ->
                h.setBearerAuth(tokenProvider.getValidToken())
                if (accountSeq != null) h.set(HEADER_ACCOUNT, accountSeq.toString())
            }
            .retrieve()
            .onStatus({ it.isError }) { _, res ->
                val retryAfter = res.headers.getFirst(HttpHeaders.RETRY_AFTER)?.toLongOrNull()
                val err = runCatching {
                    objectMapper.readValue(res.body.readBytes(), TossErrorResponse::class.java).error
                }.getOrNull()
                log.warn("토스 API 실패 status={} code={} requestId={}", res.statusCode, err?.code, err?.requestId)
                throw TossApiException(
                    status = res.statusCode,
                    code = err?.code ?: "unknown-error",
                    requestId = err?.requestId,
                    message = err?.message,
                    retryAfterSeconds = retryAfter,
                )
            }
            .body(responseType)!!

    companion object {
        const val HEADER_ACCOUNT = "X-Tossinvest-Account"
        const val MAX_RATE_LIMIT_RETRY = 2
    }
}
