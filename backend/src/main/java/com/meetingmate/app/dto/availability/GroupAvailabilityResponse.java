package com.meetingmate.app.dto.availability;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
public class GroupAvailabilityResponse {

    private Long groupId;
    private List<UserAvailabilityItem> items;

    @Getter
    @Builder
    public static class UserAvailabilityItem {
        private Long userId;
        private List<SlotResponse> slots;
    }

    @Getter
    @Builder
    public static class SlotResponse {
        private LocalDate date;
        private LocalTime startTime;
        private LocalTime endTime;
    }
}