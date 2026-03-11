package com.meetingmate.app.service;

import com.meetingmate.app.domain.place.PlaceCandidate;
import com.meetingmate.app.domain.place.PlaceVote;
import com.meetingmate.app.dto.place.PlaceCreateRequest;
import com.meetingmate.app.dto.place.PlaceResponse;
import com.meetingmate.app.exception.ErrorCode;
import com.meetingmate.app.exception.GroupAccessDeniedException;
import com.meetingmate.app.exception.PlaceVoteException;
import com.meetingmate.app.repository.GroupMemberRepository;
import com.meetingmate.app.repository.PlaceCandidateRepository;
import com.meetingmate.app.repository.PlaceVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceCandidateRepository placeRepository;
    private final PlaceVoteRepository voteRepository;
    private final GroupMemberRepository groupMemberRepository;

    public void createPlace(Long groupId, Long userId, PlaceCreateRequest request) {
        placeRepository.save(
                PlaceCandidate.builder()
                        .groupId(groupId)
                        .name(request.getName())
                        .category(request.getCategory())
                        .lat(request.getLat())
                        .lng(request.getLng())
                        .createdBy(userId)
                        .build()
        );
    }

    public void vote(Long groupId, Long placeId, Long userId) {
        validateGroupMember(groupId, userId);

        PlaceCandidate place = placeRepository.findById(placeId)
                .orElseThrow(() -> new PlaceVoteException(ErrorCode.PLACE_NOT_FOUND));

        if (!groupId.equals(place.getGroupId())) {
            throw new PlaceVoteException(ErrorCode.PLACE_NOT_IN_GROUP);
        }

        if (voteRepository.existsByPlaceIdAndUserId(placeId, userId)) {
            throw new PlaceVoteException(ErrorCode.PLACE_ALREADY_VOTED);
        }

        voteRepository.save(
                PlaceVote.builder()
                        .placeId(placeId)
                        .userId(userId)
                        .build()
        );
    }

    public List<PlaceResponse> getPlaces(Long groupId) {
        return placeRepository.findByGroupId(groupId).stream()
                .map(place -> PlaceResponse.builder()
                        .id(place.getId())
                        .name(place.getName())
                        .category(place.getCategory())
                        .lat(place.getLat())
                        .lng(place.getLng())
                        .voteCount(voteRepository.countByPlaceId(place.getId()))
                        .build()
                ).toList();
    }

    private void validateGroupMember(Long groupId, Long userId) {
        boolean isMember = groupMemberRepository.existsByGroup_IdAndUserId(groupId, userId);
        if (!isMember) {
            throw new GroupAccessDeniedException(groupId);
        }
    }
}
