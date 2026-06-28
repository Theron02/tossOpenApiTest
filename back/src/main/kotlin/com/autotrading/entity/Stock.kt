package com.autotrading.entity

import com.autotrading.domain.type.Market
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 종목 마스터. [code]는 KIS API 종목 식별자와 일치하는 자연키. */
@Entity
@Table(name = "stock")
class Stock(
    @Id
    @Column(name = "code", length = 6)
    val code: String,

    @Column(name = "name", nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "market", length = 16, nullable = false)
    val market: Market,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : BaseEntity()