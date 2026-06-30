package com.autotrading.external.toss

import com.autotrading.domain.type.Currency
import com.autotrading.external.toss.dto.AccountsResponse
import com.autotrading.external.toss.dto.CandlesResponse
import com.autotrading.external.toss.dto.OAuth2TokenResponse
import com.autotrading.external.toss.dto.PricesResponse
import com.autotrading.external.toss.dto.TossErrorResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 토스 응답 DTO 파싱 검증. 핵심: 가격/수량 String → BigDecimal 정확 파싱, unknown enum fallback,
 * 알 수 없는 필드 무시(스펙 확장 허용). 외부 호출 없이 픽스처로 결정론적 검증.
 */
class TossDtoParsingTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `prices - KRW 정수와 USD 소수점을 BigDecimal로 파싱한다`() {
        val json = """
            {"result":[
              {"symbol":"005930","timestamp":"2024-01-02T05:00:00Z","lastPrice":"72000","currency":"KRW","unknownField":1},
              {"symbol":"AAPL","timestamp":null,"lastPrice":"185.70","currency":"USD"}
            ]}
        """.trimIndent()

        val res = mapper.readValue(json, PricesResponse::class.java)

        assertEquals(2, res.result.size)
        val kr = res.result[0]
        assertEquals(BigDecimal("72000"), kr.price)
        assertEquals(Currency.KRW, kr.currencyType)
        val us = res.result[1]
        assertEquals(BigDecimal("185.70"), us.price)
        assertEquals(Currency.USD, us.currencyType)
        assertNull(us.timestamp)
    }

    @Test
    fun `prices - 알 수 없는 currency는 UNKNOWN으로 fallback`() {
        val json = """{"result":[{"symbol":"X","lastPrice":"1","currency":"JPY"}]}"""
        val res = mapper.readValue(json, PricesResponse::class.java)
        assertEquals(Currency.UNKNOWN, res.result[0].currencyType)
    }

    @Test
    fun `candles - OHLCV를 BigDecimal로 파싱하고 nextBefore 커서를 읽는다`() {
        val json = """
            {"result":{"candles":[
              {"timestamp":"2024-01-02T00:00:00Z","openPrice":"71000","highPrice":"72500",
               "lowPrice":"70800","closePrice":"72000","volume":"12345678","currency":"KRW"}
            ],"nextBefore":"cursor-abc"}}
        """.trimIndent()

        val res = mapper.readValue(json, CandlesResponse::class.java)

        val c = res.result.candles.single()
        assertEquals(BigDecimal("71000"), c.open)
        assertEquals(BigDecimal("72500"), c.high)
        assertEquals(BigDecimal("70800"), c.low)
        assertEquals(BigDecimal("72000"), c.close)
        assertEquals(BigDecimal("12345678"), c.volumeValue)
        assertEquals("cursor-abc", res.result.nextBefore)
    }

    @Test
    fun `accounts - accountSeq를 int64로 파싱한다`() {
        val json = """{"result":[{"accountNo":"123-45-678","accountSeq":9007199254740993,"accountType":"BROKERAGE"}]}"""
        val res = mapper.readValue(json, AccountsResponse::class.java)
        assertEquals(9007199254740993L, res.result[0].accountSeq)
        assertEquals("BROKERAGE", res.result[0].accountType)
    }

    @Test
    fun `error envelope - code와 requestId를 읽는다`() {
        val json = """{"error":{"requestId":"req-1","code":"invalid-request","message":"bad","data":{"field":"symbols"}}}"""
        val res = mapper.readValue(json, TossErrorResponse::class.java)
        assertEquals("invalid-request", res.error.code)
        assertEquals("req-1", res.error.requestId)
    }

    @Test
    fun `oauth2 token - snake_case 필드를 매핑한다`() {
        val json = """{"access_token":"abc.def","token_type":"Bearer","expires_in":86400}"""
        val res = mapper.readValue(json, OAuth2TokenResponse::class.java)
        assertEquals("abc.def", res.accessToken)
        assertEquals(86400L, res.expiresIn)
        assertTrue(res.tokenType == "Bearer")
    }
}
