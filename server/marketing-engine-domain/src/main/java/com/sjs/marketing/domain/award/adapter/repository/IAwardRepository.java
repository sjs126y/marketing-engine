package com.sjs.marketing.domain.award.adapter.repository;

import com.sjs.marketing.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.sjs.marketing.domain.award.model.valobj.AwardDefineVO;
import com.sjs.marketing.domain.award.model.valobj.UserAwardRecordVO;

import java.util.List;

public interface IAwardRepository {

    void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate);

    /**
     * 最近的中奖记录，供运营看板展示。
     *
     * @param limit 条数上限
     */
    List<UserAwardRecordVO> queryRecentAwards(int limit);

    /**
     * 查奖品定义，用于把中奖结果翻译成给上游的交付指令。
     */
    AwardDefineVO queryAwardDefine(Integer awardId);

    /**
     * 更新发奖状态（create → complete / fail）。
     */
    void updateAwardState(String userId, String orderId, String state);
}
