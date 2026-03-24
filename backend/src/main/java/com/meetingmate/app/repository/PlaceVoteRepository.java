package com.meetingmate.app.repository;

import com.meetingmate.app.domain.place.PlaceVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface PlaceVoteRepository extends JpaRepository<PlaceVote, Long> {

    long countByPlaceId(Long placeId);
    boolean existsByPlaceIdAndUserId(Long placeId, Long userId);
    void deleteByUserId(Long userId);
    void deleteByPlaceIdIn(Collection<Long> placeIds);
}
