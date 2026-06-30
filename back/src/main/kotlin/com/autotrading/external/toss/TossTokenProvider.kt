package com.autotrading.external.toss

import com.autotrading.config.TossProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 토스 액세스 토큰 공급. 캐싱([TokenStore]) + 만료 전 사전 재발급 + 재발급 단일화.
 *
 * 토스 특성: client당 유효 토큰 1개, 재발급 시 이전 토큰 즉시 무효화, refresh token 없음.
 * → 재발급은 [lock]으로 단일화해 동시 재발급 경쟁(서로의 토큰 무효화)을 막는다.
 *
 * 단일 인스턴스 기준 인메모리 락. 다중 인스턴스 운영 시 분산락(Redis 등)으로 교체한다.
 */
@Component
class TossTokenProvider(
    private val authClient: TossAuthClient,
    private val tokenStore: TokenStore,
    private val props: TossProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val lock = ReentrantLock()

    /** 유효한 액세스 토큰을 반환한다. 없거나 만료 임박이면 재발급한다. */
    fun getValidToken(): String {
        tokenStore.get()?.let { if (!it.isNearExpiry()) return it.accessToken }
        return reissue()
    }

    /** 만료 토큰 감지 시 캐시를 비워 다음 호출에서 강제 재발급. */
    fun invalidate() = tokenStore.clear()

    private fun reissue(): String = lock.withLock {
        // 락 진입 후 재확인 — 다른 스레드가 이미 갱신했으면 그대로 사용(중복 발급 방지).
        tokenStore.get()?.let { if (!it.isNearExpiry()) return it.accessToken }

        val res = authClient.issueToken()
        val expiresAt = Instant.now().plusSeconds(res.expiresIn)
        tokenStore.save(CachedToken(res.accessToken, expiresAt))
        log.info("토스 토큰 재발급 완료. expiresIn={}s", res.expiresIn)
        res.accessToken
    }

    private fun CachedToken.isNearExpiry(): Boolean =
        Instant.now().isAfter(expiresAt.minusSeconds(props.tokenRefreshSkewSeconds))
}
