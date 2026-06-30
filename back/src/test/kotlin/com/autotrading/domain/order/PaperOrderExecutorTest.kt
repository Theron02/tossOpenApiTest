package com.autotrading.domain.order

import com.autotrading.domain.risk.RejectReason
import com.autotrading.domain.risk.RiskManager
import com.autotrading.domain.risk.RiskRejectedException
import com.autotrading.domain.type.Market
import com.autotrading.domain.type.OrderSide
import com.autotrading.domain.type.OrderStatus
import com.autotrading.domain.type.OrderType
import com.autotrading.entity.Execution
import com.autotrading.entity.PaperAccount
import com.autotrading.entity.Position
import com.autotrading.entity.Stock
import com.autotrading.entity.TradeOrder
import com.autotrading.external.toss.TossMarketDataClient
import com.autotrading.external.toss.dto.PriceItem
import com.autotrading.repository.ExecutionRepository
import com.autotrading.repository.PaperAccountRepository
import com.autotrading.repository.PositionRepository
import com.autotrading.repository.RiskSettingRepository
import com.autotrading.repository.StockRepository
import com.autotrading.repository.TradeOrderRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * PaperOrderExecutor 단위 테스트. 저장소·시세·리스크는 MockK로 격리하고,
 * 가상 체결 후 잔고/포지션/주문상태가 정확한지 검증한다.
 */
class PaperOrderExecutorTest {

    private val accountRepository = mockk<PaperAccountRepository>(relaxed = true)
    private val stockRepository = mockk<StockRepository>(relaxed = true)
    private val riskSettingRepository = mockk<RiskSettingRepository>(relaxed = true)
    private val positionRepository = mockk<PositionRepository>(relaxed = true)
    private val orderRepository = mockk<TradeOrderRepository>(relaxed = true)
    private val executionRepository = mockk<ExecutionRepository>(relaxed = true)
    private val marketDataClient = mockk<TossMarketDataClient>()
    private val riskManager = mockk<RiskManager>(relaxed = true)

    private val executor = PaperOrderExecutor(
        accountRepository, stockRepository, riskSettingRepository,
        positionRepository, orderRepository, executionRepository,
        marketDataClient, riskManager,
    )

    private val accountId = UUID.randomUUID()
    private val code = "005930"
    private val stock = Stock(code = code, name = "삼성전자", market = Market.KOSPI)

    private fun stubCommonLookups(account: PaperAccount, position: Position? = null) {
        every { orderRepository.findByIdempotencyKey(any()) } returns null
        every { accountRepository.findById(accountId) } returns Optional.of(account)
        every { stockRepository.findById(code) } returns Optional.of(stock)
        every { riskSettingRepository.findByAccountId(accountId) } returns mockk(relaxed = true)
        every { positionRepository.findByAccountIdAndStockCode(accountId, code) } returns position
        // 제네릭 save(S):S 는 relaxed 기본값이 Object라 캐스팅 실패 → firstArg()로 명시 스텁.
        every { orderRepository.save(any()) } answers { firstArg() }
        every { positionRepository.save(any()) } answers { firstArg() }
        every { executionRepository.save(any()) } answers { firstArg() }
        every { accountRepository.save(any()) } answers { firstArg() }
    }

    private fun priceOf(value: Long) {
        every { marketDataClient.getPrices(listOf(code)) } returns
            listOf(PriceItem(symbol = code, lastPrice = value.toString(), currency = "KRW"))
    }

    private fun buyMarket(qty: Int) = OrderCommand(
        accountId = accountId, stockCode = code, side = OrderSide.BUY,
        orderType = OrderType.MARKET, quantity = qty, idempotencyKey = "key-1",
    )

