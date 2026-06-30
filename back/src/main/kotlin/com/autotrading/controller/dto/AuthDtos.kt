package com.autotrading.controller.dto

import jakarta.validation.constraints.NotBlank

/** 로그인 요청. */
data class LoginRequest(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String,
)

/** 발급된 JWT. expiresInSeconds 이후 재로그인 필요. */
data class TokenResponse(
    val token: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
)
