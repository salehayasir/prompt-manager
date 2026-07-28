package com.saleha.reviewservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.saleha.reviewservice.model.Review;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null) {
            return new ArrayList<>();
        }

        List<Review> reviews = new ArrayList<>();

        for (File file : files) {

            Review review = readReviewLeniently(file);

            // Skip files that can't be read/migrated instead of failing the whole request
            if (review != null) {
                reviews.add(review);
            }
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

        return readReviewLeniently(file);
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

    /**
     * Reads a review file, tolerating the old on-disk format where
     * promptSnapshot was the full Prompt object instead of a String.
     * If an old-format file is found, it's transparently migrated and
     * rewritten so this only happens once per file. Files that can't
     * be read or migrated are skipped (logged), not thrown.
     */
    private Review readReviewLeniently(File file) {

        try {

            return objectMapper.readValue(file, Review.class);

        } catch (IOException primaryFailure) {

            try {

                JsonNode root = objectMapper.readTree(file);
                JsonNode snapshotNode = root.get("promptSnapshot");

                if (root instanceof ObjectNode objectNode
                        && snapshotNode != null
                        && snapshotNode.isObject()) {

                    JsonNode contentNode = snapshotNode.get("content");

                    objectNode.put(
                            "promptSnapshot",
                            contentNode != null ? contentNode.asText() : null
                    );

                    Review migrated = objectMapper.treeToValue(objectNode, Review.class);

                    // Persist the migration so this file reads cleanly next time
                    objectMapper.writeValue(file, migrated);

                    return migrated;
                }

            } catch (IOException migrationFailure) {

                System.err.println(
                        "Skipping unreadable review file " + file.getName()
                                + ": " + migrationFailure.getMessage()
                );

                return null;
            }

            System.err.println(
                    "Skipping unreadable review file " + file.getName()
                            + ": " + primaryFailure.getMessage()
            );

            return null;
        }
    }
}
