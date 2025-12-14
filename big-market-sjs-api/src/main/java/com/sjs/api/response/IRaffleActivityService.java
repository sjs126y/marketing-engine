package com.sjs.api.response;

import com.sjs.api.dto.ActivityDrawRequestDTO;
import com.sjs.api.dto.ActivityDrawResponseDTO;

public interface IRaffleActivityService {

    /**
     * 活动预热
     * @param activityId
     * @return
     */
    Response<Boolean> armory(Long activityId);

    /**
     * 抽奖
     * @param requestDTO
     * @return
     */
    Response<ActivityDrawResponseDTO> draw(ActivityDrawRequestDTO requestDTO);

}
