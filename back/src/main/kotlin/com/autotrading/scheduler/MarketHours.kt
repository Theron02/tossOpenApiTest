package com.autotrading.scheduler

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 한국 주식 정규장 운영시간 판정(순수 함수 — 테스트 용이).
 * KRX 정규 세션: 평일 09:00 ~ 15:30 (KST). 공휴일은 이번 범위 밖(바이패스 토글로 대응).
 */
object MarketHours {
    val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    private val OPEN = LocalTime.of(9, 0)
    private val CLOSE = LocalTime.of(15, 30)

    fun isOpen(now: ZonedDateTime): Boolean {
        val kst = now.withZoneSameInstant(SEOUL)
        if (kst.dayOfWeek == DayOfWeek.SATURDAY || kst.dayOfWeek == DayOfWeek.SUNDAY) return false
        val t = kst.toLocalTime()
        return !t.isBefore(OPEN) && !t.isAfter(CLOSE)
    }

    fun nowIsOpen(): Boolean = isOpen(ZonedDateTime.now(SEOUL))
}
