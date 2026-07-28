package com.example.ilgeobolkka.reading.repository;

import com.example.ilgeobolkka.reading.entity.ReadingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReadingSessionRepository extends JpaRepository<ReadingSession, Long> {

    @Modifying
    @Query("delete from ReadingSession readingSession where readingSession.readerId = :readerId")
    int deleteByReaderId(@Param("readerId") long readerId);
}
