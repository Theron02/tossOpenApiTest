package com.autotrading.external.toss

import com.autotrading.config.TossProperties
import com.autotrading.external.toss.dto.OAuth2ErrorResponse
import com.autotrading.external.toss.dto.OAuth2TokenResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * 토스 OAuth2 토큰 발급. `POST /oauth2/token` (Client Credentials).
 * 응답은 OAuth2 표준이라 공통 envelope이 아니다.
 */
@Component
class TossAuthClient(
    private val tossRestClient: RestClient,
    private val props: TossProperties,
    private val objectMapper: ObjectMapper,
) {
    fun issueToken(): OAuth2TokenResponse {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
            add("client_id", props.clientId)
            add("client_secret", props.clientSecret)
        }
        return tossRestClient.post()
            .uri("/oauth2/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .onStatus({ it.isError }) { _, res ->
                val err = runCatching {
                    objectMapper.readValue(res.body.readBytes(), OAuth2ErrorResponse::class.java)
                }.getOrNull()
                throw TossApiException(
                    status = res.statusCode,
                    code = err?.error ?: "token-issue-failed",
                    requestId = null,
                    message = err?.errorDescription,
                )
            }
            .body<OAuth2TokenResponse>()!!
    }
}