    @Test
    fun `시장가 매수 - 전량 체결되고 잔고 차감, 신규 포지션 생성, 체결 기록`() {
        val account = PaperAccount(name = "t", cashBalance = 1_000_000, initialSeed = 1_000_000)
        stubCommonLookups(account, position = null)
        priceOf(70_000)

        val savedPosition = slot<Position>()
        val savedExecution = slot<Execution>()
        every { positionRepository.save(capture(savedPosition)) } answers { firstArg() }
        every { executionRepository.save(capture(savedExecution)) } answers { firstArg() }

        val order = executor.execute(buyMarket(10)) // 70,000 × 10 = 700,000

        assertEquals(OrderStatus.FILLED, order.status)
        assertEquals(10, order.filledQuantity)
        assertEquals(300_000, account.cashBalance) // 1,000,000 - 700,000
        assertEquals(10, savedPosition.captured.quantity)
        assertEquals(70_000, savedPosition.captured.avgPrice)
        assertEquals(70_000, savedExecution.captured.filledPrice)
        assertEquals(10, savedExecution.captured.filledQty)
        verify(exactly = 1) { riskManager.check(any()) }
        verify(exactly = 1) { accountRepository.save(account) }
    }

    @Test
    fun `시장가 매수 - 기존 포지션이 있으면 평단가를 가중평균으로 갱신`() {
        val account = PaperAccount(name = "t", cashBalance = 1_000_000, initialSeed = 1_000_000)
        val position = Position(account = account, stock = stock, quantity = 10, avgPrice = 60_000)
        stubCommonLookups(account, position = position)
        priceOf(80_000)

        executor.execute(buyMarket(10)) // (60,000×10 + 80,000×10)/20 = 70,000

        assertEquals(20, position.quantity)
        assertEquals(70_000, position.avgPrice)
        assertEquals(200_000, account.cashBalance) // 1,000,000 - 800,000
    }

    @Test
    fun `멱등 재요청 - 기존 주문을 반환하고 재실행하지 않는다`() {
        val existing = TradeOrder(
            account = PaperAccount(name = "t", cashBalance = 1, initialSeed = 1),
            stock = stock, side = OrderSide.BUY, orderType = OrderType.MARKET,
            quantity = 1, idempotencyKey = "key-1",
        )
        every { orderRepository.findByIdempotencyKey("key-1") } returns existing

        val result = executor.execute(buyMarket(1))

        assertEquals(existing, result)
        verify(exactly = 0) { riskManager.check(any()) }
        verify(exactly = 0) { marketDataClient.getPrices(any()) }
        verify(exactly = 0) { orderRepository.save(any()) }
    }

    @Test
    fun `리스크 거부 - 예외가 전파되고 주문이 저장되지 않는다`() {
        val account = PaperAccount(name = "t", cashBalance = 1_000_000, initialSeed = 1_000_000)
        stubCommonLookups(account)
        priceOf(70_000)
        every { riskManager.check(any()) } throws RiskRejectedException(RejectReason.KILL_SWITCH, "kill")

        assertFailsWith<RiskRejectedException> { executor.execute(buyMarket(10)) }
        verify(exactly = 0) { orderRepository.save(any()) }
        verify(exactly = 0) { accountRepository.save(any()) }
    }

    @Test
    fun `지정가 매수 - 현재가가 지정가보다 높으면 미체결로 PENDING 유지`() {
        val account = PaperAccount(name = "t", cashBalance = 1_000_000, initialSeed = 1_000_000)
        stubCommonLookups(account)
        priceOf(75_000) // 현재가 75,000 > 지정가 70,000 → 미체결

        val cmd = OrderCommand(
            accountId = accountId, stockCode = code, side = OrderSide.BUY,
            orderType = OrderType.LIMIT, quantity = 10, price = 70_000, idempotencyKey = "key-1",
        )
        val order = executor.execute(cmd)

        assertEquals(OrderStatus.PENDING, order.status)
        assertEquals(0, order.filledQuantity)
        assertEquals(1_000_000, account.cashBalance) // 잔고 변동 없음
        verify(exactly = 0) { executionRepository.save(any()) }
    }
}
