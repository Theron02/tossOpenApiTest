package com.autotrading.external.toss.dto

import com.autotrading.domain.type.Currency
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

/**
 * 토스 Open API DTO. 스펙(v1.1.5) 기준.
 *
 * 설계 원칙:
 * - 가격/수량은 토스가 **문자열(decimal)**로 준다. DTO는 String 그대로 받고,
 *   도메인 변환은 [BigDecimal] 파싱으로 한다(Double 금지).
 * - timestamp는 포맷이 스펙에 명시되지 않아 raw String으로 받고 상위에서 방어적으로 파싱.
 * - 모든 DTO에 `@JsonIgnoreProperties(ignoreUnknown = true)` — 스펙 확장/unknown 필드 허용.
 */

// ── 인증 (OAuth2 표준, 공통 envelope 예외) ─────────────────────────────
@JsonIgnoreProperties(ignoreUnknown = true)
data class OAuth2TokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("token_type") val tokenType: String,
    @JsonProperty("expires_in") val expiresIn: Long,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OAuth2ErrorResponse(
    val error: String,
    @JsonProperty("error_description") val errorDescription: String? = null,
)

// ── 공통 실패 envelope ────────────────────────────────────────────────
@JsonIgnoreProperties(ignoreUnknown = true)
data class TossErrorResponse(val error: TossError)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TossError(
    val requestId: String? = null,
    val code: String,
    val message: String? = null,
    val data: Map<String, Any?>? = null,
)

// ── 현재가 GET /api/v1/prices ────────────────────────────────────────
@JsonIgnoreProperties(ignoreUnknown = true)
data class PricesResponse(val result: List<PriceItem> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class PriceItem(
    val symbol: String,
    val timestamp: String? = null,
    val lastPrice: String,
    val currency: String? = null,
) {
    /** 현재가. 문자열 → BigDecimal. */
    val price: BigDecimal get() = BigDecimal(lastPrice)
    val currencyType: Currency get() = Currency.from(currency)
}

// ── 계좌 GET /api/v1/accounts ────────────────────────────────────────
@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountsResponse(val result: List<AccountItem> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountItem(
    val accountNo: String,
    val accountSeq: Long,
    val accountType: String? = null,
)

// ── 캔들 GET /api/v1/candles ─────────────────────────────────────────
@JsonIgnoreProperties(ignoreUnknown = true)
data class CandlesResponse(val result: CandlesResult)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CandlesResult(
    val candles: List<CandleItem> = emptyList(),
    /** 페이지네이션 커서. 다음 페이지 조회 시 before 파라미터로 사용. */
    val nextBefore: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CandleItem(
    val timestamp: String,
    val openPrice: String,
    val highPrice: String,
    val lowPrice: String,
    val closePrice: String,
    val volume: String,
    val currency: String? = null,
) {
    val open: BigDecimal get() = BigDecimal(openPrice)
    val high: BigDecimal get() = BigDecimal(highPrice)
    val low: BigDecimal get() = BigDecimal(lowPrice)
    val close: BigDecimal get() = BigDecimal(closePrice)
    val volumeValue: BigDecimal get() = BigDecimal(volume)
    val currencyType: Currency get() = Currency.from(currency)
}
