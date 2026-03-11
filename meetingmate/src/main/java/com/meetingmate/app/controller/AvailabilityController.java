package com.meetingmate.app.controller;

import com.meetingmate.app.common.ApiResponse;
import com.meetingmate.app.dto.availability.AvailabilityUpsertRequest;
import com.meetingmate.app.dto.availability.GroupAvailabilityResponse;
import jakarta.validation.Valid;
import com.meetingmate.app.security.UserPrincipal;
import com.meetingmate.app.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @PostMapping("/{groupId}/availability")
    public ResponseEntity<ApiResponse<Void>> upsertMyAvailability(
            @PathVariable Long groupId,
            @Valid @RequestBody AvailabilityUpsertRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        availabilityService.upsertMyAvailability(groupId, principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("AVAILABILITY_UPDATED", "가능 시간 정보가 저장되었습니다.", null));
    }

    @GetMapping("/{groupId}/availability")
    public ResponseEntity<ApiResponse<GroupAvailabilityResponse>> getGroupAvailability(
            @PathVariable Long groupId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "GROUP_AVAILABILITY_FETCHED",
                        "그룹 가능 시간 정보를 조회했습니다.",
                        availabilityService.getGroupAvailability(groupId, principal.getId(), from, to)
                )
        );
    }
}
