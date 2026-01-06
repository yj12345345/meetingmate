package com.meetingmate.app.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_entity")
public class TestEntity {

    @Id
    @GeneratedValue
    private Long id;
}