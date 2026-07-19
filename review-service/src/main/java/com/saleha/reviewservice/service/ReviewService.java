package com.saleha.reviewservice.service;

import com.saleha.reviewservice.model.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.saleha.reviewservice.dto.CreateReviewRequest;
import com.saleha.reviewservice.model.Prompt;
import com.saleha.reviewservice.model.Review;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Service
public class ReviewService {

    private final RestClient restClient;
    private final ReviewStorageService reviewStorageService;

    public ReviewService(RestClient restClient,
                         ReviewStorageService reviewStorageService) {

        this.restClient = restClient;
        this.reviewStorageService = reviewStorageService;
    }
    public Review createReview(CreateReviewRequest request) throws IOException {

        Prompt prompt;

        try {

            prompt = restClient.get()
                    .uri("/prompts/{id}", request.getPromptId())
                    .retrieve()
                    .body(Prompt.class);

        } catch (Exception e) {

            throw new RuntimeException("Prompt service unavailable");
        }

        Review review = new Review();

        review.setId(UUID.randomUUID());
        review.setPromptId(request.getPromptId());
        review.setPromptSnapshot(prompt);
        review.setReviewerName(request.getReviewerName());
        review.setScore(request.getScore());
        review.setFeedback(request.getFeedback());
        review.setReviewedAt(LocalDateTime.now());

        reviewStorageService.saveReview(review);

        return review;
    }
    public List<Review> getAllReviews()
                throws IOException {

            return reviewStorageService.getAllReviews();
        }



        public Review getReviewById(UUID id)
                throws IOException {

            return reviewStorageService.getReviewById(id);
        }



        public ReviewSummary getSummary(UUID promptId)
                throws IOException {


            List<Review> reviews =
                    reviewStorageService
                            .getReviewsByPromptId(promptId);


            double average = reviews.stream()
                    .mapToInt(Review::getScore)
                    .average()
                    .orElse(0);


            return new ReviewSummary(
                    promptId,
                    reviews.size(),
                    average
            );
        }



        public record ReviewSummary(
                UUID promptId,
                int totalReviews,
                double averageScore
        ) {}
}