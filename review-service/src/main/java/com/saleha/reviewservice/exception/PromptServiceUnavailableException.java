package com.saleha.reviewservice.exception;

public class PromptServiceUnavailableException extends RuntimeException {

    public PromptServiceUnavailableException(String message) {
        super(message);
    }

    public PromptServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
