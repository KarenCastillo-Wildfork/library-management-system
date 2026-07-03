package com.library.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTP client used to call Service B (the loans service).
 *
 * <p>We use Spring's {@link RestClient} (introduced in Spring 6.1 / Boot 3.2) instead
 * of the deprecated {@code RestTemplate} or the reactive {@code WebClient}: this
 * application is entirely blocking/servlet-based, so a synchronous client is the
 * right fit, and {@code RestClient} gives a modern fluent API without pulling in
 * Reactor just to call {@code .block()} everywhere.</p>
 *
 * <p>The request factory is the JDK's built-in {@link HttpClient} (Java 11+), so no
 * extra HTTP client dependency (Apache HttpClient, OkHttp) is needed. Explicit
 * connect/read timeouts are configured so a stalled or unreachable Service B fails
 * fast instead of hanging the caller's request thread.</p>
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient serviceBRestClient(AppProperties appProperties) {
        Duration timeout = Duration.ofMillis(appProperties.getServiceB().getTimeoutMs());

        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(appProperties.getServiceB().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
