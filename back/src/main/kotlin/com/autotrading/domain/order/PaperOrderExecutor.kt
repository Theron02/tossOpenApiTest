package com.autotrading.domain.order

import com.autotrading.domain.risk.RiskCheckContext
import com.autotrading.domain.risk.RiskDecision
import com.autotrading.domain.risk.RiskManager
import com.autotrading.domain.risk.RiskRejectedException
import com.autotrading.domain.type.OrderSide
import com.autotrading.domain.type.OrderStatus
import com.autotrading.domain.type.OrderType
import com.autotrading.entity.Execution
import com.autotrading.entity.PaperAccount
import com.autotrading.entity.Position
import com.autotrading.entity.Stock
import com.autotrading.entity.TradeOrder
import com.autotrading.external.toss.TossMarketDataClient
import com.autotrading.repository.ExecutionRepository
import com.autotrading.repository.PaperAccountRepository
import com.autotrading.repository.PositionRepository
import com.autotrading.repository.RiskSettingRepository
import com.autotrading.repository.StockRepository
import com.autotrading.repository.TradeOrderRepository
import com.autotrading.service.RealizedPnlService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 모의(Paper) 주문 실행기. 토스로 주문을 전송하지 않고, 토스 **현재가로 가상 체결**한 뒤
 * `trade_order`/`execution`/`position`/`paper_account` 잔고를 기록한다.
 *
 * 흐름: 멱등성 확인 → 현재가 조회 → **RiskManager 통과** → 주문 생성 → 가상 체결 → 잔고·포지션 갱신.
 * 잔고 차감 + 주문/체결 기록 + 포지션 갱신은 하나의 트랜잭션으로 원자화한다.
 *
 * 단순화(문서화): 현재가 조회(외부 호출)를 트랜잭션 안에서 수행한다. Paper/저빈도 단계에선 무해하며,
 * 실거래/고빈도 전환 시 외부 호출을 트랜잭션 밖으로 분리한다(TODO). 수수료는 0으로 둔다(TODO: 수수료율).
 */
