package com.sjs.marketing.domain.activity.service;

import com.sjs.marketing.domain.activity.model.entity.PartakeRaffleActivityEntity;
import com.sjs.marketing.domain.activity.model.entity.UserRaffleOrderEntity;

public interface IRaffleActivityPartakeService {

    UserRaffleOrderEntity createOrder(String userId, Long activityId);

    UserRaffleOrderEntity createOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity);
}
