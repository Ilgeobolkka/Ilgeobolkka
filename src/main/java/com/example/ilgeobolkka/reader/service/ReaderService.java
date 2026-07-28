package com.example.ilgeobolkka.reader.service;

import com.example.ilgeobolkka.reader.entity.Reader;
import com.example.ilgeobolkka.reader.exception.EmailAlreadyExistsException;
import com.example.ilgeobolkka.reader.repository.ReaderRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReaderService {

    private final ReaderRepository readerRepository;
    private final PasswordEncoder passwordEncoder;

    public Reader createReader(String email, String rawPassword) {
        if (readerRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        Reader reader =
                Reader.signup(email, passwordEncoder.encode(rawPassword), Instant.now());
        try {
            return readerRepository.saveAndFlush(reader);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException(exception);
        }
    }
}
