package com.autotrading.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.client.ClientHttpRequestFactories
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * ML 예측 서비스 호출용 [RestClient]. 짧은 타임아웃(예측 실패는 백엔드가 HOLD 로 안전 처리).
 */
@Configuration
@EnableConfigurationProperties(MlProperties::class)
class MlClientConfig {

    @Bean
    fun mlRestClient(props: MlProperties): RestClient {
        val settings = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(Duration.ofMillis(props.connectTimeoutMs))
            .withReadTimeout(Duration.ofMillis(props.readTimeoutMs))
        return RestClient.builder()
            .baseUrl(props.baseUrl)
            .requestFactory(ClientHttpRequestFactories.get(settings))
            .build()
    }
}
