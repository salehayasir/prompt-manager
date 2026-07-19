package com.saleha.reviewservice.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class Review {

    private UUID id;

    private UUID promptId;

    private Prompt promptSnapshot;

    private String reviewerName;

    @Min(1)
    @Max(5)
    private int score;

    private String feedback;

    private LocalDateTime reviewedAt;
}