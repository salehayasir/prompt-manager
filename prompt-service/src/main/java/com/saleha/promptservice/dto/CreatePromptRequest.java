package com.saleha.promptservice.dto;

import lombok.Data;

@Data
public class CreatePromptRequest {

    private String name;

    private String description;

    private String content;

    private String tags;

    private String modelTarget;
}
