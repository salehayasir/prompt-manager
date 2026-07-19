package com.saleha.promptservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {

        CorsConfiguration config = new CorsConfiguration();

        // Local development
        config.addAllowedOrigin("http://localhost");
        config.addAllowedOrigin("http://localhost:5173");

        // Allow any ngrok subdomain
        config.addAllowedOriginPattern("https://*.ngrok-free.dev");

        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        // Only needed if you use cookies or authentication
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}