package com.saleha.promptservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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

    // 404 - resource does not exist
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException exception
    ) {

        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage());
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

    // 400 - generic bad input (also covers an invalid sortBy field name - see PromptController)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException exception
    ) {

        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    // 400 - missing file or unsupported file type on attachment upload
    @ExceptionHandler(InvalidAttachmentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidAttachment(
            InvalidAttachmentException exception
    ) {

        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    // 400 - the "file" part was missing from the multipart request entirely
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPart(
            MissingServletRequestPartException exception
    ) {

        return buildResponse(HttpStatus.BAD_REQUEST, "No file was provided");
    }

    // 400 - request body wasn't multipart at all (e.g. no file attached, no body sent)
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception
    ) {

        return buildResponse(HttpStatus.BAD_REQUEST, "No file was provided");
    }

    // 400 - uploaded file exceeds the configured size limit
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(
            MaxUploadSizeExceededException exception
    ) {

        return buildResponse(HttpStatus.BAD_REQUEST, "Uploaded file is too large");
    }

    // 502 - Cloudinary was reached but returned an error for the operation
    @ExceptionHandler(CloudinaryOperationException.class)
    public ResponseEntity<Map<String, Object>> handleCloudinaryOperation(
            CloudinaryOperationException exception
    ) {

        return buildResponse(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    // 503 - Cloudinary could not be reached at all
    @ExceptionHandler(CloudinaryUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleCloudinaryUnavailable(
            CloudinaryUnavailableException exception
    ) {

        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    // 401 - bad login credentials
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(
            InvalidCredentialsException exception
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    // 401 - Spring Security auth failures (e.g. bad/missing token surfacing here instead of the entry point)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(
            AuthenticationException exception
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED, exception.getMessage());
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