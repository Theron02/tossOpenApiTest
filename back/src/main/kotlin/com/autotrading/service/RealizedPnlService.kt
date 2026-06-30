package com.autotrading.service

import com.autotrading.domain.type.OrderSide
import com.autotrading.repository.ExecutionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * 오늘(KST) 누적 실현손익 집계. 스키마 변경 없이 **체결 이력을 평균원가법으로 재생**해 계산한다.
 *
 * 종목별로 시간순 재생: BUY는 평균단가를 가중평균으로 갱신, SELL은 (체결가 - 평균단가)×수량을 실현.
 * 그 중 오늘(KST 00:00~24:00) 체결된 SELL의 실현분만 합산한다. 음수면 손실.
 *
 * 간이 구현(문서화): 매 호출마다 전체 이력 재생(O(n)). Paper/저빈도 단계엔 충분.
 * 추후 일별 실현손익 스냅샷/원장으로 정교화.
 */
@Service
class RealizedPnlService(
    private val executionRepository: ExecutionRepository,
) {
    private val seoul = ZoneId.of("Asia/Seoul")

    @Transactional(readOnly = true)
    fun todayRealizedPnl(accountId: UUID): Long {
        val dayStart = LocalDate.now(seoul).atStartOfDay(seoul).toInstant()
        val dayEnd = dayStart.plusSeconds(24 * 60 * 60)

        val executions = executionRepository.findByAccountIdOrderByExecutedAt(accountId)
        val avgCostByStock = HashMap<String, RunningCost>()
        var todayRealized = 0L

        for (e in executions) {
            val code = e.order.stock.code
            val rc = avgCostByStock.getOrPut(code) { RunningCost() }
            when (e.order.side) {
                OrderSide.BUY -> rc.addBuy(e.filledQty, e.filledPrice)
                OrderSide.SELL -> {
                    val realized = (e.filledPrice - rc.avgCost) * e.filledQty - e.fee
                    rc.reduce(e.filledQty)
                    if (e.executedAt >= dayStart && e.executedAt < dayEnd) todayRealized += realized
                }
            }
        }
        return todayRealized
    }

    /** 종목별 평균원가/수량 누적 상태. */
    private class RunningCost {
        var qty: Int = 0
        var avgCost: Long = 0

        fun addBuy(filledQty: Int, filledPrice: Long) {
            val newQty = qty + filledQty
            if (newQty > 0) avgCost = (avgCost * qty + filledPrice * filledQty) / newQty
            qty = newQty
        }

        fun reduce(filledQty: Int) {
            qty = (qty - filledQty).coerceAtLeast(0)
        }
    }
}
