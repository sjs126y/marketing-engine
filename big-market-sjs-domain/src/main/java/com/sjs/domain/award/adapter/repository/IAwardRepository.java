package com.sjs.domain.award.adapter.repository;

import com.sjs.domain.award.model.aggregate.UserAwardRecordAggregate;

public interface IAwardRepository {
    void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate);
}
