package com.saleha.reviewservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saleha.reviewservice.model.Review;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class ReviewStorageService {

    private static final String REVIEW_FOLDER = "reviews";

    private final ObjectMapper objectMapper;

    public ReviewStorageService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    // Save single review as JSON file
    public void saveReview(Review review) throws IOException {

        File folder = new File(REVIEW_FOLDER);

        if (!folder.exists()) {
            folder.mkdirs();
        }

        File file = new File(folder, review.getId() + ".json");

        objectMapper.writeValue(file, review);
    }


    // Get all reviews
    public List<Review> getAllReviews() throws IOException {

        File folder = new File(REVIEW_FOLDER);

        if (!folder.exists()) {
            return new ArrayList<>();
        }

        File[] files = folder.listFiles();

        if (files == null) {
            return new ArrayList<>();
        }

        List<Review> reviews = new ArrayList<>();

        for (File file : files) {

            Review review = objectMapper.readValue(
                    file,
                    Review.class
            );

            reviews.add(review);
        }

        return reviews;
    }


    // Get review by id
    public Review getReviewById(UUID id) throws IOException {

        File file = new File(
                REVIEW_FOLDER,
                id + ".json"
        );

        if (!file.exists()) {
            return null;
        }

        return objectMapper.readValue(
                file,
                Review.class
        );
    }


    // Get reviews for a prompt
    public List<Review> getReviewsByPromptId(UUID promptId)
            throws IOException {

        List<Review> result = new ArrayList<>();

        for (Review review : getAllReviews()) {

            if (review.getPromptId()
                    .equals(promptId)) {

                result.add(review);
            }
        }

        return result;
    }
}