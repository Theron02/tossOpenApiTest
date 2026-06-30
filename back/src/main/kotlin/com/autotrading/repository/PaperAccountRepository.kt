package com.autotrading.repository

import com.autotrading.entity.PaperAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaperAccountRepository : JpaRepository<PaperAccount, UUID>
