package com.saleha.reviewservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateReviewRequest {

    @NotNull
    private UUID promptId;

    @NotBlank
    private String reviewerName;

    @Min(1)
    @Max(5)
    private int score;

    @NotBlank
    private String feedback;
}