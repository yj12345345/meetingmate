package com.meetingmate.app.exception;

import com.meetingmate.app.common.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "그룹을 찾을 수 없습니다."),
    GROUP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "GROUP_ACCESS_DENIED", "그룹 접근 권한이 없습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "장소를 찾을 수 없습니다."),
    PLACE_NOT_IN_GROUP(HttpStatus.BAD_REQUEST, "PLACE_NOT_IN_GROUP", "해당 장소는 이 그룹에 속하지 않습니다."),
    PLACE_ALREADY_VOTED(HttpStatus.CONFLICT, "PLACE_ALREADY_VOTED", "이미 투표한 장소입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    public ApiResponse<Void> toResponse() {
        return ApiResponse.fail(code, message);
    }
}
