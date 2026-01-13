package com.meetingmate.app.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "그룹을 찾을 수 없습니다."),
    GROUP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "GROUP_ACCESS_DENIED", "그룹 접근 권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    public ApiErrorResponse toResponse() {
        return new ApiErrorResponse(code, message);
    }
}