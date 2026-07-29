package com.saleha.promptservice.dto;

import com.saleha.promptservice.entity.Prompt;

import java.time.LocalDateTime;
import java.util.UUID;

// Response DTO for all prompt endpoints. Keeps the JPA entity (Prompt) out of
// the HTTP layer entirely - the entity is only ever seen by the repository/
// service/cache layers, and gets mapped to this shape right before it leaves
// the controller.
public record PromptResponse(
        UUID id,
        String name,
        String description,
        String content,
        String tags,
        String modelTarget,
        String attachmentUrl,
        String attachmentPublicId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PromptResponse from(Prompt prompt) {

        return new PromptResponse(
                prompt.getId(),
                prompt.getName(),
                prompt.getDescription(),
                prompt.getContent(),
                prompt.getTags(),
                prompt.getModelTarget(),
                prompt.getAttachmentUrl(),
                prompt.getAttachmentPublicId(),
                prompt.getCreatedAt(),
                prompt.getUpdatedAt()
        );
    }
}
