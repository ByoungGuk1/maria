package com.app.maria.global.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private final String mydataUrl;
    private final String returnsSecurityUrl;

    public RestClientConfig(
            @Value("${custom.mydata.url}") String mydataUrl,
            @Value("${custom.returns-security.url}") String returnsSecurityUrl) {
        this.mydataUrl = mydataUrl;
        this.returnsSecurityUrl = returnsSecurityUrl;
    }

    @Bean
    @Qualifier("returnSecuritiesRestClient")
    public RestClient returnSecuritiesRestClient(RestClient.Builder builder) {
        return builder.baseUrl(returnsSecurityUrl).build();
    }

    @Bean
    @Qualifier("mydataRestClient")
    public RestClient mydataRestClient(RestClient.Builder builder) {
        return builder.baseUrl(mydataUrl).build();
    }
}
