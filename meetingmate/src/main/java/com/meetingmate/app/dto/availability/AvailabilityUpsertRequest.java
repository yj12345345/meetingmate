package com.meetingmate.app.dto.availability;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
public class AvailabilityUpsertRequest {

    @Valid
    private List<SlotRequest> slots;

    @Getter
    public static class SlotRequest {

        @NotNull(message = "날짜는 필수입니다.")
        private LocalDate date;

        @NotNull(message = "시작 시간은 필수입니다.")
        private LocalTime startTime;

        @NotNull(message = "종료 시간은 필수입니다.")
        private LocalTime endTime;
    }
}
