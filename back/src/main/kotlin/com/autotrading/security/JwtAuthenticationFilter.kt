package com.autotrading.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * `Authorization: Bearer <jwt>`를 검증해 SecurityContext에 인증을 채운다.
 * 토큰이 없거나 유효하지 않으면 컨텍스트를 비워둔 채 통과 → 보호 자원에서 401로 차단된다.
 */
@Component
class JwtAuthenticationFilter(private val jwtService: JwtService) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header != null && header.startsWith(BEARER)) {
            val subject = jwtService.validateAndGetSubject(header.removePrefix(BEARER).trim())
            if (subject != null && SecurityContextHolder.getContext().authentication == null) {
                val auth = UsernamePasswordAuthenticationToken(subject, null, listOf(SimpleGrantedAuthority("ROLE_OPERATOR")))
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private const val BEARER = "Bearer "
    }
}
