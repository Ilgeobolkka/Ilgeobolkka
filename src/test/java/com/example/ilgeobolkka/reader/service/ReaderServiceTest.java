package com.example.ilgeobolkka.reader.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ilgeobolkka.auth.exception.InvalidCredentialsException;
import com.example.ilgeobolkka.reader.entity.Reader;
import com.example.ilgeobolkka.reader.exception.EmailAlreadyExistsException;
import com.example.ilgeobolkka.reader.repository.ReaderRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ReaderServiceTest {

    @Mock
    private ReaderRepository readerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void 존재하지_않는_이메일도_더미_해시와_비밀번호를_대조한다() {
        String rawPassword = "Valid-password1!";
        String dummyPasswordHash = "{bcrypt}dummy";
        when(passwordEncoder.encode(any())).thenReturn(dummyPasswordHash);
        ReaderService readerService =
                new ReaderService(readerRepository, passwordEncoder);
        when(readerRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> readerService.authenticate("missing@example.com", rawPassword));

        verify(passwordEncoder).matches(rawPassword, dummyPasswordHash);
    }

    @Test
    void 동시_가입의_이메일_고유_제약_위반은_중복_이메일_오류로_변환한다() {
        ReaderService readerService =
                new ReaderService(readerRepository, passwordEncoder);
        when(readerRepository.existsByEmail("reader@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Valid-password1!")).thenReturn("{bcrypt}encoded");
        when(readerRepository.saveAndFlush(any(Reader.class)))
                .thenThrow(new DataIntegrityViolationException("uk_reader_email"));

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> readerService.createReader(
                        "reader@example.com",
                        "Valid-password1!"));
    }
}
