package com.meetingmate.app.controller;

import com.meetingmate.app.common.ApiResponse;
import com.meetingmate.app.domain.user.dto.UserProfileUpdateRequest;
import com.meetingmate.app.domain.user.dto.UserMeResponse;
import com.meetingmate.app.security.UserPrincipal;
import com.meetingmate.app.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserApiController {

    private final UserService userService;

    @GetMapping("/api/user/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success("USER_ME_FETCHED", "내 사용자 정보를 조회했습니다.", userService.getMyInfo(principal.getId()))
        );
    }

    @PutMapping("/api/user/profile")
    public ResponseEntity<ApiResponse<UserMeResponse>> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UserProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("USER_PROFILE_UPDATED", "닉네임이 수정되었습니다.", userService.updateNickname(principal.getId(), request.getNickname()))
        );
    }

    @DeleteMapping("/api/user")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@AuthenticationPrincipal UserPrincipal principal) {
        userService.deleteMyAccount(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("USER_DELETED", "회원 탈퇴가 완료되었습니다.", null));
    }
}
