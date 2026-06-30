package com.autotrading.repository

import com.autotrading.entity.Stock
import org.springframework.data.jpa.repository.JpaRepository

interface StockRepository : JpaRepository<Stock, String> {
    fun findByIsActiveTrue(): List<Stock>
}