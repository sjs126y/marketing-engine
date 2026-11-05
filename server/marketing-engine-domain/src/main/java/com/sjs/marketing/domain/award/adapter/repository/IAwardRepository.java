package com.sjs.marketing.domain.award.adapter.repository;

import com.sjs.marketing.domain.award.model.aggregate.UserAwardRecordAggregate;

public interface IAwardRepository {
    void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate);
}
