package com.meetingmate.app.controller;

import com.meetingmate.app.common.ApiResponse;
import com.meetingmate.app.dto.group.*;
import jakarta.validation.Valid;
import com.meetingmate.app.security.UserPrincipal;
import com.meetingmate.app.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    /**
     * 모임 생성
     */
    @PostMapping
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            @Valid @RequestBody GroupCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long userId = principal.getId();   // 로그인한 사용자 id
        GroupResponse response = groupService.createGroup(request, userId);
        return ResponseEntity.ok(ApiResponse.success("GROUP_CREATED", "모임이 생성되었습니다.", response));
    }

    /**
     * 모임 참여 (초대코드)
     */
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<JoinResponse>> joinGroup(
            @Valid @RequestBody GroupJoinRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long userId = principal.getId();
        JoinResponse response = groupService.joinGroup(request, userId);
        return ResponseEntity.ok(ApiResponse.success("GROUP_JOINED", "모임에 참여했습니다.", response));
    }

    /**
     * 내가 속한 모임 목록 조회
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<MyGroupResponse>>> getMyGroups(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long userId = principal.getId();
        List<MyGroupResponse> responses = groupService.getMyGroups(userId);
        return ResponseEntity.ok(ApiResponse.success("MY_GROUPS_FETCHED", "내 모임 목록을 조회했습니다.", responses));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<GroupDetailResponse>> getGroupDetail(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long userId = principal.getId();
        GroupDetailResponse response = groupService.getGroupDetail(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success("GROUP_DETAIL_FETCHED", "모임 상세 정보를 조회했습니다.", response));
    }

    @PostMapping("/{groupId}/places/{placeId}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPlace(
            @PathVariable Long groupId,
            @PathVariable Long placeId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        groupService.confirmPlace(groupId, placeId, principal.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("PLACE_CONFIRMED", "장소가 확정되었습니다.", null));
    }
}
