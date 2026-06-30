package com.autotrading.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** JWT 설정. [secret]은 HS256용 256bit 이상 비밀(환경변수로만). */
@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val secret: String,
    val expirationMinutes: Long = 120,
)

/**
 * 단일 운영자 자격증명. User 테이블 없이 env로만 주입한다.
 * [passwordHash]는 BCrypt 해시(평문 비밀번호를 코드·VCS에 두지 않는다).
 */
@ConfigurationProperties(prefix = "app.auth")
data class AuthProperties(
    val username: String,
    val passwordHash: String,
)
