package com.autotrading.scheduler

import com.autotrading.domain.type.Market
import com.autotrading.entity.PaperAccount
import com.autotrading.entity.RiskSetting
import com.autotrading.entity.Stock
import com.autotrading.entity.StrategyConfig
import com.autotrading.repository.PaperAccountRepository
import com.autotrading.repository.RiskSettingRepository
import com.autotrading.repository.StockRepository
import com.autotrading.repository.StrategyConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 로컬 테스트용 데모 데이터 시더. `trading.seed-demo=true` 일 때만 활성.
 *
 * 자동매매 루프가 실제로 신호·주문을 내려면 (Stock, PaperAccount, RiskSetting, 활성 StrategyConfig)가
 * 필요한데, 전략 생성 API가 없어 로컬에서 만들 방법이 없다. 이 시더가 최소 1세트를 **없을 때만** 만든다(멱등).
 *
 * ⚠️ 실제 DB에 행을 쓴다. 기본 꺼짐. 데모 전략은 GOLDEN_CROSS(외부 ML 서비스 불필요), 1분봉, 삼성전자(005930).
 */
@Component
@ConditionalOnProperty(prefix = "trading", name = ["seed-demo"], havingValue = "true")
class DemoDataSeeder(
    private val stockRepository: StockRepository,
    private val paperAccountRepository: PaperAccountRepository,
    private val riskSettingRepository: RiskSettingRepository,
    private val strategyConfigRepository: StrategyConfigRepository,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(vararg args: String?) {
        if (!stockRepository.existsById(STOCK_CODE)) {
            stockRepository.save(Stock(code = STOCK_CODE, name = "삼성전자", market = Market.KOSPI))
            log.info("[seed] stock {} 생성", STOCK_CODE)
        }

        val account = paperAccountRepository.findAll().firstOrNull()
            ?: paperAccountRepository.save(
                PaperAccount(name = "데모 모의계좌", cashBalance = SEED_CASH, initialSeed = SEED_CASH),
            ).also { log.info("[seed] paper account {} 생성", it.id) }

        if (riskSettingRepository.findByAccountId(account.id) == null) {
            riskSettingRepository.save(
                RiskSetting(account = account, dailyLossLimit = 1_000_000, maxPositionPct = 30),
            )
            log.info("[seed] risk setting 생성 account={}", account.id)
        }

        val exists = strategyConfigRepository.findByAccountId(account.id)
            .any { it.strategyName == "GOLDEN_CROSS" && it.stockCode == STOCK_CODE }
        if (!exists) {
            val config = StrategyConfig(
                account = account,
                strategyName = "GOLDEN_CROSS",
                stockCode = STOCK_CODE,
                params = mapOf(
                    "shortPeriod" to 5,
                    "longPeriod" to 20,
                    "candleInterval" to "MIN_1",
                    "orderQuantity" to 1,
                ),
            )
            config.enable()
            strategyConfigRepository.save(config)
            log.info("[seed] 활성 전략 GOLDEN_CROSS/{} 생성 (1분봉)", STOCK_CODE)
        }

        log.info("[seed] 데모 시드 완료. 루프가 활성 전략을 평가합니다.")
    }

    companion object {
        private const val STOCK_CODE = "005930"
        private const val SEED_CASH = 10_000_000L
    }
}
