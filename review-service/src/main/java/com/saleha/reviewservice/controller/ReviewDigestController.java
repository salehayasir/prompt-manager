package com.saleha.reviewservice.controller;

import com.saleha.reviewservice.exception.ReviewNotFoundException;
import com.saleha.reviewservice.service.ReviewDigestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reviews/digest")
public class ReviewDigestController {

    private final ReviewDigestService reviewDigestService;

    public ReviewDigestController(ReviewDigestService reviewDigestService) {
        this.reviewDigestService = reviewDigestService;
    }

    // GET /reviews/digest/latest
    @GetMapping("/latest")
    public ReviewDigestService.ReviewDigest getLatestDigest() {

        ReviewDigestService.ReviewDigest digest = reviewDigestService.getLatestDigest();

        if (digest == null) {
            throw new ReviewNotFoundException("No digest has been computed yet");
        }

        return digest;
    }
}
