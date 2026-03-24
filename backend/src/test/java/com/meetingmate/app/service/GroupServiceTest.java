package com.meetingmate.app.service;

import com.meetingmate.app.domain.group.Group;
import com.meetingmate.app.domain.group.GroupMember;
import com.meetingmate.app.dto.group.GroupCreateRequest;
import com.meetingmate.app.dto.group.GroupJoinRequest;
import com.meetingmate.app.dto.group.GroupResponse;
import com.meetingmate.app.repository.GroupMemberRepository;
import com.meetingmate.app.repository.GroupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @InjectMocks
    private GroupService groupService;

    @Test
    void 모임_생성에_성공한다() {
        GroupCreateRequest request = new GroupCreateRequest();
        ReflectionTestUtils.setField(request, "name", "주간 회의");
        ReflectionTestUtils.setField(request, "description", "백엔드 스프린트 점검");

        when(groupRepository.existsByInviteCode(any())).thenReturn(false);
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
            Group group = invocation.getArgument(0);
            group.setId(1L);
            return group;
        });
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GroupResponse response = groupService.createGroup(request, 99L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("주간 회의");
        assertThat(response.getInviteCode()).hasSize(6);

        ArgumentCaptor<Group> groupCaptor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getOwnerId()).isEqualTo(99L);

        ArgumentCaptor<GroupMember> memberCaptor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(99L);
    }

    @Test
    void 이미_참여한_모임이면_중복_참여에_실패한다() {
        GroupJoinRequest request = new GroupJoinRequest();
        ReflectionTestUtils.setField(request, "inviteCode", "ABC123");

        Group group = Group.builder()
                .id(10L)
                .name("스터디")
                .inviteCode("ABC123")
                .ownerId(1L)
                .build();

        when(groupRepository.findByInviteCode("ABC123")).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroup_IdAndUserId(10L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> groupService.joinGroup(request, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 참여한 모임입니다.");

        verify(groupMemberRepository, times(0)).save(any(GroupMember.class));
    }
}