@Component
class PaperOrderExecutor(
    private val accountRepository: PaperAccountRepository,
    private val stockRepository: StockRepository,
    private val riskSettingRepository: RiskSettingRepository,
    private val positionRepository: PositionRepository,
    private val orderRepository: TradeOrderRepository,
    private val executionRepository: ExecutionRepository,
    private val marketDataClient: TossMarketDataClient,
    private val riskManager: RiskManager,
    private val realizedPnlService: RealizedPnlService,
) : OrderExecutor {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun execute(command: OrderCommand): TradeOrder {
        // 멱등성: 같은 키의 주문이 이미 있으면 그대로 반환(재실행 금지).
        orderRepository.findByIdempotencyKey(command.idempotencyKey)?.let {
            log.info("멱등 재요청 — 기존 주문 반환. key={}", command.idempotencyKey)
            return it
        }

        val account = accountRepository.findById(command.accountId)
            .orElseThrow { IllegalArgumentException("계좌 없음: ${command.accountId}") }
        val stock = stockRepository.findById(command.stockCode)
            .orElseThrow { IllegalArgumentException("종목 없음: ${command.stockCode}") }
        val riskSetting = riskSettingRepository.findByAccountId(command.accountId)
        val position = positionRepository.findByAccountIdAndStockCode(command.accountId, command.stockCode)

        val referencePrice = currentPrice(command.stockCode)
        val checkPrice = command.price ?: referencePrice

        // 모든 주문은 RiskManager를 경유한다. 차단 시 RiskRejectedException으로 실행을 중단한다.
        val decision = riskManager.check(
            RiskCheckContext(
                account = account,
                riskSetting = riskSetting,
                side = command.side,
                quantity = command.quantity,
                price = checkPrice,
                existingPosition = position,
                todayRealizedPnl = realizedPnlService.todayRealizedPnl(command.accountId),
                hasOpenOrderSameSide = hasOpenOrderSameSide(command),
            ),
        )
        if (decision is RiskDecision.Rejected) {
            throw RiskRejectedException(decision.reason, decision.detail)
        }

        val order = TradeOrder(
            account = account,
            stock = stock,
            side = command.side,
            orderType = command.orderType,
            quantity = command.quantity,
            price = command.price,
            idempotencyKey = command.idempotencyKey,
            signalId = command.signalId,
        )

        val fillPrice = resolveFillPrice(command, referencePrice)
        if (fillPrice == null) {
            // LIMIT 미체결: PENDING으로 둔다(폴링/재평가 시 체결 시도는 이후 작업).
            orderRepository.save(order)
            log.info("주문 접수(미체결) id={} {} {} qty={} limit={} 현재가={}", order.id, command.side, command.stockCode, command.quantity, command.price, referencePrice)
            return order
        }

        order.applyFill(command.quantity) // 전량 체결 → FILLED
        orderRepository.save(order)
        executionRepository.save(
            Execution(order = order, filledQty = command.quantity, filledPrice = fillPrice, fee = FEE, executedAt = Instant.now()),
        )
        settle(account, stock, position, command, fillPrice)

        log.info("가상 체결 id={} {} {} qty={} price={} balance={}", order.id, command.side, command.stockCode, command.quantity, fillPrice, account.cashBalance)
        return order
    }

    /** 동일 계좌·종목·방향의 미체결(PENDING/PARTIAL) 주문이 있는지 — 중복 주문 가드용. */
    private fun hasOpenOrderSameSide(command: OrderCommand): Boolean =
        orderRepository
            .findByStockCodeAndStatusIn(command.stockCode, listOf(OrderStatus.PENDING, OrderStatus.PARTIAL))
            .any { it.account.id == command.accountId && it.side == command.side }

    /** 현재가(원). 미국 소수점 종목은 이번 범위 밖(KR 정수 기준). */
    private fun currentPrice(stockCode: String): Long =
        marketDataClient.getPrices(listOf(stockCode))
            .firstOrNull { it.symbol == stockCode }
            ?.price?.toLong()
            ?: throw IllegalStateException("현재가 조회 실패: $stockCode")

    /**
     * 체결가를 결정한다(체결 불가면 null).
     * - MARKET: 현재가로 전량 체결.
     * - LIMIT: 마케터블일 때만 현재가로 체결 (BUY는 현재가<=지정가, SELL은 현재가>=지정가).
     */
    private fun resolveFillPrice(command: OrderCommand, referencePrice: Long): Long? {
        when (command.orderType) {
            OrderType.MARKET -> return referencePrice
            OrderType.LIMIT -> {
                val limit = command.price ?: return null
                val marketable = when (command.side) {
                    OrderSide.BUY -> referencePrice <= limit
                    OrderSide.SELL -> referencePrice >= limit
                }
                return if (marketable) referencePrice else null
            }
        }
    }

    /** 체결 결과를 잔고·포지션에 반영한다. */
    private fun settle(account: PaperAccount, stock: Stock, position: Position?, command: OrderCommand, fillPrice: Long) {
        val amount = fillPrice * command.quantity
        when (command.side) {
            OrderSide.BUY -> {
                account.withdraw(amount + FEE)
                if (position == null) {
                    positionRepository.save(Position(account = account, stock = stock, quantity = command.quantity, avgPrice = fillPrice))
                } else {
                    position.addBuy(command.quantity, fillPrice)
                    positionRepository.save(position)
                }
            }
            OrderSide.SELL -> {
                // RiskManager가 수량 부족을 막으므로 position은 non-null이고 수량 충분.
                requireNotNull(position) { "포지션 없이 매도 도달 — 리스크 가드 우회 의심" }
                position.reduceSell(command.quantity)
                positionRepository.save(position)
                account.deposit(amount - FEE)
            }
        }
        accountRepository.save(account)
    }

    companion object {
        /** Paper 단계 수수료(원). TODO: 시장·통화별 수수료율 적용. */
        private const val FEE = 0L
    }
}
