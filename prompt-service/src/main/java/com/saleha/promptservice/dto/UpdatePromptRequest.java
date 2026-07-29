package com.saleha.promptservice.dto;

import lombok.Data;

// Request DTO for PUT /prompts/{id}. All fields are optional/nullable - only
// the fields the client actually sends get applied, exactly like the old
// behavior that used to bind the Prompt entity directly.
@Data
public class UpdatePromptRequest {

    private String name;

    private String description;

    private String content;

    private String tags;

    private String modelTarget;
}
