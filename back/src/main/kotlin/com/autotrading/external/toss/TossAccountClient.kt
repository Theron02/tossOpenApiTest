package com.autotrading.external.toss

import com.autotrading.config.TossProperties
import com.autotrading.external.toss.dto.AccountItem
import com.autotrading.external.toss.dto.AccountsResponse
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/**
 * 토스 계좌 조회. `GET /api/v1/accounts`로 [AccountItem.accountSeq]를 얻어
 * 이후 계좌 컨텍스트 API(Asset/Order 등)의 `X-Tossinvest-Account` 헤더로 사용한다.
 */
@Component
class TossAccountClient(
    private val executor: TossApiExecutor,
    private val props: TossProperties,
) {
    fun getAccounts(): List<AccountItem> {
        val uri = UriComponentsBuilder.fromUriString(props.baseUrl)
            .path("/api/v1/accounts")
            .build().toUri()
        return executor.get(uri, object : ParameterizedTypeReference<AccountsResponse>() {}).result
    }
}
