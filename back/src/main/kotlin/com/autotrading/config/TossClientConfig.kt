package com.autotrading.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.client.ClientHttpRequestFactories
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * 토스 API 호출용 [RestClient]. base-url과 타임아웃만 설정한다.
 * 인증 헤더(Authorization)는 호출 시점에 [com.autotrading.external.toss.TossTokenProvider]가 주입한다.
 */
@Configuration
@EnableConfigurationProperties(TossProperties::class)
class TossClientConfig {

    @Bean
    fun tossRestClient(props: TossProperties): RestClient {
        val settings = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(Duration.ofMillis(props.connectTimeoutMs))
            .withReadTimeout(Duration.ofMillis(props.readTimeoutMs))
        return RestClient.builder()
            .baseUrl(props.baseUrl)
            .requestFactory(ClientHttpRequestFactories.get(settings))
            .build()
    }
}
