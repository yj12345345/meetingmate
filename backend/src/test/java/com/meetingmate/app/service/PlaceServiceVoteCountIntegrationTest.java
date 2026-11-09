package com.meetingmate.app.service;

import com.meetingmate.app.domain.place.PlaceCandidate;
import com.meetingmate.app.domain.place.PlaceVote;
import com.meetingmate.app.dto.place.PlaceResponse;
import com.meetingmate.app.repository.PlaceCandidateRepository;
import com.meetingmate.app.repository.PlaceVoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real JPQL GROUP BY query against H2 to make sure the N+1 fix returns
 * the same vote counts as the old per-place COUNT, including places with 0 votes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlaceServiceVoteCountIntegrationTest {

    @Autowired PlaceService placeService;
    @Autowired PlaceCandidateRepository placeRepository;
    @Autowired PlaceVoteRepository voteRepository;

    @Test
    void getPlacesReturnsVoteCountsPerPlaceInOneQuery() {
        long groupId = 4242L;
        PlaceCandidate a = placeRepository.save(place(groupId, "A"));
        PlaceCandidate b = placeRepository.save(place(groupId, "B"));
        PlaceCandidate noVotes = placeRepository.save(place(groupId, "C"));
        PlaceCandidate otherGroup = placeRepository.save(place(9999L, "X"));

        vote(a, 1L); vote(a, 2L); vote(a, 3L);
        vote(b, 1L);
        vote(otherGroup, 1L);

        Map<String, Long> counts = placeService.getPlaces(groupId).stream()
                .collect(Collectors.toMap(PlaceResponse::getName, PlaceResponse::getVoteCount));

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of("A", 3L, "B", 1L, "C", 0L));
        assertThat(placeService.getPlaces(123456L)).isEmpty();
        assertThat(noVotes.getId()).isNotNull();
    }

    private PlaceCandidate place(long groupId, String name) {
        return PlaceCandidate.builder().groupId(groupId).name(name).category("CAFE")
                .lat(37.5).lng(127.0).createdBy(1L).build();
    }

    private void vote(PlaceCandidate place, long userId) {
        voteRepository.save(PlaceVote.builder().placeId(place.getId()).userId(userId).build());
    }
}
