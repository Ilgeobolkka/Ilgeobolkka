package com.example.testfixture.querydsl;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "querydsl_test_entity")
class QuerydslTestEntity {

    @Id
    Long id;

    String title;

    @Transient
    String ignoredValue;
}
