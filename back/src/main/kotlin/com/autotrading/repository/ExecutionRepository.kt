package com.autotrading.repository

import com.autotrading.entity.Execution
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ExecutionRepository : JpaRepository<Execution, UUID> {
    fun findByOrderId(orderId: UUID): List<Execution>
}