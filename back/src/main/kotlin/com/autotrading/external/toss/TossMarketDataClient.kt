package com.autotrading.external.toss

import com.autotrading.config.TossProperties
import com.autotrading.external.toss.dto.CandlesResponse
import com.autotrading.external.toss.dto.CandlesResult
import com.autotrading.external.toss.dto.PriceItem
import com.autotrading.external.toss.dto.PricesResponse
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/**
 * 토스 시세 조회. 현재가(`/prices`)와 캔들(`/candles`).
 * WebSocket 미지원이라 실시간 시세는 이 폴링으로 받는다(다건 조회로 호출 수 절감).
 */
@Component
class TossMarketDataClient(
    private val executor: TossApiExecutor,
    private val props: TossProperties,
) {
    /** 현재가 다건 조회. symbols 최대 200종목. */
    fun getPrices(symbols: List<String>): List<PriceItem> {
        require(symbols.isNotEmpty()) { "symbols는 비어 있을 수 없다" }
        require(symbols.size <= MAX_SYMBOLS) { "symbols는 최대 ${MAX_SYMBOLS}개" }
        val uri = UriComponentsBuilder.fromUriString(props.baseUrl)
            .path("/api/v1/prices")
            .queryParam("symbols", symbols.joinToString(","))
            .build().toUri()
        return executor.get(uri, object : ParameterizedTypeReference<PricesResponse>() {}).result
    }

    /**
     * 캔들 조회. [interval]은 토스 지원값 `1m`/`1d`. [count] 최대 200.
     * [before]는 페이지네이션 커서(이전 응답의 nextBefore).
     */
    fun getCandles(symbol: String, interval: String, count: Int, before: String? = null): CandlesResult {
        require(count in 1..MAX_CANDLE_COUNT) { "count는 1..${MAX_CANDLE_COUNT}" }
        val uri = UriComponentsBuilder.fromUriString(props.baseUrl)
            .path("/api/v1/candles")
            .queryParam("symbol", symbol)
            .queryParam("interval", interval)
            .queryParam("count", count)
            .apply { if (before != null) queryParam("before", before) }
            .build().toUri()
        return executor.get(uri, object : ParameterizedTypeReference<CandlesResponse>() {}).result
    }

    companion object {
        const val MAX_SYMBOLS = 200
        const val MAX_CANDLE_COUNT = 200
    }
}
