package com.meetingmate.app.domain.group;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "meeting_group")  // 테이블 이름 변경
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 모임 이름
    private String name;

    // 모임 설명
    private String description;

    // 초대 코드 (A1B2C3 같은 6자리)
    @Column(unique = true)
    private String inviteCode;

    // 모임 생성자(User의 id)
    private Long hostUserId;

    // 생성 시간 자동 입력
    @CreationTimestamp
    private LocalDateTime createdAt;
}