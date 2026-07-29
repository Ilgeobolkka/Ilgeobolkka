package com.example.ilgeobolkka.reader.service;

import com.example.ilgeobolkka.auth.exception.InvalidCredentialsException;
import com.example.ilgeobolkka.reader.entity.Reader;
import com.example.ilgeobolkka.reader.exception.EmailAlreadyExistsException;
import com.example.ilgeobolkka.reader.repository.ReaderRepository;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ReaderService {

    private static final String USER_NOT_FOUND_PASSWORD = "userNotFoundPassword";

    private final ReaderRepository readerRepository;
    private final PasswordEncoder passwordEncoder;
    private final String userNotFoundEncodedPassword;

    public ReaderService(
            ReaderRepository readerRepository,
            PasswordEncoder passwordEncoder) {
        this.readerRepository = readerRepository;
        this.passwordEncoder = passwordEncoder;
        this.userNotFoundEncodedPassword = passwordEncoder.encode(USER_NOT_FOUND_PASSWORD);
    }

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

    public Reader authenticate(String email, String rawPassword) {
        Reader reader = readerRepository.findByEmail(email).orElse(null);
        String passwordHash = reader == null
                ? userNotFoundEncodedPassword
                : reader.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(rawPassword, passwordHash);
        if (reader == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return reader;
    }
}
