package com.saleha.reviewservice.service;

import com.saleha.reviewservice.model.Review;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReviewDigestService {

    private static final Logger log = LoggerFactory.getLogger(ReviewDigestService.class);

    private final ReviewStorageService reviewStorageService;

    // Written by the scheduled job, read by the controller - volatile gives
    // safe publication across threads without needing a lock for this simple case.
    private volatile ReviewDigest latestDigest;

    public ReviewDigestService(ReviewStorageService reviewStorageService) {
        this.reviewStorageService = reviewStorageService;
    }

    // Compute one digest immediately at startup so the endpoint has data
    // right away, instead of waiting for the first scheduled interval to elapse.
    @PostConstruct
    public void computeOnStartup() {
        computeDigest();
    }

    @Scheduled(fixedRateString = "${digest.interval-ms}")
    public void computeDigest() {

        try {

            List<Review> reviews = reviewStorageService.getAllReviews();

            int totalReviews = reviews.size();

            double averageScore = reviews.stream()
                    .mapToInt(Review::getScore)
                    .average()
                    .orElse(0);

            // "Highest scoring prompt" = the prompt whose reviews have the
            // highest average score (not just the single highest review).
            UUID highestScoringPromptId = reviews.stream()
                    .collect(Collectors.groupingBy(
                            Review::getPromptId,
                            Collectors.averagingInt(Review::getScore)
                    ))
                    .entrySet().stream()
                    .max(Comparator.comparingDouble(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElse(null);

            ReviewDigest digest = new ReviewDigest(
                    totalReviews,
                    averageScore,
                    highestScoringPromptId,
                    LocalDateTime.now()
            );

            this.latestDigest = digest;

            log.info(
                    "DIGEST - totalReviews={}, averageScore={}, highestScoringPromptId={}",
                    digest.totalReviews(), digest.averageScore(), digest.highestScoringPromptId()
            );

        } catch (Exception e) {

            // A scheduled method throwing would just silently stop future runs -
            // log and keep the previous digest rather than let that happen.
            log.error("Failed to compute review digest: {}", e.getMessage(), e);
        }
    }

    public ReviewDigest getLatestDigest() {
        return latestDigest;
    }

    public record ReviewDigest(
            int totalReviews,
            double averageScore,
            UUID highestScoringPromptId,
            LocalDateTime computedAt
    ) {}
}
