package com.saleha.promptservice.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {

        // Simple in-memory cache, keyed by prompt id. No expiry/eviction policy
        // beyond the explicit @CacheEvict/@CachePut calls elsewhere in the app.
        return new ConcurrentMapCacheManager("prompts");
    }
}
