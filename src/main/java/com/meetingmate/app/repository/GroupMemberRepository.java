package com.meetingmate.app.repository;

import com.meetingmate.app.domain.group.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    boolean existsByGroup_IdAndUserId(Long groupId, Long userId);

    List<GroupMember> findByUserId(Long userId);
    long countByGroup_Id(Long groupId);
}