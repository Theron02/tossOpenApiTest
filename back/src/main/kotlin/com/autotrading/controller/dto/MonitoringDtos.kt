package com.autotrading.controller.dto

import com.autotrading.entity.Execution
import com.autotrading.entity.SignalLog
import com.autotrading.entity.TradeOrder
import java.time.Instant

/**
 * 모니터링(조회) 응답 DTO. **엔티티를 직접 노출하지 않는다.**
 * 금액은 정밀도 보존을 위해 문자열(원)로 직렬화한다(JS number 정밀도 손실 방지).
 */

// 포트폴리오 요약 (서비스에서 현재가로 평가 후 구성)
data class PortfolioResponse(
    val accountId: String,
    val name: String,
    val cash: String,
    val positionsValue: String,
    val totalEquity: String,
    val evalPnl: String,
    val returnRate: String,
    val initialSeed: String,
    /** 현재가로 평가됐는지(false면 평단가 폴백 — 토스 시세 미연동/실패). */
    val pricedAtMarket: Boolean,
)

// 보유 포지션 (서비스에서 현재가로 평가)
data class PositionResponse(
    val stockCode: String,
    val quantity: Int,
    val avgPrice: String,
    val currentPrice: String?,
    val evalAmount: String,
    val evalPnl: String,
    val pnlRate: String,
)

data class OrderResponse(
    val id: String,
    val stockCode: String,
    val side: String,
    val orderType: String,
    val quantity: Int,
    val price: String?,
    val status: String,
    val filledQuantity: Int,
    val createdAt: Instant?,
)

data class ExecutionResponse(
    val id: String,
    val orderId: String,
    val filledQty: Int,
    val filledPrice: String,
    val fee: String,
    val executedAt: Instant,
)

data class SignalResponse(
    val id: String,
    val strategyName: String,
    val stockCode: String,
    val signal: String,
    val indicatorSnapshot: Map<String, Any>,
    val createdAt: Instant?,
)

fun TradeOrder.toResponse() = OrderResponse(
    id = id.toString(),
    stockCode = stock.code,
    side = side.name,
    orderType = orderType.name,
    quantity = quantity,
    price = price?.toString(),
    status = status.name,
    filledQuantity = filledQuantity,
    createdAt = createdAt,
)

fun Execution.toResponse() = ExecutionResponse(
    id = id.toString(),
    orderId = order.id.toString(),
    filledQty = filledQty,
    filledPrice = filledPrice.toString(),
    fee = fee.toString(),
    executedAt = executedAt,
)

fun SignalLog.toResponse() = SignalResponse(
    id = id.toString(),
    strategyName = strategy.strategyName,
    stockCode = stockCode,
    signal = signal.name,
    indicatorSnapshot = indicatorSnapshot,
    createdAt = createdAt,
)
