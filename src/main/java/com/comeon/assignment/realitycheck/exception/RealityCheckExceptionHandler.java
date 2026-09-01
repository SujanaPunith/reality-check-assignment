package com.comeon.assignment.realitycheck.exception;

import com.comeon.assignment.realitycheck.model.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RealityCheckExceptionHandler {

    @ExceptionHandler(RealityCheckException.class)
    public ResponseEntity<ApiErrorResponse> handleRealityCheckException(RealityCheckException exception) {
        return ResponseEntity.status(statusFor(exception.getCode()))
                .body(new ApiErrorResponse(exception.getCode(), messageFor(exception.getCode())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationFailure() {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "INVALID_INTERVAL_MINUTES", "intervalMinutes must be between 1 and 1440."));
    }

    private HttpStatus statusFor(String code) {
        return switch (code) {
            case "PLAYER_NOT_FOUND", "ACTIVE_SESSION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "ACTIVE_SESSION_ALREADY_EXISTS", "SESSION_STATE_CONFLICT" -> HttpStatus.CONFLICT;
            case "INVALID_INTERVAL_MINUTES" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private String messageFor(String code) {
        return switch (code) {
            case "PLAYER_NOT_FOUND" -> "Player was not found.";
            case "ACTIVE_SESSION_NOT_FOUND" -> "The player has no active reality-check session.";
            case "ACTIVE_SESSION_ALREADY_EXISTS" -> "The player already has an active reality-check session.";
            case "INVALID_INTERVAL_MINUTES" -> "intervalMinutes must be between 1 and 1440.";
            case "SESSION_STATE_CONFLICT" -> "The session changed concurrently. Retry the request.";
            default -> "An unexpected error occurred.";
        };
    }
}
