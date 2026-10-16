package com.sjs.marketing.api;

import com.sjs.marketing.api.dto.ActivityDrawRequestDTO;
import com.sjs.marketing.api.dto.ActivityDrawResponseDTO;
import com.sjs.marketing.api.dto.UserActivityAccountRequestDTO;
import com.sjs.marketing.api.dto.UserActivityAccountResponseDTO;
import com.sjs.marketing.api.response.Response;

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

    /**
     * 签到返利
     * @param userId
     * @return
     */
    Response<Boolean> calendarSignRebate(String userId);

    Response<Boolean> isCalendarSignRebate(String userId);

    Response<UserActivityAccountResponseDTO> queryUserActivityAccount(UserActivityAccountRequestDTO request);

}
