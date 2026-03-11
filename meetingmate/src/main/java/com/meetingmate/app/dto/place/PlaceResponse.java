package com.meetingmate.app.dto.place;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlaceResponse {
    private Long id;
    private String name;
    private String category;
    private Double lat;
    private Double lng;
    private long voteCount;
}