package com.sjs.domain.activity.service;

import com.sjs.domain.activity.model.entity.PartakeRaffleActivityEntity;
import com.sjs.domain.activity.model.entity.UserRaffleOrderEntity;

public interface IRaffleActivityPartakeService {

    UserRaffleOrderEntity createOrder(String userId, Long activityId);

    UserRaffleOrderEntity createOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity);
}
