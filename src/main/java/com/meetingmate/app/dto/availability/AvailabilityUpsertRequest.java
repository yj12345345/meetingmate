package com.meetingmate.app.dto.availability;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
public class AvailabilityUpsertRequest {
    private List<SlotRequest> slots;

    @Getter
    public static class SlotRequest {
        private LocalDate date;
        private LocalTime startTime;
        private LocalTime endTime;
    }
}