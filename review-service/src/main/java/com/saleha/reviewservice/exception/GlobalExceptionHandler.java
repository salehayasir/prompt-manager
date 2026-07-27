package com.saleha.reviewservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status,
            String message
    ) {

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);

        return ResponseEntity.status(status).body(body);
    }

    // 404 - review does not exist
    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleReviewNotFound(
            ReviewNotFoundException exception
    ) {

        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    // 404 - referenced prompt does not exist
    @ExceptionHandler(PromptNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePromptNotFound(
            PromptNotFoundException exception
    ) {

        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    // 503 - prompt service could not be reached / errored out
    @ExceptionHandler(PromptServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handlePromptServiceUnavailable(
            PromptServiceUnavailableException exception
    ) {

        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    // 400 - request body failed bean validation (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException exception
    ) {

        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    // 400 - path variable / query param has the wrong type (e.g. bad UUID)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {

        String message = "Invalid value for parameter '" + exception.getName() + "'";

        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    // 400 - generic bad input
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException exception
    ) {

        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    // 500 - anything unexpected
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(
            Exception exception
    ) {

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred: " + exception.getMessage()
        );
    }
}
