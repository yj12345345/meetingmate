package com.meetingmate.app.domain.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {

    void deleteByAvailability(Availability availability);

    List<AvailabilitySlot> findAllByAvailabilityIn(Collection<Availability> availabilities);

    // (선택) 기간 필터가 필요하면 서비스에서 date로 걸러도 되고,
    // JPA 메서드로 하고 싶으면 아래처럼 추가할 수 있습니다.
    List<AvailabilitySlot> findAllByAvailabilityInAndDateBetween(
            Collection<Availability> availabilities,
            LocalDate from,
            LocalDate to
    );
}