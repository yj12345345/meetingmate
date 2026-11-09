package com.meetingmate.app.repository;

import com.meetingmate.app.domain.place.PlaceVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PlaceVoteRepository extends JpaRepository<PlaceVote, Long> {

    long countByPlaceId(Long placeId);

    /** Vote counts for many places in one query (avoids one COUNT per place). */
    @Query("select v.placeId as placeId, count(v) as voteCount from PlaceVote v "
            + "where v.placeId in :placeIds group by v.placeId")
    List<PlaceVoteCount> countGroupedByPlaceIds(@Param("placeIds") Collection<Long> placeIds);

    interface PlaceVoteCount {
        Long getPlaceId();
        Long getVoteCount();
    }

    boolean existsByPlaceIdAndUserId(Long placeId, Long userId);
    void deleteByUserId(Long userId);
    void deleteByPlaceIdIn(Collection<Long> placeIds);
}
