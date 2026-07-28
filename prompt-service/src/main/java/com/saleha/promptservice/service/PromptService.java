package com.saleha.promptservice.service;

import com.saleha.promptservice.entity.Prompt;
import com.saleha.promptservice.exception.ResourceNotFoundException;
import com.saleha.promptservice.repository.PromptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PromptService {

    private static final Logger log = LoggerFactory.getLogger(PromptService.class);

    private final PromptRepository promptRepository;

    public PromptService(PromptRepository promptRepository) {
        this.promptRepository = promptRepository;
    }

    // Only runs on a cache miss - a cache hit returns straight from the
    // "prompts" cache without this method body (and this log line) executing.
    @Cacheable(value = "prompts", key = "#id")
    public Prompt getPromptById(UUID id) {

        log.info("CACHE MISS - loading prompt {} from the database", id);

        return promptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prompt not found with id: " + id
                ));
    }
}