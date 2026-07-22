package com.example.ilgeobolkka.support.querydsl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class QuerydslQTypeGenerationTest {

    @Test
    void 엔티티의_지속_필드로_Q타입을_생성한다() {
        QQuerydslTestEntity querydslTestEntity = QQuerydslTestEntity.querydslTestEntity;

        assertAll(
                () -> assertEquals(Long.class, querydslTestEntity.id.getType()),
                () -> assertEquals(String.class, querydslTestEntity.title.getType()));
    }

    @Test
    void 영속하지_않는_필드는_Q타입에서_제외한다() {
        assertThrows(
                NoSuchFieldException.class,
                () -> QQuerydslTestEntity.class.getDeclaredField("ignoredValue"));
    }
}
