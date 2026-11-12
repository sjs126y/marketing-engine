package com.sjs.marketing.infrastructure.dao;
import com.sjs.marketing.infrastructure.dao.po.UserAwardRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author marketing-engine contributors
 * @description 用户中奖记录表
 * @create 2024-04-03 15:57
 */
@Mapper
public interface IUserAwardRecordDao {
    void insert(UserAwardRecord userAwardRecord);
}
