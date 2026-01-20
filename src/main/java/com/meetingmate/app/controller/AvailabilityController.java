package com.meetingmate.app.controller;

import com.meetingmate.app.dto.availability.AvailabilityUpsertRequest;
import com.meetingmate.app.dto.availability.GroupAvailabilityResponse;
import com.meetingmate.app.security.UserPrincipal;
import com.meetingmate.app.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @PostMapping("/{groupId}/availability")
    public ResponseEntity<Void> upsertMyAvailability(
            @PathVariable Long groupId,
            @RequestBody AvailabilityUpsertRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        availabilityService.upsertMyAvailability(groupId, principal.getId(), request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{groupId}/availability")
    public ResponseEntity<GroupAvailabilityResponse> getGroupAvailability(
            @PathVariable Long groupId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                availabilityService.getGroupAvailability(groupId, principal.getId(), from, to)
        );
    }
}