package com.app.maria.global.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    @Qualifier("registrableStockRestClient")
    public RestClient restClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:10001")
                .build();
    }

    @Bean
    @Qualifier("mydataRestClient")
    public RestClient mydataRestClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:10002")
                .build();
    }
}
