package com.autotrading.external.toss

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** 캐싱된 토스 액세스 토큰. */
data class CachedToken(val accessToken: String, val expiresAt: Instant)

/**
 * 토큰 저장소 추상화. 지금은 인메모리([InMemoryTokenStore]),
 * 추후 다중 인스턴스 운영 시 RedisTokenStore로 교체한다(인터페이스 유지).
 */
interface TokenStore {
    fun get(): CachedToken?
    fun save(token: CachedToken)
    fun clear()
}

@Component
class InMemoryTokenStore : TokenStore {
    private val ref = AtomicReference<CachedToken?>(null)
    override fun get(): CachedToken? = ref.get()
    override fun save(token: CachedToken) = ref.set(token)
    override fun clear() = ref.set(null)
}
