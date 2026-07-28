package com.saleha.reviewservice.service;

import com.saleha.reviewservice.model.Review;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String NOTIFICATIONS_LOG_FILE = "notifications.log";

    // Runs on the dedicated "notificationExecutor" pool, not the calling
    // (HTTP request) thread - the caller does not wait for this to finish.
    @Async("notificationExecutor")
    public void sendReviewCreatedNotification(Review review) {

        try {
            // Simulates a slow notification dispatch (e.g. email/webhook),
            // to prove the HTTP response already returned before this runs.
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        String line = String.format(
                "[%s] New review by %s on prompt %s - score %d%n",
                LocalDateTime.now(), review.getReviewerName(), review.getPromptId(), review.getScore()
        );

        try (FileWriter writer = new FileWriter(NOTIFICATIONS_LOG_FILE, true)) {
            writer.write(line);
        } catch (IOException e) {
            log.error("Failed to write notification for review {}: {}", review.getId(), e.getMessage());
            return;
        }

        log.info("NOTIFICATION SENT - review {} (reviewer={}, promptId={}, score={})",
                review.getId(), review.getReviewerName(), review.getPromptId(), review.getScore());
    }
}
