package com.saleha.promptservice.controller;

import com.saleha.promptservice.entity.Prompt;
import com.saleha.promptservice.repository.PromptRepository;
import com.saleha.promptservice.dto.CreatePromptRequest;
import com.saleha.promptservice.dto.PageResponse;
import com.saleha.promptservice.dto.PromptResponse;
import com.saleha.promptservice.dto.UpdatePromptRequest;
import com.saleha.promptservice.service.AttachmentService;
import com.saleha.promptservice.service.PromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

// NOTE: every method here returns/accepts a DTO (CreatePromptRequest,
// UpdatePromptRequest, PromptResponse), never the Prompt JPA entity directly.
// The entity only lives in the repository/service/cache layers.
// Caching (@Cacheable/@CachePut/@CacheEvict) lives entirely in PromptService
// now - having @CachePut here return PromptResponse while PromptService's
// @Cacheable returned Prompt caused a type mismatch (ClassCastException) the
// first time a prompt was re-fetched after being updated.
@RestController
@RequestMapping("/prompts")
public class PromptController {

    private static final Logger log = LoggerFactory.getLogger(PromptController.class);

    private final PromptRepository promptRepository;
    private final AttachmentService attachmentService;
    private final PromptService promptService;
    private final CacheManager cacheManager;

    public PromptController(
            PromptRepository promptRepository,
            AttachmentService attachmentService,
            PromptService promptService,
            CacheManager cacheManager
    ) {
        this.promptRepository = promptRepository;
        this.attachmentService = attachmentService;
        this.promptService = promptService;
        this.cacheManager = cacheManager;
    }


    // POST /prompts
    @PostMapping
    public PromptResponse createPrompt(@RequestBody CreatePromptRequest request) {

        Prompt saved = promptService.createPrompt(request);

        return PromptResponse.from(saved);
    }


    // GET /prompts
    @GetMapping
    public PageResponse<PromptResponse> getAllPrompts(
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {

        validateSortField(sortBy);

        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Prompt> result = (tag != null && !tag.isBlank())
                ? promptRepository.findByTagsContainingIgnoreCase(tag, pageable)
                : promptRepository.findAll(pageable);

        Page<PromptResponse> mapped = result.map(PromptResponse::from);

        return PageResponse.from(mapped);
    }

    // Spring Data would normally reject an unknown sortBy at query time via
    // its own exception, but that class's package moved across Spring Data
    // versions - checking against Prompt's declared fields directly avoids
    // depending on the exact exception type/location.
    private void validateSortField(String sortBy) {

        try {
            Prompt.class.getDeclaredField(sortBy);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Invalid sort field: " + sortBy);
        }
    }


    // GET /prompts/{id}
    @GetMapping("/{id}")
    public PromptResponse getPrompt(@PathVariable UUID id) {

        Cache cache = cacheManager.getCache("prompts");
        boolean cacheHit = cache != null && cache.get(id) != null;

        if (cacheHit) {
            log.info("CACHE HIT - serving prompt {} from cache", id);
        }

        // getPromptById is @Cacheable - its body (and the "CACHE MISS" log)
        // only runs when cacheHit is false above.
        Prompt prompt = promptService.getPromptById(id);

        return PromptResponse.from(prompt);
    }

    // PUT /prompts/{id}
    @PutMapping("/{id}")
    public PromptResponse updatePrompt(
            @PathVariable UUID id,
            @RequestBody UpdatePromptRequest request
    ) {

        Prompt saved = promptService.updatePrompt(id, request);

        return PromptResponse.from(saved);
    }


    // DELETE /prompts/{id}
    @DeleteMapping("/{id}")
    public void deletePrompt(@PathVariable UUID id) {

        promptService.deletePrompt(id);
    }

    @GetMapping("/{id}/exists")
    public boolean promptExists(@PathVariable UUID id) {

        return promptRepository.existsById(id);
    }


    // POST /prompts/{id}/attachment
    @PostMapping(value = "/{id}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PromptResponse uploadAttachment(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) {
        Prompt prompt = attachmentService.uploadAttachment(id, file);
        return PromptResponse.from(prompt);
    }


    // DELETE /prompts/{id}/attachment
    @DeleteMapping("/{id}/attachment")
    public PromptResponse deleteAttachment(@PathVariable UUID id) {
        Prompt prompt = attachmentService.deleteAttachment(id);
        return PromptResponse.from(prompt);
    }

}
