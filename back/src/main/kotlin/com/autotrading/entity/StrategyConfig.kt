package com.autotrading.entity

import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Type
import java.util.UUID

/**
 * 전략 설정. [params]는 전략마다 구조가 달라 jsonb로 둔다(스키마 변경 없이 확장).
 * 엔진은 [enabled]=true인 설정만 평가한다.
 */
@Entity
@Table(
    name = "strategy_config",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_strategy_account_name_stock",
            columnNames = ["account_id", "strategy_name", "stock_code"],
        ),
    ],
)
class StrategyConfig(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    val account: PaperAccount,

    /** TradingStrategy 구현체 name (예: "GOLDEN_CROSS"). */
    @Column(name = "strategy_name", nullable = false)
    val strategyName: String,

    @Column(name = "stock_code", length = 6, nullable = false)
    val stockCode: String,

    /** 전략 파라미터 (예: {"shortPeriod":5,"longPeriod":20}). */
    @Type(JsonType::class)
    @Column(name = "params", columnDefinition = "jsonb", nullable = false)
    var params: Map<String, Any>,

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),
) : BaseEntity() {

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = false
        protected set

    fun enable() {
        enabled = true
    }

    fun disable() {
        enabled = false
    }
}