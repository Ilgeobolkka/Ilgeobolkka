package com.example.ilgeobolkka.support.querydsl;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

@Entity
class QuerydslTestEntity {

    @Id
    Long id;

    String title;

    @Transient
    String ignoredValue;
}
