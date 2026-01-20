package com.meetingmate.app.domain.availability;

import com.meetingmate.app.domain.group.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AvailabilityRepository extends JpaRepository<Availability, Long> {
    Optional<Availability> findByGroupAndUserId(Group group, Long userId);
    List<Availability> findAllByGroup(Group group);
}