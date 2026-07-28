package com.example.ilgeobolkka.auth.facade;

import com.example.ilgeobolkka.auth.dto.SignupAuthRequest;
import com.example.ilgeobolkka.auth.dto.SignupAuthResponse;
import com.example.ilgeobolkka.ink.service.InkService;
import com.example.ilgeobolkka.reader.entity.Reader;
import com.example.ilgeobolkka.reader.service.ReaderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthFacade {

    private final ReaderService readerService;
    private final InkService inkService;

    @Transactional
    public SignupAuthResponse signup(SignupAuthRequest request) {
        Reader reader = readerService.createReader(request.email(), request.password());
        inkService.createInkAccount(reader.getId());
        return SignupAuthResponse.from(reader);
    }
}
