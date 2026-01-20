package com.meetingmate.app.service;

import com.meetingmate.app.domain.availability.*;
import com.meetingmate.app.domain.group.Group;
import com.meetingmate.app.dto.availability.AvailabilityUpsertRequest;
import com.meetingmate.app.dto.availability.GroupAvailabilityResponse;
import com.meetingmate.app.dto.availability.GroupAvailabilityResponse.SlotResponse;
import com.meetingmate.app.dto.availability.GroupAvailabilityResponse.UserAvailabilityItem;
import com.meetingmate.app.exception.GroupAccessDeniedException;
import com.meetingmate.app.exception.GroupNotFoundException;
import com.meetingmate.app.repository.GroupMemberRepository;
import com.meetingmate.app.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;

    private final AvailabilityRepository availabilityRepository;
    private final AvailabilitySlotRepository slotRepository;

    @Transactional
    public void upsertMyAvailability(Long groupId, Long userId, AvailabilityUpsertRequest request) {
        Group group = groupRepository.findById(groupId).orElseThrow(() -> new GroupNotFoundException(groupId));
        validateMember(group, userId);

        List<AvailabilityUpsertRequest.SlotRequest> slots = request.getSlots();
        if (slots == null) slots = List.of();

        // 30분 단위 + start < end + null 체크
        for (AvailabilityUpsertRequest.SlotRequest s : slots) {
            validateSlot(s);
        }

        Availability availability = availabilityRepository.findByGroupAndUserId(group, userId)
                .orElseGet(() -> availabilityRepository.save(
                        Availability.builder().group(group).userId(userId).build()
                ));

        // 교체형 저장: 기존 슬롯 삭제 → 새 슬롯 insert
        slotRepository.deleteByAvailability(availability);

        List<AvailabilitySlot> entities = slots.stream()
                .map(s -> AvailabilitySlot.builder()
                        .availability(availability)
                        .date(s.getDate())
                        .startTime(s.getStartTime())
                        .endTime(s.getEndTime())
                        .build())
                .toList();

        slotRepository.saveAll(entities);
    }

    @Transactional(readOnly = true)
    public GroupAvailabilityResponse getGroupAvailability(Long groupId, Long userId, LocalDate from, LocalDate to) {
        Group group = groupRepository.findById(groupId).orElseThrow(() -> new GroupNotFoundException(groupId));
        validateMember(group, userId);

        // 그룹 멤버들의 availability를 모두 가져오려면:
        // 1) groupMember에서 userId 목록 조회 → 2) availability를 생성/조회하는 방식이 필요합니다.
        // 다만 MVP에서는 "availability가 있는 사용자만 반환"해도 충분합니다.
        //
        // 지금은 availability 테이블에서 group 기준으로 전부 조회하는 메서드를 하나 추가하는 게 가장 깔끔합니다.
        // 아래처럼 AvailabilityRepository에 findAllByGroup(Group group) 추가 추천.
        //
        // 여기서는 해당 메서드가 있다고 가정합니다.
        List<Availability> availabilities = availabilityRepository.findAllByGroup(group);

        List<AvailabilitySlot> slots;
        if (from != null && to != null) {
            slots = slotRepository.findAllByAvailabilityInAndDateBetween(availabilities, from, to);
        } else {
            slots = slotRepository.findAllByAvailabilityIn(availabilities);
        }

        // availabilityId 기준으로 userId 매핑
        Map<Long, Long> availabilityIdToUserId = availabilities.stream()
                .collect(Collectors.toMap(Availability::getId, Availability::getUserId));

        // userId -> slots
        Map<Long, List<AvailabilitySlot>> grouped = slots.stream()
                .collect(Collectors.groupingBy(s -> availabilityIdToUserId.get(s.getAvailability().getId())));

        List<UserAvailabilityItem> items = grouped.entrySet().stream()
                .map(e -> UserAvailabilityItem.builder()
                        .userId(e.getKey())
                        .slots(e.getValue().stream()
                                .sorted(Comparator.comparing(AvailabilitySlot::getDate)
                                        .thenComparing(AvailabilitySlot::getStartTime))
                                .map(x -> SlotResponse.builder()
                                        .date(x.getDate())
                                        .startTime(x.getStartTime())
                                        .endTime(x.getEndTime())
                                        .build())
                                .toList())
                        .build())
                .sorted(Comparator.comparing(UserAvailabilityItem::getUserId))
                .toList();

        return GroupAvailabilityResponse.builder()
                .groupId(groupId)
                .items(items)
                .build();
    }

    private void validateMember(Group group, Long userId) {
        boolean isMember = groupMemberRepository.existsByGroup_IdAndUserId(group.getId(), userId);
        if (!isMember) throw new GroupAccessDeniedException(group.getId());
    }

    private void validateSlot(AvailabilityUpsertRequest.SlotRequest s) {
        if (s.getDate() == null || s.getStartTime() == null || s.getEndTime() == null) {
            throw new IllegalArgumentException("INVALID_SLOT");
        }
        LocalTime start = s.getStartTime();
        LocalTime end = s.getEndTime();
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("INVALID_TIME_RANGE");
        }
        // 30분 단위 체크: 분이 0 또는 30이어야 함
        if (!isHalfHourUnit(start) || !isHalfHourUnit(end)) {
            throw new IllegalArgumentException("TIME_NOT_HALF_HOUR_UNIT");
        }
    }

    private boolean isHalfHourUnit(LocalTime t) {
        int m = t.getMinute();
        return (m == 0 || m == 30) && t.getSecond() == 0 && t.getNano() == 0;
    }
}