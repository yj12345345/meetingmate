package com.meetingmate.app.service;

import com.meetingmate.app.domain.place.PlaceCandidate;
import com.meetingmate.app.exception.ErrorCode;
import com.meetingmate.app.exception.PlaceVoteException;
import com.meetingmate.app.repository.GroupMemberRepository;
import com.meetingmate.app.repository.PlaceCandidateRepository;
import com.meetingmate.app.repository.PlaceVoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    @Mock
    private PlaceCandidateRepository placeRepository;

    @Mock
    private PlaceVoteRepository voteRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @InjectMocks
    private PlaceService placeService;

    @Test
    void 다른_그룹의_장소에는_투표할_수_없다() {
        PlaceCandidate place = PlaceCandidate.builder()
                .id(3L)
                .groupId(99L)
                .name("카페")
                .category("CAFE")
                .lat(37.0)
                .lng(127.0)
                .createdBy(1L)
                .build();

        when(groupMemberRepository.existsByGroup_IdAndUserId(10L, 7L)).thenReturn(true);
        when(placeRepository.findById(3L)).thenReturn(Optional.of(place));

        assertThatThrownBy(() -> placeService.vote(10L, 3L, 7L))
                .isInstanceOf(PlaceVoteException.class)
                .satisfies(ex -> assertThat(((PlaceVoteException) ex).getErrorCode()).isEqualTo(ErrorCode.PLACE_NOT_IN_GROUP));

        verify(voteRepository, never()).save(any());
    }

    @Test
    void 이미_투표한_장소에는_중복_투표할_수_없다() {
        PlaceCandidate place = PlaceCandidate.builder()
                .id(3L)
                .groupId(10L)
                .name("카페")
                .category("CAFE")
                .lat(37.0)
                .lng(127.0)
                .createdBy(1L)
                .build();

        when(groupMemberRepository.existsByGroup_IdAndUserId(10L, 7L)).thenReturn(true);
        when(placeRepository.findById(3L)).thenReturn(Optional.of(place));
        when(voteRepository.existsByPlaceIdAndUserId(3L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> placeService.vote(10L, 3L, 7L))
                .isInstanceOf(PlaceVoteException.class)
                .satisfies(ex -> assertThat(((PlaceVoteException) ex).getErrorCode()).isEqualTo(ErrorCode.PLACE_ALREADY_VOTED));

        verify(voteRepository, never()).save(any());
    }
}
