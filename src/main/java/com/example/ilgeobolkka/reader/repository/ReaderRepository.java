package com.example.ilgeobolkka.reader.repository;

import com.example.ilgeobolkka.reader.entity.Reader;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReaderRepository extends JpaRepository<Reader, Long> {

    boolean existsByEmail(String email);

    Optional<Reader> findByEmail(String email);
}
