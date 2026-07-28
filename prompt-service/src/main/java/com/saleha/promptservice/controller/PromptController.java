package com.saleha.promptservice.controller;

import com.saleha.promptservice.entity.Prompt;
import com.saleha.promptservice.exception.ResourceNotFoundException;
import com.saleha.promptservice.repository.PromptRepository;
import com.saleha.promptservice.dto.CreatePromptRequest;
import com.saleha.promptservice.service.AttachmentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/prompts")
public class PromptController {

    private final PromptRepository promptRepository;
    private final AttachmentService attachmentService;

    public PromptController(PromptRepository promptRepository, AttachmentService attachmentService) {
        this.promptRepository = promptRepository;
        this.attachmentService = attachmentService;
    }


    // POST /prompts
   @PostMapping
    public Prompt createPrompt(@RequestBody CreatePromptRequest request) {

        Prompt prompt = new Prompt();

        prompt.setName(request.getName());
        prompt.setDescription(request.getDescription());
        prompt.setContent(request.getContent());
        prompt.setTags(request.getTags());
        prompt.setModelTarget(request.getModelTarget());

        return promptRepository.save(prompt);
    }


    // GET /prompts
    @GetMapping
    public List<Prompt> getAllPrompts() {
        return promptRepository.findAll();
    }


    @GetMapping("/{id}")
    public Prompt getPrompt(@PathVariable UUID id) {

        return promptRepository.findById(id)
                .orElseThrow(
                    () -> new ResourceNotFoundException(
                        "Prompt not found with id: " + id
                    )
                );
    }

        @PutMapping("/{id}")
    public Prompt updatePrompt(
            @PathVariable UUID id,
            @RequestBody Prompt updatedPrompt
    ) {

    Prompt existingPrompt = promptRepository.findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException(
                    "Prompt not found with id: " + id
                )
            );


    if (updatedPrompt.getName() != null) {
        existingPrompt.setName(updatedPrompt.getName());
    }

    if (updatedPrompt.getDescription() != null) {
        existingPrompt.setDescription(updatedPrompt.getDescription());
    }

    if (updatedPrompt.getContent() != null) {
        existingPrompt.setContent(updatedPrompt.getContent());
    }

    if (updatedPrompt.getTags() != null) {
        existingPrompt.setTags(updatedPrompt.getTags());
    }

    if (updatedPrompt.getModelTarget() != null) {
        existingPrompt.setModelTarget(updatedPrompt.getModelTarget());
    }


    return promptRepository.save(existingPrompt);
}


    // DELETE /prompts/{id}
    @DeleteMapping("/{id}")
public void deletePrompt(@PathVariable UUID id) {

    if (!promptRepository.existsById(id)) {
        throw new ResourceNotFoundException(
        "Prompt not found with id: " + id
);
    }

    promptRepository.deleteById(id);
}

@GetMapping("/{id}/exists")
public boolean promptExists(@PathVariable UUID id) {

    return promptRepository.existsById(id);
}


// POST /prompts/{id}/attachment
@PostMapping(value = "/{id}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Prompt uploadAttachment(
        @PathVariable UUID id,
        @RequestParam("file") MultipartFile file
) {
    return attachmentService.uploadAttachment(id, file);
}


// DELETE /prompts/{id}/attachment
@DeleteMapping("/{id}/attachment")
public Prompt deleteAttachment(@PathVariable UUID id) {
    return attachmentService.deleteAttachment(id);
}

}