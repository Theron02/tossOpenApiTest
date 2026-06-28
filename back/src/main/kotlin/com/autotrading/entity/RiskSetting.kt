package com.autotrading.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.util.UUID

/**
 * 리스크 설정 (계좌 1:1). RiskManager가 주문 직전 검사한다.
 * 리스크 파라미터를 계좌 본체에서 분리해 런타임 변경이 계좌에 영향 주지 않게 한다.
 */
@Entity
@Table(name = "risk_setting")
class RiskSetting(
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    val account: PaperAccount,

    /** 일일 누적 손실 한도 (원). 도달 시 신규 매수 차단. */
    @Column(name = "daily_loss_limit", nullable = false)
    var dailyLossLimit: Long,

    /** 종목당 최대 비중 (1~100%). */
    @Column(name = "max_position_pct", nullable = false)
    var maxPositionPct: Int,

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),
) : BaseEntity() {

    /** true면 모든 신규 주문 즉시 차단. */
    @Column(name = "kill_switch", nullable = false)
    var killSwitch: Boolean = false
        protected set

    init {
        require(maxPositionPct in 1..100) { "max_position_pct는 1..100 범위여야 한다: $maxPositionPct" }
        require(dailyLossLimit >= 0) { "daily_loss_limit는 음수일 수 없다: $dailyLossLimit" }
    }

    fun activateKillSwitch() {
        killSwitch = true
    }

    fun deactivateKillSwitch() {
        killSwitch = false
    }
}