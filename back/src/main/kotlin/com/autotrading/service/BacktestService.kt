package com.autotrading.service

import com.autotrading.domain.backtest.BacktestEngine
import com.autotrading.domain.backtest.BacktestRequest
import com.autotrading.domain.backtest.BacktestResult
import com.autotrading.repository.CandleRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * `candle` 테이블(TASK_03 수집분)을 데이터 소스로 백테스트를 실행한다.
 * 백테스트는 **실주문과 완전히 격리**된 경로다(토스 주문 호출 없음).
 *
 * 한계(문서화): 날짜 범위 쿼리 대신 최근 [maxCandles]개를 오름차순으로 사용한다.
 * 특정 기간 백테스트가 필요하면 candle 적재 후 범위 쿼리를 추가한다(이번 범위 밖).
 */
@Service
class BacktestService(
    private val backtestEngine: BacktestEngine,
    private val candleRepository: CandleRepository,
) {
    @Transactional(readOnly = true)
    fun run(request: BacktestRequest, maxCandles: Int = DEFAULT_MAX_CANDLES): BacktestResult {
        val desc = candleRepository.findByStockCodeAndCandleIntervalOrderByCandleTimeDesc(
            request.stockCode, request.interval, PageRequest.of(0, maxCandles),
        )
        require(desc.size >= 2) { "백테스트할 캔들이 부족하다: ${desc.size}개 (stock=${request.stockCode}, interval=${request.interval})" }
        return backtestEngine.run(request, desc.asReversed()) // 오름차순으로
    }

    companion object {
        const val DEFAULT_MAX_CANDLES = 2000
    }
}
