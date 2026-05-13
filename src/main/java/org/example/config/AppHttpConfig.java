package org.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP 客户端配置
 * 设置全局超时时间，用于 Embedding 服务的 HTTP 调用
 */
@Configuration
public class AppHttpConfig {

    private static final long TIMEOUT_MS = 180000; // 3分钟

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout((int) TIMEOUT_MS);
                    setReadTimeout((int) TIMEOUT_MS);
                }});
    }
}
