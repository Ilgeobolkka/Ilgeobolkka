package com.example.ilgeobolkka.ink.service;

import com.example.ilgeobolkka.ink.entity.InkAccount;
import com.example.ilgeobolkka.ink.repository.InkAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InkService {

    private final InkAccountRepository inkAccountRepository;

    public void createInkAccount(long readerId) {
        inkAccountRepository.save(InkAccount.create(readerId));
    }
}
