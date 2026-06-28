package com.autotrading.entity

import com.autotrading.domain.type.Market

/** 테스트용 엔티티 생성 헬퍼. */
object Fixtures {
    fun account(cashBalance: Long = 10_000_000, initialSeed: Long = 10_000_000) =
        PaperAccount(name = "test", cashBalance = cashBalance, initialSeed = initialSeed)

    fun stock(code: String = "005930") =
        Stock(code = code, name = "삼성전자", market = Market.KOSPI)
}