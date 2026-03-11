package com.meetingmate.app.controller;

import com.meetingmate.app.common.ApiResponse;
import com.meetingmate.app.dto.place.PlaceCreateRequest;
import jakarta.validation.Valid;
import com.meetingmate.app.dto.place.PlaceResponse;
import com.meetingmate.app.security.UserPrincipal;
import com.meetingmate.app.service.PlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups/{groupId}/places")
public class PlaceController {

    private final PlaceService placeService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createPlace(
            @PathVariable Long groupId,
            @Valid @RequestBody PlaceCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        placeService.createPlace(groupId, principal.getUser().getId(), request);
        return ResponseEntity.ok(ApiResponse.success("PLACE_CREATED", "장소 후보가 등록되었습니다.", null));
    }

    @PostMapping("/{placeId}/vote")
    public ResponseEntity<ApiResponse<Void>> vote(
            @PathVariable Long groupId,
            @PathVariable Long placeId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        placeService.vote(groupId, placeId, principal.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("PLACE_VOTED", "장소 투표가 완료되었습니다.", null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlaceResponse>>> list(@PathVariable Long groupId) {
        return ResponseEntity.ok(
                ApiResponse.success("PLACES_FETCHED", "장소 목록을 조회했습니다.", placeService.getPlaces(groupId))
        );
    }
}
