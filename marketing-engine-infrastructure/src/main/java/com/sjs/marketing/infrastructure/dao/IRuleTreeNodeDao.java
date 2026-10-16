package com.sjs.marketing.infrastructure.dao;

import com.sjs.marketing.infrastructure.dao.po.RuleTreeNode;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author marketing-engine contributors
 * @description 规则树节点表DAO
 * @create 2024-02-03 08:43
 */
@Mapper
public interface IRuleTreeNodeDao {

    List<RuleTreeNode> queryRuleTreeNodeListByTreeId(String treeId);

    List<RuleTreeNode> queryRuleLocks(String[] treeIds);
}
