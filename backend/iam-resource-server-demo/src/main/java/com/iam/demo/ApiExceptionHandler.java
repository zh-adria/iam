package com.iam.demo;

import com.iam.sdk.IamSdkException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IamSdkException.class)
    public ResponseEntity<Map<String, String>> handleIamSdkException(IamSdkException ex) {
        HttpStatus status = ex.getMessage() != null && ex.getMessage().contains("principal")
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(Map.of(
                "code", status == HttpStatus.UNAUTHORIZED ? "UNAUTHORIZED" : "FORBIDDEN",
                "message", ex.getMessage()));
    }
}
