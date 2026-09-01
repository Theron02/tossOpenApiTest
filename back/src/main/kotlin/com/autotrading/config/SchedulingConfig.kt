package com.autotrading.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** @Scheduled 활성화 + 자동매매 루프 설정 바인딩. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(TradingProperties::class)
class SchedulingConfig
