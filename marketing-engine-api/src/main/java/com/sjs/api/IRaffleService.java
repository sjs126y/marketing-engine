package com.sjs.api;

import com.sjs.api.dto.RaffleAwardListRequestDTO;
import com.sjs.api.dto.RaffleAwardListResponseDTO;
import com.sjs.api.dto.RaffleRequestDTO;
import com.sjs.api.dto.RaffleResponseDTO;
import com.sjs.api.response.Response;

import java.util.List;

public interface IRaffleService {

    /**
     * 策略预热
     * @param strategyId
     * @return
     */
    Response<Boolean> strategyArmory(Long strategyId);

    /**
     * 查询策略奖品列表
     * @param requestDTO
     * @return
     */
    Response<List<RaffleAwardListResponseDTO>> queryRaffleAwardList(RaffleAwardListRequestDTO requestDTO);

    /**
     * 抽奖
     * @param requestDTO
     * @return
     */
    Response<RaffleResponseDTO> randomRaffle(RaffleRequestDTO requestDTO);

}
