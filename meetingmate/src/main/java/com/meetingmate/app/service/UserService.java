package com.meetingmate.app.service;

import com.meetingmate.app.domain.availability.Availability;
import com.meetingmate.app.domain.availability.AvailabilityRepository;
import com.meetingmate.app.domain.availability.AvailabilitySlotRepository;
import com.meetingmate.app.domain.group.Group;
import com.meetingmate.app.domain.place.PlaceCandidate;
import com.meetingmate.app.domain.user.User;
import com.meetingmate.app.domain.user.dto.UserMeResponse;
import com.meetingmate.app.repository.GroupMemberRepository;
import com.meetingmate.app.repository.GroupRepository;
import com.meetingmate.app.repository.PlaceCandidateRepository;
import com.meetingmate.app.repository.PlaceVoteRepository;
import com.meetingmate.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final AvailabilityRepository availabilityRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final PlaceCandidateRepository placeCandidateRepository;
    private final PlaceVoteRepository placeVoteRepository;

    @Transactional(readOnly = true)
    public UserMeResponse getMyInfo(Long userId) {
        User user = getUserOrThrow(userId);
        return new UserMeResponse(user);
    }

    @Transactional
    public UserMeResponse updateNickname(Long userId, String nickname) {
        User user = getUserOrThrow(userId);
        user.setNickname(nickname);
        return new UserMeResponse(user);
    }

    @Transactional
    public void deleteMyAccount(Long userId) {
        User user = getUserOrThrow(userId);

        // 1) 사용자가 owner인 그룹 및 하위 데이터 삭제
        List<Group> ownedGroups = groupRepository.findAllByOwnerId(userId);
        for (Group group : ownedGroups) {
            deleteGroupRelatedData(group);
            groupRepository.delete(group);
        }

        // 2) 사용자가 멤버/참여자로 남긴 데이터 삭제
        groupMemberRepository.deleteByUserId(userId);
        deleteAvailabilityData(availabilityRepository.findAllByUserId(userId));
        placeVoteRepository.deleteByUserId(userId);

        // 3) 사용자가 생성한 장소 후보 및 해당 투표 삭제
        deletePlacesAndVotes(placeCandidateRepository.findByCreatedBy(userId));
        placeCandidateRepository.deleteByCreatedBy(userId);

        // 4) 사용자 삭제
        userRepository.delete(user);
    }

    private void deleteGroupRelatedData(Group group) {
        Long groupId = group.getId();

        groupMemberRepository.deleteByGroup_Id(groupId);
        deleteAvailabilityData(availabilityRepository.findAllByGroup(group));
        deletePlacesAndVotes(placeCandidateRepository.findByGroupId(groupId));
        placeCandidateRepository.deleteByGroupId(groupId);
    }

    private void deleteAvailabilityData(List<Availability> availabilities) {
        if (availabilities.isEmpty()) {
            return;
        }
        availabilitySlotRepository.deleteAllByAvailabilityIn(availabilities);
        availabilityRepository.deleteAll(availabilities);
    }

    private void deletePlacesAndVotes(List<PlaceCandidate> places) {
        if (places.isEmpty()) {
            return;
        }
        List<Long> placeIds = places.stream()
                .map(PlaceCandidate::getId)
                .toList();
        if (!placeIds.isEmpty()) {
            placeVoteRepository.deleteByPlaceIdIn(placeIds);
        }
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }
}
