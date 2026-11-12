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

    /**
     * 查最近的中奖记录。
     *
     * <p>user_award_record 是分片表，这里不带分片键，ShardingSphere 会广播到所有分片
     * 再按 award_time 归并排序后取前 N 条——运营看板用，数据量大了应该换成汇总表。</p>
     */
    java.util.List<UserAwardRecord> queryRecent(@org.apache.ibatis.annotations.Param("limit") int limit);

    /**
     * 更新发奖状态（create → complete / fail）。
     *
     * <p>user_award_record 按 user_id 分片，所以带 userId 才能精确路由；
     * 条件里带上原状态，重复回调是幂等的。</p>
     */
    int updateAwardState(@org.apache.ibatis.annotations.Param("userId") String userId,
                         @org.apache.ibatis.annotations.Param("orderId") String orderId,
                         @org.apache.ibatis.annotations.Param("state") String state);
}
