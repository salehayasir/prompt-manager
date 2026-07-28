package com.saleha.reviewservice.config;

import com.saleha.reviewservice.security.JwtAuthenticationEntryPoint;
import com.saleha.reviewservice.security.JwtAuthenticationFilter;
import com.saleha.reviewservice.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint entryPoint;
    private final JwtService jwtService;

    public SecurityConfig(JwtAuthenticationEntryPoint entryPoint, JwtService jwtService) {
        this.entryPoint = entryPoint;
        this.jwtService = jwtService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/actuator/health",
                            "/swagger-ui/**",
                            "/v3/api-docs/**"
                    ).permitAll()
                    .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
            .addFilterBefore(
                    (jakarta.servlet.Filter) new JwtAuthenticationFilter(jwtService),
                    UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}