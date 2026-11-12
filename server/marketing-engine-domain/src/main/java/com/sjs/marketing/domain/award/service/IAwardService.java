package com.sjs.marketing.domain.award.service;

import com.sjs.marketing.domain.award.model.entity.UserAwardRecordEntity;
import com.sjs.marketing.domain.award.model.valobj.AwardDefineVO;
import com.sjs.marketing.domain.award.model.valobj.UserAwardRecordVO;

import java.util.List;

public interface IAwardService {

    void saveUserAwardRecord(UserAwardRecordEntity userAwardRecordEntity);

    /**
     * 最近的中奖记录。
     *
     * @param limit 条数上限
     */
    List<UserAwardRecordVO> queryRecentAwards(int limit);

    AwardDefineVO queryAwardDefine(Integer awardId);

    /**
     * 回调成功后把发奖状态推进到 complete。
     */
    void markDelivered(String userId, String orderId);

}
