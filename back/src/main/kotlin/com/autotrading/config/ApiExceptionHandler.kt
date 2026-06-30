package com.autotrading.config

import com.autotrading.common.ApiResponse
import com.autotrading.common.ConflictException
import com.autotrading.common.ResourceNotFoundException
import com.autotrading.domain.risk.RiskRejectedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 컨트롤러 예외 일괄 처리. 모든 에러를 공통 래퍼([ApiResponse])의 error로 변환하고,
 * 도메인 의미에 맞는 HTTP 상태·flat 코드로 매핑한다. (인증 미보유 401은 SecurityConfig의 entry point가 처리)
 */
@RestControllerAdvice
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ResourceNotFoundException::class)
    fun notFound(e: ResourceNotFoundException) = body(HttpStatus.NOT_FOUND, "not-found", e.message)

    @ExceptionHandler(ConflictException::class)
    fun conflict(e: ConflictException) = body(HttpStatus.CONFLICT, "conflict", e.message)

    @ExceptionHandler(RiskRejectedException::class)
    fun riskRejected(e: RiskRejectedException) =
        body(HttpStatus.UNPROCESSABLE_ENTITY, "risk-rejected", "${e.reason}: ${e.message}")

    @ExceptionHandler(BadCredentialsException::class)
    fun badCredentials(e: BadCredentialsException) = body(HttpStatus.UNAUTHORIZED, "invalid-credentials", e.message)

    @ExceptionHandler(AccessDeniedException::class)
    fun accessDenied(e: AccessDeniedException) = body(HttpStatus.FORBIDDEN, "forbidden", "권한이 없습니다")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val detail = e.bindingResult.fieldErrors.joinToString("; ") { "${it.field} ${it.defaultMessage}" }
        return body(HttpStatus.BAD_REQUEST, "validation-failed", detail.ifBlank { "요청 검증 실패" })
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(e: HttpMessageNotReadableException) = body(HttpStatus.BAD_REQUEST, "bad-request", "요청 본문을 해석할 수 없습니다")

    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(e: IllegalArgumentException) = body(HttpStatus.BAD_REQUEST, "bad-request", e.message)

    @ExceptionHandler(Exception::class)
    fun generic(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("처리되지 않은 예외", e)
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "서버 오류가 발생했습니다")
    }

    private fun body(status: HttpStatus, code: String, message: String?): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(status).body(ApiResponse.fail(code, message ?: code))
}
