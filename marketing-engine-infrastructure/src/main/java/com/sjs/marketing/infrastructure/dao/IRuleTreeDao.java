package com.sjs.marketing.infrastructure.dao;

import com.sjs.marketing.infrastructure.dao.po.RuleTree;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author marketing-engine contributors
 * @description 规则树表DAO
 * @create 2024-02-03 08:42
 */
@Mapper
public interface IRuleTreeDao {

    RuleTree queryRuleTreeByTreeId(String treeId);

}
