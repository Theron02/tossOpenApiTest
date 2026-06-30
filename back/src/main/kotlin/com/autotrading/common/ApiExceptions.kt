package com.autotrading.common

/** 자원을 찾을 수 없음 → 404. */
class ResourceNotFoundException(message: String) : RuntimeException(message)

/** 상태 충돌(예: 운영 계정 모호, 위험동작 미확인) → 409. */
class ConflictException(message: String) : RuntimeException(message)
