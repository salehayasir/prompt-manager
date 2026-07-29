package com.saleha.reviewservice.controller;

import com.saleha.reviewservice.dto.CreateReviewRequest;
import com.saleha.reviewservice.dto.PageResponse;
import com.saleha.reviewservice.model.Review;
import com.saleha.reviewservice.service.ReviewService;
import jakarta.validation.Valid;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/reviews")
public class ReviewController {


    private final ReviewService reviewService;


    public ReviewController(ReviewService reviewService) {

        this.reviewService = reviewService;
    }


    // Create review
    @PostMapping
    public Review createReview(
            @Valid @RequestBody CreateReviewRequest request
    ) throws IOException {

        return reviewService.createReview(request);
    }



    // Get all reviews, optionally filtered by promptId, reviewerName, minScore, maxScore
    @GetMapping
    public PageResponse<Review> getAllReviews(
            @RequestParam(required = false) UUID promptId,
            @RequestParam(required = false) String reviewerName,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) Integer maxScore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "reviewedAt") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) throws IOException {

        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        return PageResponse.from(
                reviewService.getAllReviews(promptId, reviewerName, minScore, maxScore, pageable)
        );
    }



    // Get review by id
    @GetMapping("/{id}")
    public Review getReviewById(
            @PathVariable UUID id
    ) throws IOException {

        return reviewService.getReviewById(id);
    }



    // Get all reviews for a specific prompt
    @GetMapping("/prompt/{promptId}")
    public List<Review> getReviewsByPromptId(
            @PathVariable UUID promptId
    ) throws IOException {

        return reviewService.getReviewsByPromptId(promptId);
    }



    // Get summary for a prompt
    @GetMapping("/prompt/{promptId}/summary")
    public ReviewService.ReviewSummary getSummary(
            @PathVariable UUID promptId
    ) throws IOException {

        return reviewService.getSummary(promptId);
    }

}
