package com.saleha.promptservice.exception;

// Cloudinary could not be reached at all (timeout, DNS failure, connection refused).
public class CloudinaryUnavailableException extends RuntimeException {

    public CloudinaryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
