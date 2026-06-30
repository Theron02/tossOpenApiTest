package com.autotrading.security

import com.autotrading.config.JwtProperties
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.Date

/** JWT 발급·검증. 앱↔백엔드 인증 전용(토스 토큰과 무관). */
@Service
class JwtService(private val props: JwtProperties) {

    private val key = Keys.hmacShaKeyFor(props.secret.toByteArray())

    /** subject(운영자 username)로 토큰 발급. */
    fun generate(subject: String): String {
        val now = System.currentTimeMillis()
        return Jwts.builder()
            .subject(subject)
            .issuedAt(Date(now))
            .expiration(Date(now + props.expirationMinutes * 60_000))
            .signWith(key)
            .compact()
    }

    fun expiresInSeconds(): Long = props.expirationMinutes * 60

    /** 유효하면 subject 반환, 아니면 null(만료·위조·형식오류 포함). */
    fun validateAndGetSubject(token: String): String? = try {
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload.subject
    } catch (e: JwtException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
