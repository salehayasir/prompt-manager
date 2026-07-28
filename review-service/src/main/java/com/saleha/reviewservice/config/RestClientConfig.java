package com.saleha.reviewservice.config;

import com.saleha.reviewservice.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${prompt.service.url}")
    private String promptServiceUrl;

    private final JwtService jwtService;

    public RestClientConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    public RestClient restClient() {

        return RestClient.builder()
                .baseUrl(promptServiceUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(jwtService.generateServiceToken());
                    return execution.execute(request, body);
                })
                .build();
    }
}