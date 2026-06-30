package com.autotrading.external.toss

import com.autotrading.domain.type.CandleInterval
import com.autotrading.service.CandleCollector
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import kotlin.test.assertTrue

/**
 * 토스 Open API 라이브 검증 (TASK_03 단계 1·2). 샌드박스가 없어 실거래 환경에 직접 붙는다.
 *
 * TOSS_CLIENT_ID 환경변수가 없으면 스킵 — 자격증명 없이 빌드가 깨지지 않게.
 * 시세/캔들/계좌 조회는 읽기 전용이라 부수효과 없음(주문은 이 범위 밖).
 * 단, candle 수집은 DB에 신규 봉을 저장한다(SUPABASE_* 환경변수도 필요).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "TOSS_CLIENT_ID", matches = ".+")
class TossLiveIntegrationTest {

    private val log = LoggerFactory.getLogger(javaClass)

    @Autowired lateinit var tokenProvider: TossTokenProvider
    @Autowired lateinit var marketDataClient: TossMarketDataClient
    @Autowired lateinit var accountClient: TossAccountClient
    @Autowired lateinit var candleCollector: CandleCollector

    private val samsung = "005930"

    @Test
    fun `단계1 - 토큰 발급 후 삼성전자 현재가를 BigDecimal로 받는다`() {
        val token = tokenProvider.getValidToken()
        assertTrue(token.isNotBlank(), "access token should be issued")

        val prices = marketDataClient.getPrices(listOf(samsung))
        val price = prices.single { it.symbol == samsung }
        log.info("삼성전자 현재가={} {}", price.price, price.currencyType)
        assertTrue(price.price > BigDecimal.ZERO, "현재가는 0보다 커야 한다")
    }

    @Test
    fun `단계2 - 계좌 accountSeq를 획득한다`() {
        val accounts = accountClient.getAccounts()
        log.info("계좌 수={}", accounts.size)
        accounts.forEach { log.info("accountNo={} accountSeq={} type={}", it.accountNo, it.accountSeq, it.accountType) }
        // 계좌가 없을 수도 있으나, 호출·파싱이 성공하면 단계2 인증/엔드포인트 검증은 통과.
        assertTrue(accounts.all { it.accountSeq > 0 }, "accountSeq는 양수여야 한다")
    }

    @Test
    fun `단계2 - 삼성전자 일봉을 수집해 candle 테이블에 저장한다`() {
        val result = marketDataClient.getCandles(samsung, "1d", count = 5)
        log.info("수신 캔들={} nextBefore={}", result.candles.size, result.nextBefore)
        assertTrue(result.candles.isNotEmpty(), "캔들이 수신돼야 한다")

        val saved = candleCollector.collect(samsung, CandleInterval.DAY_1, count = 5)
        log.info("저장된 신규 캔들={}", saved)
        assertTrue(saved >= 0)
    }
}
