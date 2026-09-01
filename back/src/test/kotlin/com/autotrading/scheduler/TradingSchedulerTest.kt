package com.autotrading.scheduler

import com.autotrading.config.TradingProperties
import com.autotrading.domain.strategy.StrategyEngine
import com.autotrading.entity.PaperAccount
import com.autotrading.entity.StrategyConfig
import com.autotrading.repository.StrategyConfigRepository
import com.autotrading.service.CandleCollector
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TradingSchedulerTest {

    private val repo = mockk<StrategyConfigRepository>()
    private val collector = mockk<CandleCollector>(relaxed = true)
    private val engine = mockk<StrategyEngine>(relaxed = true)

    private fun scheduler(props: TradingProperties) =
        TradingScheduler(repo, collector, engine, props)

    private fun config(stock: String, interval: String? = null): StrategyConfig {
        val account = mockk<PaperAccount>()
        val params = if (interval == null) emptyMap() else mapOf("candleInterval" to interval)
        return StrategyConfig(account, "ML", stock, params)
    }

    @Test
    fun `enabled=false 면 아무것도 하지 않는다`() {
        scheduler(TradingProperties(enabled = false, ignoreMarketHours = true)).tick()
        verify(exactly = 0) { repo.findByEnabledTrue() }
        verify(exactly = 0) { engine.evaluateEnabled() }
    }

    @Test
    fun `활성 전략이 있으면 캔들 수집 후 엔진을 평가한다`() {
        every { repo.findByEnabledTrue() } returns listOf(config("005930", "MIN_1"))
        scheduler(TradingProperties(enabled = true, ignoreMarketHours = true)).tick()

        verify(exactly = 1) { collector.collect("005930", com.autotrading.domain.type.CandleInterval.MIN_1, any()) }
        verify(exactly = 1) { engine.evaluateEnabled() }
    }

    @Test
    fun `같은 종목-주기는 한 번만 수집한다`() {
        every { repo.findByEnabledTrue() } returns listOf(
            config("005930", "MIN_1"),
            config("005930", "MIN_1"), // 동일 (종목,주기)
        )
        scheduler(TradingProperties(enabled = true, ignoreMarketHours = true)).tick()
        verify(exactly = 1) { collector.collect("005930", any(), any()) }
    }

    @Test
    fun `한 종목 수집 실패가 엔진 평가를 막지 않는다`() {
        every { repo.findByEnabledTrue() } returns listOf(config("005930", "MIN_1"))
        every { collector.collect(any(), any(), any()) } throws RuntimeException("toss down")
        scheduler(TradingProperties(enabled = true, ignoreMarketHours = true)).tick()
        verify(exactly = 1) { engine.evaluateEnabled() } // 여전히 평가 수행
    }

    @Test
    fun `활성 전략이 없으면 엔진을 호출하지 않는다`() {
        every { repo.findByEnabledTrue() } returns emptyList()
        scheduler(TradingProperties(enabled = true, ignoreMarketHours = true)).tick()
        verify(exactly = 0) { engine.evaluateEnabled() }
    }

    @Test
    fun `장 운영시간 판정 - 평일 장중은 열림, 주말-장외는 닫힘`() {
        val kst = ZoneId.of("Asia/Seoul")
        // 2026-09-01 화요일 10:00 KST → 열림
        assertTrue(MarketHours.isOpen(ZonedDateTime.of(2026, 9, 1, 10, 0, 0, 0, kst)))
        // 같은 날 08:59 → 닫힘 / 15:31 → 닫힘
        assertFalse(MarketHours.isOpen(ZonedDateTime.of(2026, 9, 1, 8, 59, 0, 0, kst)))
        assertFalse(MarketHours.isOpen(ZonedDateTime.of(2026, 9, 1, 15, 31, 0, 0, kst)))
        // 2026-09-05 토요일 10:00 → 닫힘
        assertFalse(MarketHours.isOpen(ZonedDateTime.of(2026, 9, 5, 10, 0, 0, 0, kst)))
    }
}
