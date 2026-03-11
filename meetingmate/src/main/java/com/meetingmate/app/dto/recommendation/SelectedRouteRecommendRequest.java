package com.meetingmate.app.dto.recommendation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SelectedRouteRecommendRequest {
    private String keyword;
    private String meetingType;
    private String mood;
    private String location;
    private String planHint;
    private List<SelectedPlace> selectedPlaces;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SelectedPlace {
        private String category;
        private String name;
        private String address;
        private String reason;
        private List<String> tags;
        private String mapQuery;
    }
}
