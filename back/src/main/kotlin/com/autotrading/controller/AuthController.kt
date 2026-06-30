package com.autotrading.controller

import com.autotrading.common.ApiResponse
import com.autotrading.config.AuthProperties
import com.autotrading.controller.dto.LoginRequest
import com.autotrading.controller.dto.TokenResponse
import com.autotrading.security.JwtService
import jakarta.validation.Valid
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 로그인·토큰 발급. 단일 운영자 자격증명(env)과 대조 후 JWT 발급. 공개 엔드포인트. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authProperties: AuthProperties,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ApiResponse<TokenResponse> {
        val ok = request.username == authProperties.username &&
            passwordEncoder.matches(request.password, authProperties.passwordHash)
        if (!ok) throw BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다")

        val token = jwtService.generate(request.username)
        return ApiResponse.ok(TokenResponse(token = token, expiresInSeconds = jwtService.expiresInSeconds()))
    }
}
