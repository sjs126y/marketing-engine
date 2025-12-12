package com.sjs.domain.activity.service;

import com.sjs.domain.activity.adapter.repository.IActivityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RaffleActivityService extends AbstractRaffleActivity{

    public RaffleActivityService(IActivityRepository repository) {
        super(repository);
    }
}
