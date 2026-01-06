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
@Table(name = "group_members")
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 그룹에 속했는지 (JPA 연관관계)
    @ManyToOne
    @JoinColumn(name = "group_id")
    private Group group;

    // 어떤 유저인지
    private Long userId;

    @CreationTimestamp
    private LocalDateTime joinedAt;
}