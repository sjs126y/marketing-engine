package com.sjs.marketing.domain.award.service;

import com.sjs.marketing.domain.award.model.entity.UserAwardRecordEntity;
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

}
