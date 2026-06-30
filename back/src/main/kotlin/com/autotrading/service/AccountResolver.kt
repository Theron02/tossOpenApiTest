package com.autotrading.service

import com.autotrading.common.ConflictException
import com.autotrading.common.ResourceNotFoundException
import com.autotrading.entity.PaperAccount
import com.autotrading.repository.PaperAccountRepository
import org.springframework.stereotype.Component

/**
 * 단일 운영자 시스템의 운영 계정을 해석한다(경로에 accountId 없음).
 * 시스템에 PaperAccount가 정확히 하나일 때 그것을 사용한다. 없으면 404, 여러 개면 409.
 * (멀티계정 전환 시 accountId 파라미터 경로로 대체)
 */
@Component
class AccountResolver(private val accountRepository: PaperAccountRepository) {

    fun resolve(): PaperAccount {
        val accounts = accountRepository.findAll()
        return when {
            accounts.isEmpty() -> throw ResourceNotFoundException("운영 계정(PaperAccount)이 없습니다")
            accounts.size > 1 -> throw ConflictException("운영 계정이 여러 개입니다(${accounts.size}). 단일 계정만 지원합니다")
            else -> accounts.first()
        }
    }
}
