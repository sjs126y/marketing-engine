package com.sjs.marketing.infrastructure.dao;

import com.sjs.marketing.infrastructure.dao.po.RuleTreeNodeLine;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author marketing-engine contributors
 * @description 规则树节点连线表DAO
 * @create 2024-02-03 08:44
 */
@Mapper
public interface IRuleTreeNodeLineDao {

    List<RuleTreeNodeLine> queryRuleTreeNodeLineListByTreeId(String treeId);

}
