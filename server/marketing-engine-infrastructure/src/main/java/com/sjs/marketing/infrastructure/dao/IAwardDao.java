package com.sjs.marketing.infrastructure.dao;

import com.sjs.marketing.infrastructure.dao.po.Award;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author marketing-engine contributors
 * @description 奖品表DAO
 * @create 2023-12-16 13:23
 */
@Mapper
public interface IAwardDao {

    List<Award> queryAwardList();


    /**
     * 查奖品定义（发奖渠道与参数）。
     */
    com.sjs.marketing.infrastructure.dao.po.Award queryAwardDefine(@org.apache.ibatis.annotations.Param("awardId") Integer awardId);
}
