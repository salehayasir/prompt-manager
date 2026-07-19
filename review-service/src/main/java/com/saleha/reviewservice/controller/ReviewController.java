package com.saleha.reviewservice.controller;

import com.saleha.reviewservice.dto.CreateReviewRequest;
import com.saleha.reviewservice.model.Review;
import com.saleha.reviewservice.service.ReviewService;
import jakarta.validation.Valid;

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



    // Get all reviews
    @GetMapping
    public List<Review> getAllReviews()
            throws IOException {

        return reviewService.getAllReviews();
    }



    // Get review by id
    @GetMapping("/{id}")
    public Review getReviewById(
            @PathVariable UUID id
    ) throws IOException {

        return reviewService.getReviewById(id);
    }



    // Get summary for a prompt
    @GetMapping("/prompt/{promptId}/summary")
    public ReviewService.ReviewSummary getSummary(
            @PathVariable UUID promptId
    ) throws IOException {

        return reviewService.getSummary(promptId);
    }

}