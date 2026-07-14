package com.autotrading.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * CORS 설정 (Flutter 웹 개발용).
 *
 * 주의: MVC 레벨(WebMvcConfigurer.addCorsMappings)이 아니라 CorsConfigurationSource 빈으로 둔다.
 * Security 필터 체인이 MVC보다 먼저 실행되므로, MVC 레벨 CORS만 있으면 보호 자원의
 * preflight(OPTIONS)가 401로 차단된다. SecurityConfig의 http.cors()가 이 빈을 사용한다.
 */
@Configuration
class WebConfig {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*") // 개발용: 모든 도메인·포트 허용. 운영 전환 시 앱 도메인으로 제한할 것
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS") // 제어 API는 PATCH 사용
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600 // preflight 결과 캐싱(초)
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}