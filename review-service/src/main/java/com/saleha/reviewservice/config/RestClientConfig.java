package com.saleha.reviewservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;


@Configuration
public class RestClientConfig {

    @Value("${prompt.service.url}")
    private String promptServiceUrl;

    @Bean
    public RestClient restClient() {

        return RestClient.builder()
                .baseUrl(promptServiceUrl)
                .build();
    }
    
}