package com.demo.orderplatform.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * Short timeouts on purpose: a hung catalog should surface as a fast 503 on
     * order intake, not as threads piling up in the servlet pool.
     */
    @Bean
    public RestClient inventoryRestClient(RestClient.Builder builder,
                                          @Value("${app.catalog.base-url}") String baseUrl,
                                          @Value("${app.catalog.timeout-ms}") int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
