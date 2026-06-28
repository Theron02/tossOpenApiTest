package com.autotrading.entity

import com.autotrading.domain.type.Signal
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import java.util.UUID

/**
 * 신호 발생 기록. "왜 이 시점에 이 신호가 나왔는가"를 추적(백테스트 검증·디버깅).
 * 주문으로 이어지면 trade_order.signal_id가 이 로그를 참조한다.
 */
@Entity
@Table(name = "signal_log")
class SignalLog(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "strategy_id", nullable = false)
    val strategy: StrategyConfig,

    @Column(name = "stock_code", length = 6, nullable = false)
    val stockCode: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "signal", length = 8, nullable = false)
    val signal: Signal,

    /** 산출 시점 지표값 (예: {"ma5":71000,"ma20":70500,"rsi":68.2}). */
    @Type(JsonType::class)
    @Column(name = "indicator_snapshot", columnDefinition = "jsonb", nullable = false)
    val indicatorSnapshot: Map<String, Any>,

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),
) : BaseEntity()