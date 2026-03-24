package com.meetingmate.app.domain.place;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long groupId;

    private String name;        // 장소 이름
    private String category;    // FOOD / CAFE / ACTIVITY

    private Double lat;
    private Double lng;

    private Long createdBy;     // 제안한 사용자 ID
}