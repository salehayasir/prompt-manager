package com.saleha.reviewservice.service;

import com.saleha.reviewservice.dto.CreateReviewRequest;
import com.saleha.reviewservice.exception.PromptNotFoundException;
import com.saleha.reviewservice.exception.PromptServiceUnavailableException;
import com.saleha.reviewservice.exception.ReviewNotFoundException;
import com.saleha.reviewservice.model.Prompt;
import com.saleha.reviewservice.model.Review;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    private final RestClient restClient;
    private final ReviewStorageService reviewStorageService;
    private final NotificationService notificationService;

    public ReviewService(RestClient restClient,
                         ReviewStorageService reviewStorageService,
                         NotificationService notificationService) {

        this.restClient = restClient;
        this.reviewStorageService = reviewStorageService;
        this.notificationService = notificationService;
    }

    public Review createReview(CreateReviewRequest request) throws IOException {

        Prompt prompt = fetchPrompt(request.getPromptId());

        Review review = new Review();

        review.setId(UUID.randomUUID());
        review.setPromptId(request.getPromptId());
        review.setPromptSnapshot(prompt.getContent());
        review.setReviewerName(request.getReviewerName());
        review.setScore(request.getScore());
        review.setFeedback(request.getFeedback());
        review.setReviewedAt(LocalDateTime.now());

        reviewStorageService.saveReview(review);

        // Fire-and-forget: runs on notificationExecutor, this method returns
        // immediately without waiting for it to complete.
        notificationService.sendReviewCreatedNotification(review);

        return review;
    }

    // Fetches the prompt from Prompt Service, distinguishing "not found" from "unavailable"
    private Prompt fetchPrompt(UUID promptId) {

        try {

            Prompt prompt = restClient.get()
                    .uri("/prompts/{id}", promptId)
                    .retrieve()
                    .body(Prompt.class);

            if (prompt == null) {
                throw new PromptNotFoundException(
                        "Prompt not found with id: " + promptId
                );
            }

            return prompt;

        } catch (HttpClientErrorException.NotFound e) {

            throw new PromptNotFoundException(
                    "Prompt not found with id: " + promptId
            );

        } catch (HttpClientErrorException | HttpServerErrorException e) {

            // Prompt Service responded, but with an unexpected error status
            throw new PromptServiceUnavailableException(
                    "Prompt Service returned an unexpected error", e
            );

        } catch (ResourceAccessException e) {

            // Connection refused, timeout, DNS failure, etc. - service is actually down
            throw new PromptServiceUnavailableException(
                    "Prompt Service is unavailable", e
            );

        } catch (RestClientException e) {

            throw new PromptServiceUnavailableException(
                    "Prompt Service is unavailable", e
            );
        }
    }

    public List<Review> getAllReviews(
            UUID promptId,
            String reviewerName,
            Integer minScore,
            Integer maxScore
    ) throws IOException {

        return reviewStorageService.getAllReviews().stream()
                .filter(r -> promptId == null || promptId.equals(r.getPromptId()))
                .filter(r -> reviewerName == null
                        || (r.getReviewerName() != null
                            && r.getReviewerName().equalsIgnoreCase(reviewerName)))
                .filter(r -> minScore == null || r.getScore() >= minScore)
                .filter(r -> maxScore == null || r.getScore() <= maxScore)
                .collect(Collectors.toList());
    }

    public Review getReviewById(UUID id)
            throws IOException {

        Review review = reviewStorageService.getReviewById(id);

        if (review == null) {
            throw new ReviewNotFoundException(
                    "Review not found with id: " + id
            );
        }

        return review;
    }

    public List<Review> getReviewsByPromptId(UUID promptId)
            throws IOException {

        return reviewStorageService.getReviewsByPromptId(promptId);
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
