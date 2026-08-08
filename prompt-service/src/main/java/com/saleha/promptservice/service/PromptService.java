package com.saleha.promptservice.service;

import com.saleha.promptservice.dto.CreatePromptRequest;
import com.saleha.promptservice.dto.UpdatePromptRequest;
import com.saleha.promptservice.entity.Prompt;
import com.saleha.promptservice.exception.ResourceNotFoundException;
import com.saleha.promptservice.repository.PromptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
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

    public Prompt createPrompt(CreatePromptRequest request) {

        Prompt prompt = new Prompt();

        prompt.setName(request.getName());
        prompt.setDescription(request.getDescription());
        prompt.setContent(request.getContent());
        prompt.setTags(request.getTags());
        prompt.setModelTarget(request.getModelTarget());

        return promptRepository.save(prompt);
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

    // Returns Prompt (not PromptResponse) so the value @CachePut stores under
    // key #id is the SAME type getPromptById()'s @Cacheable expects to read
    // back - mixing types on the same cache/key caused the ClassCastException
    // seen on the next GET after an update.
    @CachePut(value = "prompts", key = "#id")
    public Prompt updatePrompt(UUID id, UpdatePromptRequest request) {

        Prompt existingPrompt = promptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Prompt not found with id: " + id
                ));

        if (request.getName() != null) {
            existingPrompt.setName(request.getName());
        }

        if (request.getDescription() != null) {
            existingPrompt.setDescription(request.getDescription());
        }

        if (request.getContent() != null) {
            existingPrompt.setContent(request.getContent());
        }

        if (request.getTags() != null) {
            existingPrompt.setTags(request.getTags());
        }

        if (request.getModelTarget() != null) {
            existingPrompt.setModelTarget(request.getModelTarget());
        }

        Prompt saved = promptRepository.save(existingPrompt);

        log.info("CACHE UPDATED - prompt {} refreshed in cache after update", id);

        return saved;
    }

    @CacheEvict(value = "prompts", key = "#id")
    public void deletePrompt(UUID id) {

        if (!promptRepository.existsById(id)) {
            throw new ResourceNotFoundException(
                    "Prompt not found with id: " + id
            );
        }

        promptRepository.deleteById(id);

        log.info("CACHE EVICTED - prompt {} removed from cache after delete", id);
    }
}