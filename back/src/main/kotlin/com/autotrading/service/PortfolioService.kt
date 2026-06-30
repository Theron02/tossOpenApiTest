package com.autotrading.service

import com.autotrading.controller.dto.PortfolioResponse
import com.autotrading.controller.dto.PositionResponse
import com.autotrading.external.toss.TossMarketDataClient
import com.autotrading.repository.PositionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 포트폴리오·포지션 평가. 보유 종목은 토스 현재가로 평가하고, 시세 조회 실패 시 평단가로 폴백한다
 * (개발/오프라인에서도 동작). 금액은 원 단위 [Long], 비율은 [BigDecimal].
 */
@Service
class PortfolioService(
    private val accountResolver: AccountResolver,
    private val positionRepository: PositionRepository,
    private val marketDataClient: TossMarketDataClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun portfolio(): PortfolioResponse {
        val account = accountResolver.resolve()
        val positions = positionRepository.findByAccountId(account.id)
        val (prices, priced) = currentPrices(positions.map { it.stock.code })

        var positionsValue = 0L
        var cost = 0L
        positions.forEach { p ->
            val cur = prices[p.stock.code] ?: p.avgPrice
            positionsValue += cur * p.quantity
            cost += p.avgPrice * p.quantity
        }
        val cash = account.cashBalance
        val totalEquity = cash + positionsValue
        val evalPnl = positionsValue - cost
        val returnRate = if (account.initialSeed > 0) {
            BigDecimal(totalEquity - account.initialSeed).divide(BigDecimal(account.initialSeed), 6, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return PortfolioResponse(
            accountId = account.id.toString(),
            name = account.name,
            cash = cash.toString(),
            positionsValue = positionsValue.toString(),
            totalEquity = totalEquity.toString(),
            evalPnl = evalPnl.toString(),
            returnRate = returnRate.toPlainString(),
            initialSeed = account.initialSeed.toString(),
            pricedAtMarket = priced,
        )
    }

    @Transactional(readOnly = true)
    fun positions(): List<PositionResponse> {
        val account = accountResolver.resolve()
        val positions = positionRepository.findByAccountId(account.id)
        val (prices, _) = currentPrices(positions.map { it.stock.code })
        return positions.map { p ->
            val cur = prices[p.stock.code]
            val priceForEval = cur ?: p.avgPrice
            val evalAmount = priceForEval * p.quantity
            val evalPnl = (priceForEval - p.avgPrice) * p.quantity
            val pnlRate = if (p.avgPrice > 0) {
                BigDecimal(priceForEval - p.avgPrice).divide(BigDecimal(p.avgPrice), 6, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO
            PositionResponse(
                stockCode = p.stock.code,
                quantity = p.quantity,
                avgPrice = p.avgPrice.toString(),
                currentPrice = cur?.toString(),
                evalAmount = evalAmount.toString(),
                evalPnl = evalPnl.toString(),
                pnlRate = pnlRate.toPlainString(),
            )
        }
    }

    /** 현재가 맵(원). 두 번째 값은 시세 조회 성공 여부. 실패 시 빈 맵 + false. */
    private fun currentPrices(codes: List<String>): Pair<Map<String, Long>, Boolean> {
        if (codes.isEmpty()) return emptyMap<String, Long>() to true
        return try {
            val map = marketDataClient.getPrices(codes.distinct())
                .associate { it.symbol to it.price.toLong() }
            map to true
        } catch (e: Exception) {
            log.warn("현재가 조회 실패 — 평단가로 폴백. codes={} err={}", codes, e.message)
            emptyMap<String, Long>() to false
        }
    }
}
