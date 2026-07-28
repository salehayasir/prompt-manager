package com.saleha.promptservice.exception;

// Cloudinary was reached, but responded with an error for the requested operation.
public class CloudinaryOperationException extends RuntimeException {

    public CloudinaryOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
