package com.meetingmate.app.controller;

import com.meetingmate.app.dto.group.GroupCreateRequest;
import com.meetingmate.app.dto.group.GroupJoinRequest;
import com.meetingmate.app.dto.group.GroupResponse;
import com.meetingmate.app.dto.group.JoinResponse;
import com.meetingmate.app.dto.group.MyGroupResponse;
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
    public ResponseEntity<GroupResponse> createGroup(
            @RequestBody GroupCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long userId = principal.getId();   // 로그인한 사용자 id
        GroupResponse response = groupService.createGroup(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 모임 참여 (초대코드)
     */
    @PostMapping("/join")
    public ResponseEntity<JoinResponse> joinGroup(
            @RequestBody GroupJoinRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long userId = principal.getId();
        JoinResponse response = groupService.joinGroup(request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 내가 속한 모임 목록 조회
     */
    @GetMapping("/me")
    public ResponseEntity<List<MyGroupResponse>> getMyGroups(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Long userId = principal.getId();
        List<MyGroupResponse> responses = groupService.getMyGroups(userId);
        return ResponseEntity.ok(responses);
    }
}