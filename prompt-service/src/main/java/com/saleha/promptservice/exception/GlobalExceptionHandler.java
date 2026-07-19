package com.saleha.promptservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(
            ResourceNotFoundException exception
    ) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                    Map.of(
                        "message", exception.getMessage(),
                        "status", 404,
                        "timestamp", LocalDateTime.now()
                    )
                );
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception exception) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                    Map.of(
                        "message", exception.getMessage(),
                        "status", 400,
                        "timestamp", LocalDateTime.now()
                    )
                );
    }
}