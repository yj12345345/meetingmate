package com.meetingmate.app.repository;

import com.meetingmate.app.domain.place.PlaceCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceCandidateRepository extends JpaRepository<PlaceCandidate, Long> {

    List<PlaceCandidate> findByGroupId(Long groupId);
    List<PlaceCandidate> findByCreatedBy(Long createdBy);
    List<PlaceCandidate> findTop5ByNameContainingIgnoreCaseOrderByIdDesc(String keyword);
    void deleteByGroupId(Long groupId);
    void deleteByCreatedBy(Long createdBy);
}
