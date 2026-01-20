package com.meetingmate.app.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GroupNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleGroupNotFound() {
        ErrorCode ec = ErrorCode.GROUP_NOT_FOUND;
        return ResponseEntity
                .status(ec.getStatus())
                .body(ec.toResponse());
    }

    @ExceptionHandler(GroupAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied() {
        ErrorCode ec = ErrorCode.GROUP_ACCESS_DENIED;
        return ResponseEntity
                .status(ec.getStatus())
                .body(ec.toResponse());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity
                .badRequest()
                .body(new ApiErrorResponse("BAD_REQUEST", e.getMessage()));
    }
}