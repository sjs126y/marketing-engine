package com.sjs.domain.activity.service;

import com.sjs.domain.activity.model.entity.PartakeRaffleActivityEntity;
import com.sjs.domain.activity.model.entity.UserRaffleOrderEntity;

public interface IRaffleActivityPartakeService {

    UserRaffleOrderEntity createOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity);

}
