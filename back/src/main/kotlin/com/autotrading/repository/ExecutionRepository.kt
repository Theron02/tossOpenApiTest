package com.autotrading.repository

import com.autotrading.entity.Execution
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ExecutionRepository : JpaRepository<Execution, UUID> {
    fun findByOrderId(orderId: UUID): List<Execution>

    /**
     * 한 계좌의 모든 체결을 시간순으로 조회한다. 실현손익 재생(평균원가법)용.
     * order·stock을 fetch join 해 N+1을 피한다.
     */
    @Query(
        "select e from Execution e join fetch e.order o join fetch o.stock " +
            "where o.account.id = :accountId order by e.executedAt asc",
    )
    fun findByAccountIdOrderByExecutedAt(@Param("accountId") accountId: UUID): List<Execution>
}