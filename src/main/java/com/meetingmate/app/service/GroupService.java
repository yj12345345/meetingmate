package com.meetingmate.app.service;

import com.meetingmate.app.domain.group.Group;
import com.meetingmate.app.domain.group.GroupMember;
import com.meetingmate.app.dto.group.GroupCreateRequest;
import com.meetingmate.app.dto.group.GroupJoinRequest;
import com.meetingmate.app.dto.group.GroupResponse;
import com.meetingmate.app.dto.group.JoinResponse;
import com.meetingmate.app.dto.group.MyGroupResponse;
import com.meetingmate.app.repository.GroupMemberRepository;
import com.meetingmate.app.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 6;
    private final Random random = new SecureRandom();

    /**
     * 모임 생성
     */
    @Transactional
    public GroupResponse createGroup(GroupCreateRequest request, Long hostUserId) {
        // 초대코드 생성 (중복 방지)
        String inviteCode = generateUniqueInviteCode();

        Group group = Group.builder()
                .name(request.getName())
                .description(request.getDescription())
                .hostUserId(hostUserId)
                .inviteCode(inviteCode)
                .build();

        Group saved = groupRepository.save(group);

        // 생성자는 자동으로 멤버에도 추가
        GroupMember hostMember = GroupMember.builder()
                .group(saved)
                .userId(hostUserId)
                .build();
        groupMemberRepository.save(hostMember);

        return GroupResponse.builder()
                .groupId(saved.getId())
                .groupName(saved.getName())
                .inviteCode(saved.getInviteCode())
                .build();
    }

    /**
     * 초대 코드로 모임 참여
     */
    @Transactional
    public JoinResponse joinGroup(GroupJoinRequest request, Long userId) {
        Group group = groupRepository.findByInviteCode(request.getInviteCode())
                .orElseThrow(() -> new RuntimeException("초대코드가 올바르지 않습니다."));

        // 이미 가입한 경우
        boolean alreadyJoined =
                groupMemberRepository.existsByGroup_IdAndUserId(group.getId(), userId);

        if (alreadyJoined) {
            return JoinResponse.builder()
                    .groupId(group.getId())
                    .groupName(group.getName())
                    .build();
        }

        GroupMember member = GroupMember.builder()
                .group(group)
                .userId(userId)
                .build();
        groupMemberRepository.save(member);

        return JoinResponse.builder()
                .groupId(group.getId())
                .groupName(group.getName())
                .build();
    }

    /**
     * 내가 가입한 모임 목록 조회
     */
    public List<MyGroupResponse> getMyGroups(Long userId) {
        List<GroupMember> memberships = groupMemberRepository.findByUserId(userId);

        return memberships.stream()
                .map(m -> {
                    Group g = m.getGroup();
                    return MyGroupResponse.builder()
                            .groupId(g.getId())
                            .groupName(g.getName())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 초대코드 생성 + 중복 체크
     */
    private String generateUniqueInviteCode() {
        while (true) {
            String code = generateInviteCode();
            boolean exists = groupRepository.existsByInviteCode(code);
            if (!exists) {
                return code;
            }
        }
    }

    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            int idx = random.nextInt(INVITE_CODE_CHARS.length());
            sb.append(INVITE_CODE_CHARS.charAt(idx));
        }
        return sb.toString();
    }
}